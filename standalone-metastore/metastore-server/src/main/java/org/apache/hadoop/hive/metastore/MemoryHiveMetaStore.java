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

import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.metastore.api.AlreadyExistsException;
import org.apache.hadoop.hive.metastore.api.AlterTableRequest;
import org.apache.hadoop.hive.metastore.api.AlterTableResponse;
import org.apache.hadoop.hive.metastore.api.CheckConstraintsRequest;
import org.apache.hadoop.hive.metastore.api.CheckConstraintsResponse;
import org.apache.hadoop.hive.metastore.api.CreateTableRequest;
import org.apache.hadoop.hive.metastore.api.Database;
import org.apache.hadoop.hive.metastore.api.GetAllFunctionsResponse;
import org.apache.hadoop.hive.metastore.api.GetDatabaseRequest;
import org.apache.hadoop.hive.metastore.api.GetTableRequest;
import org.apache.hadoop.hive.metastore.api.GetTableResult;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.NotNullConstraintsRequest;
import org.apache.hadoop.hive.metastore.api.NotNullConstraintsResponse;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.metastore.api.ThriftHiveMetastore.Iface;
import org.apache.hadoop.hive.metastore.api.WMGetActiveResourcePlanRequest;
import org.apache.hadoop.hive.metastore.api.WMGetActiveResourcePlanResponse;
import org.apache.hadoop.hive.metastore.utils.MetaStoreUtils;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemoryHiveMetaStore extends AbstractThriftHiveMetastore {
  private static final Logger LOG = LoggerFactory.getLogger(MemoryHiveMetaStore.class);

  private final Warehouse wh; // hdfs warehouse
  private final ConcurrentHashMap<String, Database> databases = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Pair<String, String>, Table> tables = new ConcurrentHashMap<>();

  static Iface newHMSHandler(Configuration conf) throws MetaException {
    return new MemoryHiveMetaStore(conf);
  }

  private MemoryHiveMetaStore(Configuration conf) throws MetaException {
    this.wh = new Warehouse(conf);
  }

  private static void validateCatalogName(String catalogName) {
    if (!catalogName.equals("hive")) {
      throw new UnsupportedOperationException("MemoryHiveMetastore supports only the `hive` catalog");
    }
  }

  @Override
  public void create_database(Database database) throws TException {
    validateCatalogName(database.getCatalogName());
    if (databases.putIfAbsent(database.getName(), database) != null) {
      throw new AlreadyExistsException(String.format("`%s` already exists", database.getName()));
    }
    LOG.info("Created " + database);
  }

  @Override
  public Database get_database_req(GetDatabaseRequest request) throws TException {
    validateCatalogName(request.getCatalogName());
    final Database database = databases.get(request.getName());
    if (database == null) {
      throw new NoSuchObjectException(String.format("Database `%s` doesn't exist", request.getName()));
    }
    return database;
  }

  @Override
  public void create_table_req(CreateTableRequest request) throws TException {
    validateCatalogName(request.getTable().getCatName());
    final Table table = request.getTable();
    final GetDatabaseRequest databaseRequest = new GetDatabaseRequest();
    databaseRequest.setCatalogName(table.getCatName());
    databaseRequest.setName(table.getDbName());
    final Database db = get_database_req(databaseRequest);
    final Path location = wh.getDefaultTablePath(db, table.getTableName(), MetaStoreUtils.isExternalTable(table));
    table.getSd().setLocation(location.toString());
    if (tables.putIfAbsent(Pair.of(table.getDbName(), table.getTableName()), table) != null) {
      throw new AlreadyExistsException(String.format("`%s.%s` already exists",
          table.getDbName(), table.getTableName()));
    }
    LOG.info("Created " + table);
  }

  @Override
  public AlterTableResponse alter_table_req(AlterTableRequest req) {
    tables.put(Pair.of(req.getDbName(), req.getTableName()), req.getTable());
    return new AlterTableResponse();
  }

  @Override
  public GetTableResult get_table_req(GetTableRequest req) throws TException {
    validateCatalogName(req.getCatName());
    final Table table = tables.get(Pair.of(req.getDbName(), req.getTblName()));
    if (table == null) {
      throw new NoSuchObjectException(String.format("Table `%s.%s` doesn't exist", req.getDbName(), req.getTblName()));
    }
    final GetTableResult result = new GetTableResult();
    result.setTable(table);
    return result;
  }

  @Override
  public GetAllFunctionsResponse get_all_functions() throws TException {
    return new GetAllFunctionsResponse();
  }

  @Override
  public WMGetActiveResourcePlanResponse get_active_resource_plan(WMGetActiveResourcePlanRequest request)
      throws TException {
    return new WMGetActiveResourcePlanResponse();
  }

  @Override
  public NotNullConstraintsResponse get_not_null_constraints(NotNullConstraintsRequest request) throws TException {
    return new NotNullConstraintsResponse();
  }

  @Override
  public CheckConstraintsResponse get_check_constraints(CheckConstraintsRequest request) throws TException {
    return new CheckConstraintsResponse();
  }

  @Override
  public void flushCache() throws TException {
  }

  @Override
  public void shutdown() {
    LOG.info("Shut down");
  }
}
