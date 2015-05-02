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

import java.util.logging.Logger;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardHost;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author web-online
 */
public class CloudWatchValveTest {
    
    private static final Logger logger = Logger.getLogger("CloudWatchValveTest");
    private CloudWatchValve instance;

    @Before
    public void beforeTest() throws LifecycleException {
        instance = new CloudWatchValve();
        instance.setContainer(new StandardHost());
    }

    /**
     * Test of startInternal method, of class CloudWatchValve.
     * @throws org.apache.catalina.LifecycleException
     */
    @Test
    public void testStartInternalBadTimeUnit() throws LifecycleException {
        logger.info("startInternalBadTimeUnit");
        instance.setTimeUnit("seconds");
        try {
            instance.start();
            fail("should've thrown an exception");
        } catch (LifecycleException ex) {
            assertEquals(IllegalArgumentException.class, ex.getCause().getClass());
            assertEquals("No enum constant java.util.concurrent.TimeUnit.seconds", ex.getCause().getMessage());
        }
    }

    /**
     * Test of startInternal method, of class CloudWatchValve.
     * @throws org.apache.catalina.LifecycleException
     */
    @Test
    public void testStartInternalBadInitialDelay() throws LifecycleException {
        logger.info("startInternalBadInitialDelay");
        instance.setTimeUnit("SECONDS");
        instance.setInitialDelay(30L);
        try {
            instance.start();
            fail("should've throw an exception");
        } catch (LifecycleException ex) {
            assertEquals(LifecycleException.class, ex.getCause().getClass());
            assertEquals("initialDelay (30 SECONDS) must be less than 1 minutes", ex.getCause().getMessage());

        }
    }

    /**
     * Test of startInternal method, of class CloudWatchValve.
     * @throws org.apache.catalina.LifecycleException
     */
    @Test
    public void testStartInternalBadPeriod() throws LifecycleException {
        logger.info("startInternalBadPeriod");
        instance.setTimeUnit("MILLISECONDS");
        instance.setInitialDelay(300L);
        try {
            instance.start();
            fail("should've throw an exception");
        } catch (LifecycleException ex) {
            assertEquals(LifecycleException.class, ex.getCause().getClass());
            assertEquals("initialDelay (300 MILLISECONDS) must be less than 1 minutes", ex.getCause().getMessage());
        }
    }

}
