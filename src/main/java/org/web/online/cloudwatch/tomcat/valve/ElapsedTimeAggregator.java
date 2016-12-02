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
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.cloudwatch.model.StatisticSet;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.util.EC2MetadataUtils;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * A Runnable class for aggregating ElapsedTime values and sending the values
 * in a StatisticSet to Cloud Watch.
 * @author web-online
 */
public class ElapsedTimeAggregator implements Runnable {

    private static final Log log = LogFactory.getLog(ElapsedTimeAggregator.class);

    /**
     * Cloud Watch client
     */
    private final AmazonCloudWatch cloudWatchClient;

    /**
     * Metric data request
     */
    private final PutMetricDataRequest putMetricDataRequest;

    /**
     * zero value Metric data request
     */
    private final PutMetricDataRequest zeroValuePutMetricDataRequest;

    /**
     * lock object for synchronization
     */
    private final Object lock = new Object();

    /**
     * minimum aggregate value
     */
    private double minimum = Double.MAX_VALUE;

    /**
     * maximum aggregate value
     */
    private double maximum = Double.MIN_VALUE;

    /**
     * number of values sampled
     */
    private double sampleCount = 0;

    /**
     * sum of all values
     */
    private double sum = 0;

    /**
     * region
     */
    private final Region region;

    private final ConcurrentLinkedQueue<Double> values = new ConcurrentLinkedQueue<Double>();

    /**
     * Construct the instance querying EC2 meta data to get the InstanceId
     * and querying the tags of this instance to get the
     * AutoScalingGroupName (tag key == "aws:autoscaling:groupName").
     * 
     * Additionally sets up the Cloud Watch metric with the given namespace,
     * a metric name of "ElapsedTime" and dimensions
     * of InstanceId and AutoScalingGroupName.
     * 
     * @param namespace namespace value to use to push data to CloudWatch
     * @throws IllegalStateException if instance/ASG details not able to be
     * retrieved
     */
    public ElapsedTimeAggregator(String namespace) {
        this(namespace, Regions.getCurrentRegion(), EC2MetadataUtils.getInstanceId(), null, new AmazonEC2Client(), new AmazonCloudWatchClient());
    }

    /**
     * Construct the instance. Sets up the Cloud Watch metric with the given parameters.
     * 
     * @param namespace namespace value to use to push data to CloudWatch
     * @param region region used to look for instance tags and to push CloudWatch data
     * @param instanceId instanceId to use as dimension
     * @param asgName autoscaling group name to use as dimension
     * @param ec2Client ec2 client to use in querying tags to find ASG name
     * @param cloudWatchClient cloud watch client to use to push CloudWatch data
     */
    public ElapsedTimeAggregator(String namespace, Region region, String instanceId, String asgName, AmazonEC2 ec2Client, AmazonCloudWatch cloudWatchClient) {

        this.region = region;

        if (instanceId == null) {
            throw new IllegalStateException("unable to find instance id");
        }

        // get the ASG name
        if (asgName == null) {
            ec2Client.setRegion(region);
            List<TagDescription> tagDescriptions = ec2Client.describeTags(
                    new DescribeTagsRequest().withFilters(
                        new Filter().withName("resource-id").withValues(instanceId),
                        new Filter().withName("key").withValues("aws:autoscaling:groupName")
                    )
            ).getTags();
            if (tagDescriptions.size() == 1) {
                asgName = tagDescriptions.get(0).getValue();
            }
        }

        if (asgName == null) {
            log.warn("unable to determine AutoScalingGroupName for " + instanceId +
                    ". No statistics will be published under the AutoScalingGroupName dimension");
        }

        cloudWatchClient.setRegion(region);
        this.cloudWatchClient = cloudWatchClient;

        String metricName = "ElapsedTime";
        Dimension instanceDimension =
                new Dimension().withName("InstanceId").withValue(instanceId);
        StatisticSet statisticSet = new StatisticSet();

        // set up the static MetricDatum and associate to the PutMetricDataRequest
        MetricDatum instanceMetricDatum = new MetricDatum().
                withMetricName(metricName).
                withDimensions(instanceDimension).
                withStatisticValues(statisticSet).
                withUnit(StandardUnit.Milliseconds);
        putMetricDataRequest = new PutMetricDataRequest().
                withNamespace(namespace).
                withMetricData(instanceMetricDatum);

        // and a special zero value request since statistic set doesn't
        // support zero values
        MetricDatum zeroValueInstanceMetricDatum = new MetricDatum().
                withMetricName(metricName).
                withDimensions(instanceDimension).
                withValue(0d).
                withUnit(StandardUnit.Milliseconds);
        zeroValuePutMetricDataRequest = new PutMetricDataRequest().
                withNamespace(namespace).
                withMetricData(zeroValueInstanceMetricDatum);

        // also push metrics for the ASG dimension if we have an ASG name
        if (asgName != null) {
            Dimension asgDimension =
                    new Dimension().withName("AutoScalingGroupName").withValue(asgName);
            MetricDatum asgMetricDatum = new MetricDatum().
                    withMetricName(metricName).
                    withDimensions(asgDimension).
                    withStatisticValues(statisticSet).
                    withUnit(StandardUnit.Milliseconds);
            putMetricDataRequest.withMetricData(asgMetricDatum);
            MetricDatum zeroValueAsgMetricDatum = new MetricDatum().
                    withMetricName(metricName).
                    withDimensions(asgDimension).
                    withValue(0d).
                    withUnit(StandardUnit.Milliseconds);
            zeroValuePutMetricDataRequest.withMetricData(zeroValueAsgMetricDatum);
        }
    }

    /**
     * Collect the aggregated values (min, max, count, sum) into a
     * StatisticSet and send the data to Cloud Watch
     */
    @Override
    public void run() {

        PutMetricDataRequest localPutMetricDataRequest = zeroValuePutMetricDataRequest;
        MetricDatum metricDatum = localPutMetricDataRequest.getMetricData().get(0);

        if (values.peek() != null) {
            localPutMetricDataRequest = putMetricDataRequest;
            metricDatum = localPutMetricDataRequest.getMetricData().get(0);
            StatisticSet statisticSet = metricDatum.getStatisticValues();
            double _maximum = Double.MIN_VALUE;
            double _minimum = Double.MAX_VALUE;
            double _sampleCount = 0;
            double _sum = 0;
            Double value = values.poll();
            while (value != null) {
                if (value < _minimum) {
                    _minimum = value;
                }
                if (value > _maximum) {
                    _maximum = value;
                }
                _sampleCount++;
                _sum += value;
                value = values.poll();
            }
            statisticSet.setMaximum(_maximum);
            statisticSet.setMinimum(_minimum);
            statisticSet.setSampleCount(_sampleCount);
            statisticSet.setSum(_sum);
        }

        if (sampleCount > 0) {
            localPutMetricDataRequest = putMetricDataRequest;
            metricDatum = localPutMetricDataRequest.getMetricData().get(0);
            StatisticSet statisticSet = metricDatum.getStatisticValues();
            synchronized (lock) {
                statisticSet.setMaximum(maximum);
                statisticSet.setMinimum(minimum);
                statisticSet.setSampleCount(sampleCount);
                statisticSet.setSum(sum);

                minimum = Double.MAX_VALUE;
                maximum = Double.MIN_VALUE;
                sampleCount = 0;
                sum = 0;
            }
        }

        metricDatum.setTimestamp(new Date());

        if (log.isDebugEnabled()) {
            log.debug("sending " + localPutMetricDataRequest);
        }

        cloudWatchClient.putMetricData(localPutMetricDataRequest);
    }

    public void offer(double value) {
        values.offer(value);
    }

    /**
     * Aggregate the elapsed time value into the private fields of this
     * instance (min, max, count, sum).
     * @param value the elapsed time to aggregate
     */
    public void aggregate(double value) {
        synchronized (lock) {
            if (value < minimum) {
                minimum = value;
            }
            if (value > maximum) {
                maximum = value;
            }
            sampleCount++;
            sum += value;
        }
    }

    /**
     * @return a string representation of this object
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{ElapsedTimeAggregator: {");
        String namespace = putMetricDataRequest.getNamespace();
        sb.append("region: ").append(region).append(", ");
        sb.append("namespace: ").append(namespace).append(", ");
        sb.append("metrics: [");
        String msep = "";
        for (MetricDatum metricDatum : putMetricDataRequest.getMetricData()) {
            sb.append(msep).append("{metricName: ").append(metricDatum.getMetricName()).append(", ");
            sb.append("dimensions: [");
            String dsep = "";
            for (Dimension dimension : metricDatum.getDimensions()) {
                sb.append(dsep).append(dimension);
                dsep = ", ";
            }
            sb.append("]}");
            msep = ", ";
        }
        sb.append("]}}");
        return sb.toString();
    }
}
