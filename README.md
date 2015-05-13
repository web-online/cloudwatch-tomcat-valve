## Cloud Watch Tomcat Valve
---
### Introduction
A [Tomcat Valve](https://tomcat.apache.org/tomcat-7.0-doc/config/valve.html) that aggregates statistics from the Tomcat [Request](https://tomcat.apache.org/tomcat-7.0-doc/api/org/apache/catalina/connector/Request.html) and [Response](https://tomcat.apache.org/tomcat-7.0-doc/api/org/apache/catalina/connector/Response.html) and [periodically](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/ScheduledThreadPoolExecutor.html#scheduleAtFixedRate%28java.lang.Runnable, long, long, java.util.concurrent.TimeUnit%29) pushes the statistics to [Amazon CloudWatch](http://docs.aws.amazon.com/AmazonCloudWatch/latest/DeveloperGuide/WhatIsCloudWatch.html).

Currently, the only statistic that is captured is **ElapsedTime**, which is the time taken to process the request in milliseconds. This value is aggregated and pushed out to CloudWatch with the following [dimensions](http://docs.aws.amazon.com/AmazonCloudWatch/latest/DeveloperGuide/cloudwatch_concepts.html#Dimension):
- InstanceId (retrieved from [metadata](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html))
- AutoScalingGroupName (retrieved from the instance tag *aws:autoscaling:groupName* [which is automatically added to instances created by Auto Scaling](http://docs.aws.amazon.com/AutoScaling/latest/DeveloperGuide/ASTagging.html#tag_restrictions)). If the AutoScalingGroupName cannot be found (because the instance is not auto scaled) a warning is reported and the AutoScalingGroupName dimension will not be used for pushing metrics.

This Valve may be used at the Engine, Host or Context level as required. Normally, this Valve would be used at the Host level.

### Attributes
Attribute                     | Description
----------------------------- | -----------
**className**                 | Java class name of the implementation to use. This MUST be set to **org.web.online.cloudwatch.tomcat.valve.CloudWatchValve**
namespace                     | The namespace to use when pushing data to CloudWatch. If not specified, the default is *CloudWatchValve*
initialDelay                  | Integer value indicating the initial delay in *timeUnit* before the first periodic push of data to CloudWatch. It is an error if this value is calculated to be less than 1 minute. If not specified, the default of *1* is used.
period                        | Integer value indicating the period in *timeUnit* between successive pushes of data to CloudWatch. It is an error if this value is calculated to be less than 1 minute. If not specified, the default of *1* is used.
timeUnit                      | One of *DAYS*, *HOURS*, *MICROSECONDS*, *MILLISECONDS*, *MINUTES*, *NANOSECONDS*, or *SECONDS*. If not specified, the default of *MINUTES* is used.

### Example
    <Valve className="org.web.online.cloudwatch.tomcat.valve.CloudWatchValve" />

### Build
[Maven](https://maven.apache.org/) is used to build and the [Apache Maven Shade Plugin](https://maven.apache.org/plugins/maven-shade-plugin/shade-mojo.html) is used to create a single jar *-with-dependencies* that includes the necessary AWS JDK and dependency libraries to run.
    mvn clean package

### Install
To install, the appropriate jar from the *target* folder should be placed in the $CATALINA_HOME/lib directory. If the *-with-dependencies* jar is used no other jars should be necessary. If the standalone jar is used then the relavant dependent jars will also need to be installed.

Once the jar(s) are installed, the server.xml file should be edited as in the Example above, optionally including any of the attributes to customize the behavior.

Additionally, this implementation makes use of the [AWS SDK for Java](http://aws.amazon.com/sdk-for-java/) to look up the AutoScalingGroupName and to push metrics to CloudWatch. The [DefaultAWSCredentialsProviderChain](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html) is used for authentication which looks for credentials in this order:
- Environment Variables - AWS_ACCESS_KEY_ID and AWS_SECRET_KEY
- Java System Properties - aws.accessKeyId and aws.secretKey
- Credential profiles file at the default location (~/.aws/credentials) shared by all AWS SDKs and the AWS CLI
- Instance profile credentials delivered through the Amazon EC2 metadata service ([more info](http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/java-dg-roles.html))
