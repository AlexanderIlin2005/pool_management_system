#!/bin/bash
PID=$(ps aux | grep 'pool-management-1.0-SNAPSHOT.jar' | grep -v grep | awk '{print $2}')
if [ -z "$PID" ]; then
    echo "Process not running"
else
    kill -9 $PID
    echo "Process $PID killed"
fi