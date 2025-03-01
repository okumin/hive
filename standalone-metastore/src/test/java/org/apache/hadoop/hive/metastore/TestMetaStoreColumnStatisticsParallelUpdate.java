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

package org.apache.hadoop.hive.metastore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.metastore.annotation.MetastoreUnitTest;
import org.apache.hadoop.hive.metastore.api.ColumnStatistics;
import org.apache.hadoop.hive.metastore.api.ColumnStatisticsData;
import org.apache.hadoop.hive.metastore.api.ColumnStatisticsDesc;
import org.apache.hadoop.hive.metastore.api.ColumnStatisticsObj;
import org.apache.hadoop.hive.metastore.api.StringColumnStatsData;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.metastore.client.builder.DatabaseBuilder;
import org.apache.hadoop.hive.metastore.client.builder.PartitionBuilder;
import org.apache.hadoop.hive.metastore.client.builder.TableBuilder;
import org.apache.hadoop.hive.metastore.conf.MetastoreConf;
import org.apache.hadoop.hive.metastore.security.HadoopThriftAuthBridge;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Category(MetastoreUnitTest.class)
public class TestMetaStoreColumnStatisticsParallelUpdate {
  private static final Logger LOG = LoggerFactory.getLogger(TestMetaStoreColumnStatisticsParallelUpdate.class);
  private static final int POOL_SIZE = 30;
  private static final Executor EXECUTOR = Executors.newFixedThreadPool(POOL_SIZE);

  private Configuration conf;

  @Before
  public void setUp() throws Exception {
    conf = MetastoreConf.newMetastoreConf();
    MetastoreConf.setLongVar(conf, MetastoreConf.ConfVars.CONNECTION_POOLING_MAX_CONNECTIONS, POOL_SIZE);
    MetaStoreTestUtils.setConfForStandloneMode(conf);
    MetaStoreTestUtils.startMetaStoreWithRetry(HadoopThriftAuthBridge.getBridge(), conf);
  }

  private static int countNumSuccesses(List<CompletableFuture<Boolean>> results) {
    int num = 0;
    for (CompletableFuture<Boolean> result : results) {
      try {
        if (result.get()) {
          num += 1;
        }
      } catch (Exception e) {
        LOG.error("Failed to update stats", e);
      }
    }
    return num;
  }

  private static List<String> getAllColNames() {
    List<String> colNames = new ArrayList<>();
    for (int i = 0; i < 100; ++i) {
      colNames.add("col_" + i);
    }
    return colNames;
  }

  @Test(timeout = 60000)
  public void testTableColumnStatistics() throws Exception {
    String dbName = "_test_parallel_col_stats_update_";
    String tableName = "tbl";
    List<String> colNames = getAllColNames();
    try (HiveMetaStoreClient msc = new HiveMetaStoreClient(conf)) {
      new DatabaseBuilder().setName(dbName).create(msc, conf);
      TableBuilder builder = new TableBuilder().setDbName(dbName).setTableName(tableName);
      colNames.forEach(colName -> builder.addCol(colName, "string"));
      builder.create(msc, conf);
    }

    CyclicBarrier barrier = new CyclicBarrier(POOL_SIZE);
    List<CompletableFuture<Boolean>> results = new ArrayList<>();
    for (int i = 0; i < POOL_SIZE; i++) {
      CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(() -> {
        try (HiveMetaStoreClient client = new HiveMetaStoreClient(conf)) {
          List<ColumnStatisticsObj> stats = colNames.stream().map(colName -> {
            ColumnStatisticsData data = ColumnStatisticsData.stringStats(new StringColumnStatsData(100, 0.0, 0, 0));
            return new ColumnStatisticsObj(colName, "string", data);
          }).collect(Collectors.toList());
          ColumnStatisticsDesc desc = new ColumnStatisticsDesc(true, dbName, tableName);
          ColumnStatistics columnStatistics = new ColumnStatistics(desc, stats);
          barrier.await();
          return client.updateTableColumnStatistics(columnStatistics);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }, EXECUTOR);
      results.add(result);
    }

    int numSuccesses = countNumSuccesses(results);
    LOG.info("# of successes: {}", numSuccesses);

    try (HiveMetaStoreClient client = new HiveMetaStoreClient(conf)) {
      List<ColumnStatisticsObj> actual = client.getTableColumnStatistics(dbName, tableName, colNames);
      Map<String, Integer> numColStatsMap = new HashMap<>();
      actual.forEach(stats -> numColStatsMap.compute(stats.getColName(), (k, v) -> v == null ? 1 : v + 1));
      Assert.assertEquals(colNames.size(), numColStatsMap.size());
      LOG.info(numColStatsMap.toString());
      for (String colName : colNames) {
        Assert.assertTrue(colName + " is not found", numColStatsMap.containsKey(colName));
        int actualNum = Objects.requireNonNull(numColStatsMap.get(colName));
        Assert.assertEquals(String.format("%s has %d entries", colName, actualNum), 1, actualNum);
      }
    }

    Assert.assertEquals(1, numSuccesses);
  }

  @Test(timeout = 60000)
  public void testParallelPartitionColStatsUpdate() throws Exception {
    String dbName = "_test_parallel_col_stats_update_partitioned_";
    String tableName = "partitioned_tbl";
    String partitionValue = "2024-09-29";
    String partitionName = "dt=" + partitionValue;
    List<String> colNames = getAllColNames();
    try (HiveMetaStoreClient msc = new HiveMetaStoreClient(conf)) {
      new DatabaseBuilder().setName(dbName).create(msc, conf);
      TableBuilder builder = new TableBuilder().setDbName(dbName).setTableName(tableName).addPartCol("dt", "string");
      colNames.forEach(colName -> builder.addCol(colName, "string"));
      Table table = builder.create(msc, conf);
      new PartitionBuilder().inTable(table).addValue(partitionValue).addToTable(msc, conf);
    }

    CyclicBarrier barrier = new CyclicBarrier(POOL_SIZE);
    List<CompletableFuture<Boolean>> results = new ArrayList<>();
    for (int i = 0; i < POOL_SIZE; i++) {
      CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(() -> {
        try (HiveMetaStoreClient client = new HiveMetaStoreClient(conf)) {
          List<ColumnStatisticsObj> stats = colNames.stream().map(colName -> {
            ColumnStatisticsData data = ColumnStatisticsData.stringStats(new StringColumnStatsData(100, 0.0, 0, 0));
            return new ColumnStatisticsObj(colName, "string", data);
          }).collect(Collectors.toList());
          ColumnStatisticsDesc desc = new ColumnStatisticsDesc(false, dbName, tableName);
          desc.setPartName(partitionName);
          ColumnStatistics columnStatistics = new ColumnStatistics(desc, stats);
          barrier.await();
          return client.updatePartitionColumnStatistics(columnStatistics);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }, EXECUTOR);
      results.add(result);
    }

    int numSuccesses = countNumSuccesses(results);
    LOG.info("# of successes: {}", numSuccesses);

    try (HiveMetaStoreClient client = new HiveMetaStoreClient(conf)) {
      Map<String, List<ColumnStatisticsObj>> partitionStats = client.getPartitionColumnStatistics(dbName, tableName,
          Collections.singletonList(partitionName), colNames);
      Assert.assertEquals(1, partitionStats.size());
      Assert.assertTrue(partitionStats.containsKey(partitionName));

      List<ColumnStatisticsObj> actual = partitionStats.get(partitionName);
      Map<String, Integer> numColStatsMap = new HashMap<>();
      actual.forEach(stats -> numColStatsMap.compute(stats.getColName(), (k, v) -> v == null ? 1 : v + 1));
      Assert.assertEquals(colNames.size(), numColStatsMap.size());
      LOG.info(numColStatsMap.toString());
      for (String colName : colNames) {
        Assert.assertTrue(colName + " is not found", numColStatsMap.containsKey(colName));
        int actualNum = Objects.requireNonNull(numColStatsMap.get(colName));
        Assert.assertEquals(String.format("%s has %d entries", colName, actualNum), 1, actualNum);
      }
    }

    Assert.assertEquals(1, numSuccesses);
  }
}
