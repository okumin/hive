#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -eux

BUCKET=/s3v/test

helm install ozone ozone/ozone --version 0.2.0 --wait
# Wait for a while because Ozone's Helm chart does not have readiness probes...
sleep 10
if kubectl exec -it statefulset/ozone-om -- ozone sh bucket info "$BUCKET" >/dev/null 2>&1; then
  echo "Bucket already exists. Skipping."
else
  echo "Bucket does not exist. Creating..."
  kubectl exec -it statefulset/ozone-om -- ozone sh bucket create "$BUCKET"
fi

base_dir=$(dirname "$(cd "$(dirname "$0")" || exit; pwd)")

kubectl apply -f "$base_dir/k8s/*"

kubectl rollout status deployment/hive-metastore
kubectl rollout status deployment/hive
kubectl rollout status deployment/beeline

mkdir -p "$base_dir/target"
kubectl port-forward service/hive-metastore 9083 &
echo $! > "$base_dir/target/hive-metastore-thrift.pid"
kubectl port-forward service/hive-metastore 9001 &
echo $! > "$base_dir/target/hive-metastore-rest.pid"
kubectl port-forward service/ozone-s3g-rest 9878 &
echo $! > "$base_dir/target/ozone-s3g.pid"
