#!/bin/bash

set -eux

BUCKET=/s3v/test

if kubectl exec -it statefulset/ozone-om -- ozone sh bucket info "$BUCKET" >/dev/null 2>&1; then
  echo "Bucket already exists. Skipping."
else
  echo "Bucket does not exist. Creating..."
  kubectl exec -it statefulset/ozone-om -- ozone sh bucket create "$BUCKET"
fi

kubectl exec -it deployment/beeline -- beeline -u 'jdbc:hive2://hive:10000/' \
  -e 'create table if not exists test (id int, name string) stored by iceberg'
kubectl exec -it deployment/beeline -- beeline -u 'jdbc:hive2://hive:10000/' \
  -e "insert into test values (1, 'aaa'), (2, 'bbb')"
kubectl exec -it deployment/beeline -- beeline -u 'jdbc:hive2://hive:10000/' \
  -e 'select * from test'
