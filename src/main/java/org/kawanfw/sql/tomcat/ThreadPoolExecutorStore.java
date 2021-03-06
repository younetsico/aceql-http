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

package org.kawanfw.sql.tomcat;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.kawanfw.sql.util.SqlTag;

public class ThreadPoolExecutorStore {

    public static final int DEFAULT_CORE_POOL_SIZE = 100;
    public static final int DEFAULT_MAXIMUM_POOL_SIZE = 200;
    public static final long DEFAULT_KEEP_ALIVE_TIME = 10;
    public static final int DEFAULT_BLOCKING_QUEUE_CAPACITY = 50000;

    private static ThreadPoolExecutor threadPoolExecutor = null;
    private Properties properties = null;

    /**
     * Constructor
     *
     * @param properties the ThreadPoolExecutor configuration is the properties
     */
    public ThreadPoolExecutorStore(Properties properties) {
	this.properties = Objects.requireNonNull(properties, "properties cannot be null!");
    }

    /**
     * Creates the ThreadPoolExecutor that will be used using properties
     *
     * @throws ClassNotFoundException
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public void create() {

	ThreadPoolProperties threadPoolProperties = new ThreadPoolProperties(properties);

	int corePoolSize = threadPoolProperties.getCorePoolSize();
	int maximumPoolSize = threadPoolProperties.getMaximumPoolSize();
	TimeUnit unit = threadPoolProperties.getUnit();
	long keepAliveTime = threadPoolProperties.getKeepAliveTime();
	BlockingQueue<Runnable> workQueue = threadPoolProperties.getWorkQueue();

	threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);

	System.out.println(SqlTag.SQL_PRODUCT_START + "  -> [corePoolSize: " + threadPoolExecutor.getCorePoolSize()
		+ ", maximumPoolSize: " + threadPoolExecutor.getMaximumPoolSize() + ", unit: " + unit + ", ");
	System.out
		.println(SqlTag.SQL_PRODUCT_START + "  ->  keepAliveTime: " + threadPoolExecutor.getKeepAliveTime(unit)
			+ ", workQueue: " + threadPoolExecutor.getQueue().getClass().getSimpleName() + "("
			+ threadPoolExecutor.getQueue().remainingCapacity() + ")]");
    }

    /**
     * Gets the static instance of ThreadPoolExecutor to be used in main servlet
     *
     * @return the threadPoolExecutor the instance to be used in main servlet
     */
    public static ThreadPoolExecutor getThreadPoolExecutor() {
	return threadPoolExecutor;
    }


}
