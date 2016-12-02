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

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StatisticSet;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.TagDescription;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

/**
 * Tests for functionality in ElapsedTimeAggregator
 * @author web-online
 */
public class ElapsedTimeAggregatorTest {

    private static final Logger logger = Logger.getLogger("ElapsedTimeAggregatorTest");

    private final ElapsedTimeAggregator instanceOnlyAggregator;
    private final ElapsedTimeAggregator asgAggregator;
    private final AmazonEC2 ec2Client = mock(AmazonEC2.class);
    private final AmazonCloudWatch cloudWatchClient = mock(AmazonCloudWatch.class);
    private final Region region = Region.getRegion(Regions.US_WEST_1);
    private final String namespace = "TEST";
    private final String asgName = namespace;
    private final String instanceId = "i-500f6ca6";


    /**
     * Default constructor
     */
    public ElapsedTimeAggregatorTest() {
        when(ec2Client.describeTags(any(DescribeTagsRequest.class))).
                thenReturn(new DescribeTagsResult());

        instanceOnlyAggregator = new ElapsedTimeAggregator(namespace, region, instanceId, null, ec2Client, cloudWatchClient);

        when(ec2Client.describeTags(any(DescribeTagsRequest.class))).
                thenReturn(new DescribeTagsResult().withTags(
                        new TagDescription().
                                withKey("aws:autoscaling:groupName").
                                withValue(asgName)
                ));
        asgAggregator = new ElapsedTimeAggregator(namespace, region, instanceId, null, ec2Client, cloudWatchClient);
    }
    
    /**
     * Test that aggregate and run do what they are supposed to do
     */
    @Test
    public void testInstanceOnlyAggregatorAggregateAndRun() {
        ArgumentCaptor<PutMetricDataRequest> putMetricRequestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        PutMetricDataRequest putMetricDataRequest;

        instanceOnlyAggregator.aggregate(100);
        instanceOnlyAggregator.aggregate(300);
        instanceOnlyAggregator.aggregate(500);
        instanceOnlyAggregator.aggregate(200);
        instanceOnlyAggregator.aggregate(100);
        instanceOnlyAggregator.run();
        verify(cloudWatchClient).putMetricData(putMetricRequestCaptor.capture());
        putMetricDataRequest = putMetricRequestCaptor.getValue();
        assertEquals(namespace, putMetricDataRequest.getNamespace());
        assertEquals(1, putMetricDataRequest.getMetricData().size());
        MetricDatum instanceOnlyAggregatorMetricDatum = putMetricDataRequest.getMetricData().get(0);
        assertEquals("ElapsedTime", instanceOnlyAggregatorMetricDatum.getMetricName());
        assertEquals("Milliseconds", instanceOnlyAggregatorMetricDatum.getUnit());
        assertEquals(1, instanceOnlyAggregatorMetricDatum.getDimensions().size());
        Dimension instanceOnlyAggregatorDimension = instanceOnlyAggregatorMetricDatum.getDimensions().get(0);
        assertEquals("InstanceId", instanceOnlyAggregatorDimension.getName());
        assertEquals(instanceId, instanceOnlyAggregatorDimension.getValue());
        assertNull(instanceOnlyAggregatorMetricDatum.getValue());
        StatisticSet instanceOnlyAggregatorStatisticSet = instanceOnlyAggregatorMetricDatum.getStatisticValues();
        assertEquals(100, instanceOnlyAggregatorStatisticSet.getMinimum(), 0);
        assertEquals(500, instanceOnlyAggregatorStatisticSet.getMaximum(), 0);
        assertEquals(5, instanceOnlyAggregatorStatisticSet.getSampleCount(), 0);
        assertEquals(1200, instanceOnlyAggregatorStatisticSet.getSum(), 0);
    }
    
    /**
     * Test that aggregate and run do what they are supposed to do
     */
    @Test
    public void testASGAggregatorAggregateAndRun() {
        ArgumentCaptor<PutMetricDataRequest> putMetricRequestCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        PutMetricDataRequest putMetricDataRequest;

        asgAggregator.aggregate(100);
        asgAggregator.aggregate(300);
        asgAggregator.aggregate(500);
        asgAggregator.aggregate(200);
        asgAggregator.aggregate(100);
        asgAggregator.run();
        verify(cloudWatchClient).putMetricData(putMetricRequestCaptor.capture());
        putMetricDataRequest = putMetricRequestCaptor.getValue();
        assertEquals(namespace, putMetricDataRequest.getNamespace());
        assertEquals(2, putMetricDataRequest.getMetricData().size());
        for (MetricDatum asgAggregatorMetricDatum : putMetricDataRequest.getMetricData()) {
            assertEquals("ElapsedTime", asgAggregatorMetricDatum.getMetricName());
            assertEquals("Milliseconds", asgAggregatorMetricDatum.getUnit());
            assertEquals(1, asgAggregatorMetricDatum.getDimensions().size());
            Dimension asgAggregatorDimension = asgAggregatorMetricDatum.getDimensions().get(0);
            if ("InstanceId".equals(asgAggregatorDimension.getName())) {
                assertEquals(instanceId, asgAggregatorDimension.getValue());
            } else if ("AutoScalingGroupName".equals(asgAggregatorDimension.getName())) {
                assertEquals(asgName, asgAggregatorDimension.getValue());
            } else {
                fail("unexpected dimansion name: " + asgAggregatorDimension.getName());
            }
            assertNull(asgAggregatorMetricDatum.getValue());
            StatisticSet asgAggregatorStatisticSet = asgAggregatorMetricDatum.getStatisticValues();
            assertEquals(100, asgAggregatorStatisticSet.getMinimum(), 0);
            assertEquals(500, asgAggregatorStatisticSet.getMaximum(), 0);
            assertEquals(5, asgAggregatorStatisticSet.getSampleCount(), 0);
            assertEquals(1200, asgAggregatorStatisticSet.getSum(), 0);
        }
    }

    /**
     * Test of toString method, of class ElapsedTimeAggregator.
     */
    @Test
    public void testToString() {
        logger.info("toString");
        String instanceOnlyExpected =
            "{" +
                "ElapsedTimeAggregator: {" +
                    "region: " + region.getName() + ", " +
                    "namespace: " + namespace + ", " +
                    "metrics: [" +
                        "{" +
                            "metricName: ElapsedTime, " +
                            "dimensions: [" +
                                "{Name: InstanceId,Value: " + instanceId + "}" +
                            "]" +
                        "}" +
                    "]" +
                "}" +
            "}";
        assertEquals(instanceOnlyExpected, instanceOnlyAggregator.toString());
        String asgExpected =
            "{" +
                "ElapsedTimeAggregator: {" +
                    "region: " + region.getName() + ", " +
                    "namespace: " + namespace + ", " +
                    "metrics: [" +
                        "{" +
                            "metricName: ElapsedTime, " +
                            "dimensions: [" +
                                "{Name: InstanceId,Value: " + instanceId + "}" +
                            "]" +
                        "}, " +
                        "{" +
                            "metricName: ElapsedTime, " +
                            "dimensions: [" +
                                "{Name: AutoScalingGroupName,Value: " + asgName + "}" +
                            "]" +
                        "}" +
                    "]" +
                "}" +
            "}";
        assertEquals(asgExpected, asgAggregator.toString());
    }

    /**
     * A test of the concurrency of the aggregate and run methods
     * This test is ignored because it takes a long time
     * 
     * synchronized:
        May 27, 2015 4:09:10 PM org.web.online.cloudwatch.tomcat.valve.ElapsedTimeAggregatorTest testConcurrency
        INFO: instanceOnlyAggregator:1 -> 5 ms
        May 27, 2015 4:09:11 PM org.web.online.cloudwatch.tomcat.valve.ElapsedTimeAggregatorTest testConcurrency
        INFO: instanceOnlyAggregator:5 -> 6 ms
        May 27, 2015 4:09:11 PM org.web.online.cloudwatch.tomcat.valve.ElapsedTimeAggregatorTest testConcurrency
        INFO: instanceOnlyAggregator:10 -> 11 ms
        May 27, 2015 4:09:11 PM org.web.online.cloudwatch.tomcat.valve.ElapsedTimeAggregatorTest testConcurrency
        INFO: instanceOnlyAggregator:20 -> 18 ms
        May 27, 2015 4:09:12 PM org.web.online.cloudwatch.tomcat.valve.ElapsedTimeAggregatorTest testConcurrency
        INFO: instanceOnlyAggregator:50 -> 106 ms
        Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.06 sec
     * ConcurrentLinkedQueue:
        May 27, 2015 4:31:42 PM org.web.online.cloudwatch.tomcat.valve.ElapsedTimeAggregatorTest testConcurrency
        INFO: instanceOnlyAggregator:1 -> 6 ms
        May 27, 2015 4:31:42 PM org.web.online.cloudwatch.tomcat.valve.ElapsedTimeAggregatorTest testConcurrency
        INFO: instanceOnlyAggregator:5 -> 6 ms
        May 27, 2015 4:31:42 PM org.web.online.cloudwatch.tomcat.valve.ElapsedTimeAggregatorTest testConcurrency
        INFO: instanceOnlyAggregator:10 -> 11 ms
        May 27, 2015 4:31:42 PM org.web.online.cloudwatch.tomcat.valve.ElapsedTimeAggregatorTest testConcurrency
        INFO: instanceOnlyAggregator:20 -> 17 ms
        May 27, 2015 4:31:43 PM org.web.online.cloudwatch.tomcat.valve.ElapsedTimeAggregatorTest testConcurrency
        INFO: instanceOnlyAggregator:50 -> 42 ms
        Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.399 sec
     * @throws java.lang.InterruptedException
     */
    //@Ignore
    @Test
    public void testConcurrency() throws InterruptedException {
        logger.info("testConcurrency");

        final int loopSize = 100;
        final int sleepTime = 10;
        Runnable runnableA = new Runnable() { @Override public void run() { // try {
            for (int i = 0; i < loopSize; i++) {
                instanceOnlyAggregator.aggregate(100);
                //Thread.sleep(sleepTime);
                instanceOnlyAggregator.aggregate(300);
                //Thread.sleep(sleepTime);
                instanceOnlyAggregator.run();
                //Thread.sleep(sleepTime);
                instanceOnlyAggregator.aggregate(500);
                //Thread.sleep(sleepTime);
                instanceOnlyAggregator.aggregate(200);
                //Thread.sleep(sleepTime);
                instanceOnlyAggregator.run();
                //Thread.sleep(sleepTime);
                instanceOnlyAggregator.aggregate(100);
            }
        //} catch (InterruptedException ex) {}
        }};
        Runnable runnable = new Runnable() { @Override public void run() { // try {
            for (int i = 0; i < loopSize; i++) {
                instanceOnlyAggregator.offer(100);
                //Thread.sleep(sleepTime);
                instanceOnlyAggregator.offer(300);
                //Thread.sleep(sleepTime);
                instanceOnlyAggregator.run();
                //Thread.sleep(sleepTime);
                instanceOnlyAggregator.offer(500);
                //Thread.sleep(sleepTime);
                instanceOnlyAggregator.offer(200);
                //Thread.sleep(sleepTime);
                instanceOnlyAggregator.run();
                //Thread.sleep(sleepTime);
                instanceOnlyAggregator.offer(100);
            }
        //} catch (InterruptedException ex) {}
        }};

        for (int numberOfRunnables : Arrays.asList(1, 5, 10, 20, 50)) {
            List<Runnable> runnables = new ArrayList<Runnable>();
            for (int r = 0; r < numberOfRunnables; r++) {
                runnables.add(runnableA);
            }
            String name = "instanceOnlyAggregator:"+numberOfRunnables;
            int loops = 10;
            long start = System.currentTimeMillis();
            for (int r = 0; r < loops; r++) {
                assertConcurrent(name, runnables, 1);
            }
            logger.log(Level.INFO, "{0} -> {1} ms", new Object[]{name, (System.currentTimeMillis()-start)/loops});
        }
    }

    /*
     * https://github.com/junit-team/junit/wiki/Multithreaded-code-and-concurrency
     */
    public static void assertConcurrent(final String message, final List<? extends Runnable> runnables, final int maxTimeoutSeconds) throws InterruptedException {
          final int numThreads = runnables.size();
          final List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<Throwable>());
          final ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
          try {
            final CountDownLatch allExecutorThreadsReady = new CountDownLatch(numThreads);
            final CountDownLatch afterInitBlocker = new CountDownLatch(1);
            final CountDownLatch allDone = new CountDownLatch(numThreads);
            for (final Runnable submittedTestRunnable : runnables) {
              threadPool.submit(new Runnable() {
                @Override
                public void run() {
                  allExecutorThreadsReady.countDown();
                  try {
                    afterInitBlocker.await();
                    submittedTestRunnable.run();
                  } catch (final Throwable e) {
                    exceptions.add(e);
                  } finally {
                    allDone.countDown();
                  }
                }
              });
            }
            // wait until all threads are ready
            assertTrue("Timeout initializing threads! Perform long lasting initializations before passing runnables to assertConcurrent", allExecutorThreadsReady.await(runnables.size() * 10, TimeUnit.MILLISECONDS));
            // start all test runners
            afterInitBlocker.countDown();
            assertTrue(message +" timeout! More than " + maxTimeoutSeconds + " seconds", allDone.await(maxTimeoutSeconds, TimeUnit.SECONDS));
          } finally {
            threadPool.shutdownNow();
          }
          assertTrue(message + "failed with exception(s)" + exceptions, exceptions.isEmpty());
        }
    
}
