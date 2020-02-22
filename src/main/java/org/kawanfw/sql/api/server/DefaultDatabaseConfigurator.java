/*
 * This file is part of AceQL HTTP.
 * AceQL HTTP: SQL Over HTTP
 * Copyright (C) 2020,  KawanSoft SAS
 * (http://www.kawansoft.com). All rights reserved.
 *
 * AceQL HTTP is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * AceQL HTTP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301  USA
 *
 * Any modifications to this file must keep this entire header
 * intact.
 */
package org.kawanfw.sql.api.server;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.kawanfw.sql.api.server.util.SimpleHttpClient;
import org.kawanfw.sql.servlet.ServerSqlManager;
import org.kawanfw.sql.tomcat.TomcatSqlModeStore;
import org.kawanfw.sql.tomcat.TomcatStarterUtil;
import org.kawanfw.sql.util.Tag;

/**
 * Default implementation of server side configuration for AceQL.
 * <p>
 * The two fully functional and secured methods are:
 * <ul>
 * <li>{@link #getConnection(String)} that extracts {@code Connections} from a
 * Tomcat JDBC Connection Pool.</li>
 * <li>{@link #close(Connection)} that closes the {@code Connection} and thus
 * releases it into the pool.</li>
 * </ul>
 * <p>
 * <b>WARNING</b>: This default implementation will allow to start immediate
 * remote SQL calls but is <b>*not*</b> at all secured. <br>
 * <b>It is highly recommended to override this class with a secured
 * implementation for the other methods.</b>
 * <p>
 *
 * @author Nicolas de Pomereu
 */
public class DefaultDatabaseConfigurator implements DatabaseConfigurator {

    /** The map of data sources to use for connection pooling */
    private Map<String, DataSource> dataSourceSet = new ConcurrentHashMap<>();

    private Properties properties = null;

    private static Logger ACEQL_LOGGER = null;

    /**
     * Constructor. {@code DatabaseConfigurator} implementation must have no
     * constructor or a unique no parameters constructor.
     */
    public DefaultDatabaseConfigurator() {

    }

    /**
     * @return <code>true</code> if the Authentication Web Service defined in
     *         {@code aceql-server.properties} returns the JSON String <code>{"status"="OK"}</code>, else <code>false</code> .
     */
    @Override
    public boolean login(String username, char[] password, String database, String ipAddress)
	    throws IOException, SQLException {

	if (properties == null) {
	    File file = ServerSqlManager.getAceqlServerProperties();
	    properties = TomcatStarterUtil.getProperties(file);
	}

	String url = properties.getProperty("default.login.webService.url");
	String method = properties.getProperty("default.login.webService.method");
	String timeoutSecondsStr = properties.getProperty("default.login.webService.timeoutSeconds");

	// Accept free login if no Web Service URL defined or is localhost
	if (url == null || url.contentEquals("localhost")) {
	    return true;
	}

	if (method == null) {
	    method = "POST";
	}

	if (timeoutSecondsStr == null) {
	    timeoutSecondsStr = "0";
	}

	if (!StringUtils.isNumeric(timeoutSecondsStr)) {
	    throw new IllegalArgumentException(
		    "The default.login.webService.timeoutSeconds property is not numeric: " + timeoutSecondsStr);
	}

	int timeoutSeconds = Integer.parseInt(timeoutSecondsStr);
	int connectTimeout = timeoutSeconds * 1000;
	int readTimeout = timeoutSeconds * 1000;

	SimpleHttpClient simpleHttpClient = new SimpleHttpClient(connectTimeout, readTimeout);
	String jsonResult = null;
	Map<String, String> parametersMap = new HashMap<>();
	parametersMap.put("username", username);
	parametersMap.put("password", new String(password));

	try {
	    if (method.equalsIgnoreCase("post")) {
		jsonResult = simpleHttpClient.callWithPost(new URL(url), parametersMap);
	    } else {
		jsonResult = simpleHttpClient.callWithGet(url, parametersMap);
	    }
	} catch (Exception e) {
	    Logger logger = getLogger();
	    logger.log(Level.SEVERE, "Username " + username + " can not authenticate. Error when calling SimpleHttpClient: "
		    + e.getMessage());
	    return false;
	}

	if (jsonResult == null) {
	    return false;
	}

	try {
	    JsonReader reader = Json.createReader(new StringReader(jsonResult));
	    JsonStructure jsonst = reader.read();

	    JsonObject object = (JsonObject) jsonst;
	    JsonString status = (JsonString) object.get("status");

	    if (status != null && status.getString().equals("OK")) {
	        return true;
	    } else {
	        return false;
	    }
	} catch (Exception e) {
	    Logger logger = getLogger();
	    logger.log(Level.SEVERE, "Error when parsing jsonResult of AWS: "
		    + e.getMessage());
	    return false;
	}

    }

    /**
     * Returns a {@code Connection} from
     * <a href="http://tomcat.apache.org/tomcat-8.5-doc/jdbc-pool.html" >Tomcat JDBC
     * Connection Pool</a>.<br>
     * <br>
     * the {@code Connection} is extracted from the {@code DataSource} created by
     * the embedded Tomcat JDBC Pool. The JDBC parameters used to create the
     * {@code DataSource} are defined in the properties file passed at start-up of
     * AceQL.
     *
     * @param database the database name to extract the {@code Connection} for.
     *
     * @return the {@code Connection} extracted from Tomcat JDBC Connection Pool.
     */
    @Override
    public Connection getConnection(String database) throws SQLException {

	DataSource dataSource = dataSourceSet.get(database);

	if (dataSource == null) {

	    dataSource = TomcatSqlModeStore.getDataSource(database);

	    if (dataSource == null) {

		if (TomcatSqlModeStore.isTomcatEmbedded()) {

		    String message = Tag.PRODUCT_USER_CONFIG_FAIL
			    + " the \"driverClassName\" property is not defined in the properties file for database "
			    + database + " or the Db Vendor is not supported in this version.";
		    // ServerLogger.getLogger().log(Level.WARNING, message);
		    throw new SQLException(message);
		} else {
		    String message = Tag.PRODUCT_USER_CONFIG_FAIL
			    + " the \"driverClassName\" property is not defined in the properties file for database "
			    + database + " or the servlet name does not match the url pattern in your web.xml";
		    // ServerLogger.getLogger().log(Level.WARNING, message);
		    throw new SQLException(message);
		}
	    }

	    dataSourceSet.put(database, dataSource);

	}

	Connection connection = dataSource.getConnection();
	return connection;
    }

    /**
     * Closes the connection acquired by
     * {@link DatabaseConfigurator#getConnection(String)} with a call to
     * <code>Connection.close()</code>. <br>
     * Note that Exceptions are trapped to avoid client failure. Stack trace is
     * printed on standard error stream.
     */
    @Override
    public void close(Connection connection) throws SQLException {

	try {
	    if (connection != null) {
		connection.close();
	    }
	} catch (Exception e) {
	    try {
		getLogger().log(Level.WARNING, e.toString());
	    } catch (Exception io) {
		// Should never happen
		io.printStackTrace();
	    }
	}

    }

    /**
     * @return the value of the property {@code default.maxRows} defined in the {@code aceql-server.properties} file at server startup.
     * If property does not exist, returns 0.
     */
    @Override
    public int getMaxRows(String username, String database) throws IOException, SQLException {

	int maxRows = 0;
	if (properties == null) {
	    File file = ServerSqlManager.getAceqlServerProperties();
	    properties = TomcatStarterUtil.getProperties(file);
	}

	String maxRowsStr = properties.getProperty("default.maxRows");

	if (!StringUtils.isNumeric(maxRowsStr)) {
	    throw new IllegalArgumentException("The default.maxRows property is not numeric: " + maxRowsStr);
	}

	maxRows = Integer.parseInt(maxRowsStr);

	return maxRows;
    }

    /**
     * @return <code>user.home/.aceql-server-root/username</code>. (
     *         {@code user.home} is the one of the servlet container).
     */
    @Override
    public File getBlobsDirectory(String username) throws IOException, SQLException {
	String userHome = System.getProperty("user.home");
	if (!userHome.endsWith(File.separator)) {
	    userHome += File.separator;
	}
	userHome += ".aceql-server-root" + File.separator + username;
	File userHomeDir = new File(userHome);
	userHomeDir.mkdirs();
	return userHomeDir;
    }

    /**
     * Creates a static {@code Logger} instance.
     *
     * @return a static {@code Logger} with properties:
     *         <ul>
     *         <li>Name: {@code "DefaultDatabaseConfigurator"}.</li>
     *         <li>Output file pattern:
     *         {@code user.home/.kawansoft/log/AceQL.log}.</li>
     *         <li>Formatter: {@code SimpleFormatter}.</li>
     *         <li>Limit: 200Mb.</li>
     *         <li>Count (number of files to use): 2.</li>
     *         </ul>
     */
    @Override
    public Logger getLogger() throws IOException {
	if (ACEQL_LOGGER != null) {
	    return ACEQL_LOGGER;
	}

	File logDir = new File(SystemUtils.USER_HOME + File.separator + ".kawansoft" + File.separator + "log");
	logDir.mkdirs();

	String pattern = logDir.toString() + File.separator + "AceQL.log";

	ACEQL_LOGGER = Logger.getLogger(DefaultDatabaseConfigurator.class.getName());
	Handler fh = new FileHandler(pattern, 200 * 1024 * 1024, 2, true);
	fh.setFormatter(new SimpleFormatter());
	ACEQL_LOGGER.addHandler(fh);
	return ACEQL_LOGGER;

    }

}
