/**
 * 
 */
package org.kawanfw.test.util;

import java.io.StringReader;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;

import org.kawanfw.sql.api.util.SqlUtil;
import org.kawanfw.test.parms.ConnectionLoader;

/**
 * @author Nicolas de Pomereu
 *
 */
public class StoredProcedureTest {

    /**
     * 
     */
    public StoredProcedureTest() {
	
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
	
	// Change it to change test SQL engine
	ConnectionLoader.sqlEngine = SqlUtil.POSTGRESQL;
	
	Connection connection = ConnectionLoader.getLocalConnection();
	
	if (ConnectionLoader.sqlEngine.equals(SqlUtil.MYSQL)) {
	    testMySqlStoredProcedure(connection);
	}
	else if (ConnectionLoader.sqlEngine.equals(SqlUtil.POSTGRESQL)) {
	    testPostrgreSqlStoredProcedures(connection);
	}
	else if (ConnectionLoader.sqlEngine.equals(SqlUtil.SQL_SERVER)) {
	    testSqlServerSoredProcedure(connection);
	}

	connection.close();
	
    }
    
    public static void testSqlServerSoredProcedure(Connection connection) throws SQLException {
	CallableStatement callableStatement = connection.prepareCall("{call ProcedureName(?, ?, ?) }");
	callableStatement.registerOutParameter(3, Types.INTEGER);
	callableStatement.setInt(1, 0);
	callableStatement.setInt(2, 2);
	ResultSet rs = callableStatement.executeQuery();
	
	while (rs.next()) {
	    System.out.println(rs.getString(1));
	}
	
	int out3 = callableStatement.getInt(3);
	
	callableStatement.close();
	
	System.out.println();
	System.out.println("out3: " + out3);
	
    }

    public static void testMySqlStoredProcedure(Connection connection) throws SQLException {
	CallableStatement callableStatement = connection.prepareCall("{ call demoSp(?, ?, ?) }");
	callableStatement.registerOutParameter(2, Types.INTEGER);
	callableStatement.registerOutParameter(3, Types.INTEGER);
	callableStatement.setString(1, "test");
	callableStatement.setInt(2, 12);
	ResultSet rs = callableStatement.executeQuery();
	
	while (rs.next()) {
	    System.out.println(rs.getString(1));
	}
	
	int out2 = callableStatement.getInt(2);
	int out3 = callableStatement.getInt(3);
	
	callableStatement.close();
	
	System.out.println();
	System.out.println("out2: " + out2);
	System.out.println("out3: " + out3);
	
    }

    public static void testPostrgreSqlStoredProcedures(Connection conn) throws SQLException {
	CallableStatement upperProc = conn.prepareCall("{ ? = call upper( ? ) }");
	upperProc.registerOutParameter(1, Types.VARCHAR);
	upperProc.setString(2, "lowercase to uppercase");
	upperProc.executeUpdate();
	String upperCased = upperProc.getString(1);
	upperProc.close();
	
	System.out.println("upperCased: " + upperCased);
    }
    
    
    public static void parseJson() throws Exception {
	
	/*
 
 	"parameters_out_per_name":[
            {
                "out_param_two":"13"
            },
            {
                "out_param_three":"12"
            }
    	],
	 */
	
	String jsonContent = 
	"[{\"out_param_two\":\"13\"}, {\"out_param_three\":\"12\"}]";
	
        JsonReader reader = Json.createReader(new StringReader(jsonContent));
        JsonArray jsonArray = reader.readArray();
	
	for (JsonValue jsonValue : jsonArray) {
	    System.out.println(jsonValue.toString());
	    JsonObject jsonObject = (JsonObject)jsonValue;
	    System.out.println(jsonObject.keySet());;
	}
	    
    }


}
