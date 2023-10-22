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

package org.apache.hadoop.hive.ql.plan;

import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFMaskHash;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorUtils;

public class HiveBucketFunctionV1 implements BucketFunction {
  private static final HiveBucketFunctionV1 INSTANCE = new HiveBucketFunctionV1();

  public static HiveBucketFunctionV1 get() {
    return INSTANCE;
  }

  @Override
  public int computeBucketNumber(Object[] bucketFields, ObjectInspector[] bucketFieldInspectors, int numBuckets) {
    return ObjectInspectorUtils.getBucketNumberOld(bucketFields, bucketFieldInspectors, numBuckets);
  }

  @Override
  public int computeHashCode(Object[] bucketFields, ObjectInspector[] bucketFieldInspectors) {
    return ObjectInspectorUtils.getBucketHashCodeOld(bucketFields, bucketFieldInspectors);
  }

  @Override
  public GenericUDF asUDF() {
    return new GenericUDFMaskHash();
  }

  @Override
  public String getBucketingType() {
    return "hive";
  }

  @Override
  public int getBucketingVersion() {
    return 1;
  }

  @Override
  public String toString() {
    return "HiveBucketFunctionV1";
  }
}
