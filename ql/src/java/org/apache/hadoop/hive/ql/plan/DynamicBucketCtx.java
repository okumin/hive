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

import java.io.Serializable;
import java.util.List;

public class DynamicBucketCtx implements Serializable {
  private static final long serialVersionUID = 1L;

  private final List<String> bucketCols;
  private final List<String> sortCols;
  private final int numBuckets;
  private final BucketFunction bucketFunction;


  public DynamicBucketCtx(List<String> bucketCols, List<String> sortCols, int numBuckets,
      BucketFunction bucketFunction) {
    this.bucketCols = bucketCols;
    this.sortCols = sortCols;
    this.numBuckets = numBuckets;
    this.bucketFunction = bucketFunction;
  }

  public List<String> getBucketCols() {
    return bucketCols;
  }

  public List<String> getSortCols() {
    return sortCols;
  }

  public int getNumBuckets() {
    return numBuckets;
  }

  public BucketFunction getBucketFunction() {
    return bucketFunction;
  }
}
