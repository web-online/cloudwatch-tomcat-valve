/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.web.online.cloudwatch.tomcat.valve;

import java.io.IOException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletException;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * A <a href="https://tomcat.apache.org/">Tomcat</a> <a href="https://tomcat.apache.org/tomcat-7.0-doc/api/org/apache/catalina/Valve.html">Valve</a> that sends information to <a href="http://aws.amazon.com/cloudwatch/">AWS CloudWatch</a>.
 * @author web-online
 */
public class CloudWatchValve extends ValveBase {

    private static final Log log = LogFactory.getLog(CloudWatchValve.class);

    /**
     * The initial delay before sending metrics to cloud watch. Default 1
     */
    private long initialDelay = 1;

    /**
     * The period of time between successive calls to send metrics to cloud watch. Default 1
     */
    private long period = 1;

    /**
     * The unit of time for initialDelay and period. Default MINUTES
     */
    private String timeUnitString = "MINUTES";

    /**
     * The name space to use when pushing metrics to CloudWatch. Default "CloudWatchValve"
     */
    private String namespace = "CloudWatchValve";

    /**
     * A single executor for periodic execution
     */
    private ScheduledThreadPoolExecutor executor;

    /**
     * Aggregator object to collect statistics and periodically push to
     * Cloud Watch
     */
    private ElapsedTimeAggregator aggregator;

    /**
     * Minimum initialDelay and period
     */
    private final long minimumMinutes = 1;

    /**
     * Start this component.
     * @throws LifecycleException 
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {
        super.startInternal();

        TimeUnit timeUnit = TimeUnit.valueOf(timeUnitString);

        // ensure the initialDelay is at least a minimum
        if (timeUnit.toMinutes(initialDelay) < minimumMinutes) {
            throw new LifecycleException("initialDelay ("
                    + initialDelay + " " + timeUnit + ") must be less than "
                    + minimumMinutes + " minutes");
        }

        // ensure the period is at least a minimum
        if (timeUnit.toMinutes(period) < minimumMinutes) {
            throw new LifecycleException("period ("
                    + period + " " + timeUnit + ") must be less than "
                    + minimumMinutes + " minutes");
        }

        aggregator = new ElapsedTimeAggregator(namespace);

        executor = new ScheduledThreadPoolExecutor(1);
        executor.scheduleAtFixedRate(aggregator, initialDelay, period, timeUnit);

        log.info(aggregator + " scheduled to run in " +
                initialDelay + " " + timeUnit + " and then periodically every "
                + period + " " + timeUnit);
    }


    /**
     * Stop this component.
     * @throws LifecycleException 
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        super.stopInternal();
        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * Collect metrics and send to CloudWatch.
     * @param rqst
     * @param rspns
     * @throws IOException
     * @throws ServletException 
     */
    @Override
    public void invoke(Request rqst, Response rspns) throws IOException, ServletException {

        Valve nextValve = getNext();
        if (nextValve != null) {
            nextValve.invoke(rqst, rspns);
        }

        long time = System.currentTimeMillis() - rqst.getCoyoteRequest().getStartTime();

        aggregator.aggregate(time);
    }
    
    /**
     * Set the initial delay before periodically sending metrics to Cloud Watch.
     * @param initialDelay the initial delay with a unit of timeUnit
     */
    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    /**
     * Set the period 
     * @param period the amount of time between successive calls to send metrics
     * to Cloud Watch
     */
    public void setPeriod(long period) {
        this.period = period;
    }

    /**
     * Set the time unit for the initialDelay and period
     * @param timeUnitString a string value of a TimeUnit enum
     */
    public void setTimeUnit(String timeUnitString) {
        this.timeUnitString = timeUnitString;
    }

    /**
     * Set the namespace to use for pushing data to CloudWatch
     * @param namespace namespace to use
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
