#!/bin/bash

set -eux

kubectl exec -it deployment/beeline -- beeline -u 'jdbc:hive2://hive:10000/' -e "$1"
