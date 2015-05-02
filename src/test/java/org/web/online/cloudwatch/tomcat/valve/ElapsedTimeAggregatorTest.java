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
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.TagDescription;
import java.util.logging.Logger;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for functionality in ElapsedTimeAggregator
 * @author web-online
 */
public class ElapsedTimeAggregatorTest {

    private static final Logger logger = Logger.getLogger("ElapsedTimeAggregatorTest");

    private final ElapsedTimeAggregator instanceOnlyAggregator;
    private final ElapsedTimeAggregator asgAggregator;

    /**
     * Default constructor
     */
    public ElapsedTimeAggregatorTest() {
        AmazonEC2 ec2Client = mock(AmazonEC2.class);
        AmazonCloudWatch cloudWatchClient = mock(AmazonCloudWatch.class);
        Region region = Region.getRegion(Regions.US_WEST_1);

        when(ec2Client.describeTags(any(DescribeTagsRequest.class))).
                thenReturn(new DescribeTagsResult());

        instanceOnlyAggregator = new ElapsedTimeAggregator("TEST", region, "i-500f6ca6", null, ec2Client, cloudWatchClient);

        when(ec2Client.describeTags(any(DescribeTagsRequest.class))).
                thenReturn(new DescribeTagsResult().withTags(
                        new TagDescription().
                                withKey("aws:autoscaling:groupName").
                                withValue("TEST")
                ));
        asgAggregator = new ElapsedTimeAggregator("TEST", region, "i-500f6ca6", null, ec2Client, cloudWatchClient);
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
                    "region: us-west-1, " +
                    "namespace: TEST, " +
                    "metrics: [" +
                        "{" +
                            "metricName: ElapsedTime, " +
                            "dimensions: [" +
                                "{Name: InstanceId,Value: i-500f6ca6}" +
                            "]" +
                        "}" +
                    "]" +
                "}" +
            "}";
        assertEquals(instanceOnlyExpected, instanceOnlyAggregator.toString());
        String asgExpected =
            "{" +
                "ElapsedTimeAggregator: {" +
                    "region: us-west-1, " +
                    "namespace: TEST, " +
                    "metrics: [" +
                        "{" +
                            "metricName: ElapsedTime, " +
                            "dimensions: [" +
                                "{Name: InstanceId,Value: i-500f6ca6}" +
                            "]" +
                        "}, " +
                        "{" +
                            "metricName: ElapsedTime, " +
                            "dimensions: [" +
                                "{Name: AutoScalingGroupName,Value: TEST}" +
                            "]" +
                        "}" +
                    "]" +
                "}" +
            "}";
        assertEquals(asgExpected, asgAggregator.toString());
    }
    
}
