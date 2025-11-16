#!/bin/bash

set -eux

BUCKET=/s3v/test

if kubectl exec -it statefulset/ozone-om -- ozone sh bucket info "$BUCKET" >/dev/null 2>&1; then
  echo "Bucket already exists. Skipping."
else
  echo "Bucket does not exist. Creating..."
  kubectl exec -it statefulset/ozone-om -- ozone sh bucket create "$BUCKET"
fi

kubectl exec -it deployment/beeline -- curl http://hive-metastore:9001/iceberg/v1/config
kubectl exec -it deployment/beeline -- curl -X POST \
  http://hive-metastore:9001/iceberg/v1/namespaces/default/tables \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test_iceberg_rest",
    "schema": {
      "type": "struct",
      "fields": [
        {"id": 1, "name": "id", "type": "long", "required": true}
      ]
    },
    "write-disposition": "create"
  }'
kubectl exec -it deployment/beeline -- curl http://hive-metastore:9001/iceberg/v1/namespaces/default/tables/test_iceberg_rest
