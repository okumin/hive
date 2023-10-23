/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.iceberg.mr.hive;

import java.io.Serializable;
import org.apache.hadoop.hive.ql.plan.BucketFunction;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.IntObjectInspector;
import org.apache.iceberg.util.BucketUtil;

class IcebergBucketFunction implements BucketFunction, Serializable {
  private static final long serialVersionUID = 1L;

  private static final IcebergBucketFunction INSTANCE = new IcebergBucketFunction();

  static IcebergBucketFunction get() {
    return INSTANCE;
  }

  @Override
  public int getHashCode(Object[] bucketFields, ObjectInspector[] bucketFieldInspectors) {
    // TODO: null check, other types, multi fields, etc.
    IntObjectInspector oi = (IntObjectInspector) bucketFieldInspectors[0];
    int value = oi.get(bucketFields[0]);
    return BucketUtil.hash(value) & Integer.MAX_VALUE;
  }
}
