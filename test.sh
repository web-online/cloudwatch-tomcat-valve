#!/bin/bash

echo `date` here we go

while true ; do
  # call the url that sleeps between 1 and 1000 ms 60 times (about a minute)
  for i in {1..60} ; do
    curl -sk https://internal-CloudWatchValveTest-1206789691.us-west-2.elb.amazonaws.com
  done
  
  # check out the ASG
  CloudWatchValveTestASGStats=`aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names CloudWatchValveTest --query 'AutoScalingGroups[].{Instances:length(Instances),Desired:DesiredCapacity,Min:MinSize,Max:MaxSize}|[0]'`
  
  echo $CloudWatchValveTestASGStats
  
  eval `echo "$CloudWatchValveTestASGStats" | sed '/"/!d;s/ *"//g;s/,//;s/: /=/'`
  
  # break on max
  if [[ $Instances -eq $Max ]] ; then
    echo "hit max instances . . . time for a break"
    break
  fi
done

while true ; do
  # check out the ASG
  CloudWatchValveTestASGStats=`aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names CloudWatchValveTest --query 'AutoScalingGroups[].{Instances:length(Instances),Desired:DesiredCapacity,Min:MinSize,Max:MaxSize}|[0]'`
  
  echo $CloudWatchValveTestASGStats
  
  eval `echo "$CloudWatchValveTestASGStats" | sed '/"/!d;s/ *"//g;s/,//;s/: /=/'`

  sleep 300
  
  # break on max
  if [[ $Instances -eq $Min ]] ; then
    echo "looks like we're all calm now"
    break
  fi
done

echo `date` all done
