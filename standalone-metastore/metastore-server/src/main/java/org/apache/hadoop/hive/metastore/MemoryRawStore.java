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

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.common.TableName;
import org.apache.hadoop.hive.metastore.api.AddPackageRequest;
import org.apache.hadoop.hive.metastore.api.AggrStats;
import org.apache.hadoop.hive.metastore.api.AllTableConstraintsRequest;
import org.apache.hadoop.hive.metastore.api.AlreadyExistsException;
import org.apache.hadoop.hive.metastore.api.Catalog;
import org.apache.hadoop.hive.metastore.api.CheckConstraintsRequest;
import org.apache.hadoop.hive.metastore.api.ColumnStatistics;
import org.apache.hadoop.hive.metastore.api.CreationMetadata;
import org.apache.hadoop.hive.metastore.api.CurrentNotificationEventId;
import org.apache.hadoop.hive.metastore.api.DataConnector;
import org.apache.hadoop.hive.metastore.api.Database;
import org.apache.hadoop.hive.metastore.api.DefaultConstraintsRequest;
import org.apache.hadoop.hive.metastore.api.DropPackageRequest;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.FileMetadataExprType;
import org.apache.hadoop.hive.metastore.api.ForeignKeysRequest;
import org.apache.hadoop.hive.metastore.api.Function;
import org.apache.hadoop.hive.metastore.api.GetPackageRequest;
import org.apache.hadoop.hive.metastore.api.GetPartitionsFilterSpec;
import org.apache.hadoop.hive.metastore.api.GetProjectionsSpec;
import org.apache.hadoop.hive.metastore.api.GetReplicationMetricsRequest;
import org.apache.hadoop.hive.metastore.api.HiveObjectPrivilege;
import org.apache.hadoop.hive.metastore.api.HiveObjectRef;
import org.apache.hadoop.hive.metastore.api.ISchema;
import org.apache.hadoop.hive.metastore.api.ISchemaName;
import org.apache.hadoop.hive.metastore.api.InvalidInputException;
import org.apache.hadoop.hive.metastore.api.InvalidObjectException;
import org.apache.hadoop.hive.metastore.api.InvalidOperationException;
import org.apache.hadoop.hive.metastore.api.InvalidPartitionException;
import org.apache.hadoop.hive.metastore.api.ListPackageRequest;
import org.apache.hadoop.hive.metastore.api.ListStoredProcedureRequest;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.NotNullConstraintsRequest;
import org.apache.hadoop.hive.metastore.api.NotificationEvent;
import org.apache.hadoop.hive.metastore.api.NotificationEventRequest;
import org.apache.hadoop.hive.metastore.api.NotificationEventResponse;
import org.apache.hadoop.hive.metastore.api.NotificationEventsCountRequest;
import org.apache.hadoop.hive.metastore.api.NotificationEventsCountResponse;
import org.apache.hadoop.hive.metastore.api.Package;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.api.PartitionEventType;
import org.apache.hadoop.hive.metastore.api.PartitionValuesResponse;
import org.apache.hadoop.hive.metastore.api.PrimaryKeysRequest;
import org.apache.hadoop.hive.metastore.api.PrincipalPrivilegeSet;
import org.apache.hadoop.hive.metastore.api.PrincipalType;
import org.apache.hadoop.hive.metastore.api.PrivilegeBag;
import org.apache.hadoop.hive.metastore.api.ReplicationMetricList;
import org.apache.hadoop.hive.metastore.api.Role;
import org.apache.hadoop.hive.metastore.api.RolePrincipalGrant;
import org.apache.hadoop.hive.metastore.api.RuntimeStat;
import org.apache.hadoop.hive.metastore.api.SQLAllTableConstraints;
import org.apache.hadoop.hive.metastore.api.SQLCheckConstraint;
import org.apache.hadoop.hive.metastore.api.SQLDefaultConstraint;
import org.apache.hadoop.hive.metastore.api.SQLForeignKey;
import org.apache.hadoop.hive.metastore.api.SQLNotNullConstraint;
import org.apache.hadoop.hive.metastore.api.SQLPrimaryKey;
import org.apache.hadoop.hive.metastore.api.SQLUniqueConstraint;
import org.apache.hadoop.hive.metastore.api.ScheduledQuery;
import org.apache.hadoop.hive.metastore.api.ScheduledQueryKey;
import org.apache.hadoop.hive.metastore.api.ScheduledQueryMaintenanceRequest;
import org.apache.hadoop.hive.metastore.api.ScheduledQueryPollRequest;
import org.apache.hadoop.hive.metastore.api.ScheduledQueryPollResponse;
import org.apache.hadoop.hive.metastore.api.ScheduledQueryProgressInfo;
import org.apache.hadoop.hive.metastore.api.SchemaVersion;
import org.apache.hadoop.hive.metastore.api.SchemaVersionDescriptor;
import org.apache.hadoop.hive.metastore.api.SerDeInfo;
import org.apache.hadoop.hive.metastore.api.StoredProcedure;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.metastore.api.TableMeta;
import org.apache.hadoop.hive.metastore.api.Type;
import org.apache.hadoop.hive.metastore.api.UniqueConstraintsRequest;
import org.apache.hadoop.hive.metastore.api.UnknownDBException;
import org.apache.hadoop.hive.metastore.api.UnknownPartitionException;
import org.apache.hadoop.hive.metastore.api.UnknownTableException;
import org.apache.hadoop.hive.metastore.api.WMFullResourcePlan;
import org.apache.hadoop.hive.metastore.api.WMMapping;
import org.apache.hadoop.hive.metastore.api.WMNullablePool;
import org.apache.hadoop.hive.metastore.api.WMNullableResourcePlan;
import org.apache.hadoop.hive.metastore.api.WMPool;
import org.apache.hadoop.hive.metastore.api.WMResourcePlan;
import org.apache.hadoop.hive.metastore.api.WMTrigger;
import org.apache.hadoop.hive.metastore.api.WMValidateResourcePlanResponse;
import org.apache.hadoop.hive.metastore.api.WriteEventInfo;
import org.apache.hadoop.hive.metastore.client.builder.GetPartitionsArgs;
import org.apache.hadoop.hive.metastore.model.MTable;
import org.apache.hadoop.hive.metastore.partition.spec.PartitionSpecProxy;
import org.apache.hadoop.hive.metastore.utils.MetaStoreServerUtils.ColStatsObjWithSourceInfo;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * I am feeling RawStore is too primitive to implement a custom metastore. Every method is required to be
 * composable in a transaction. It is not trivial in case that the backend is not an RDBMS...
 */
public class MemoryRawStore implements RawStore {
  private static final Logger LOG = LoggerFactory.getLogger(MemoryRawStore.class);

  private static final Catalog catalog = new Catalog("hive", "hdfs:///user/hive/warehouse");
  private static final ConcurrentHashMap<Pair<String, String>, Database> databases = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<Triple<String, String, String>, Table> tables = new ConcurrentHashMap<>();

  private boolean isActive = false;
  private Configuration conf;

  @Override
  public void shutdown() {
  }

  @Override
  public boolean openTransaction() {
    isActive = true;
    return true;
  }

  @Override
  public boolean commitTransaction() {
    isActive = false;
    return true;
  }

  @Override
  public boolean isActiveTransaction() {
    return isActive;
  }

  @Override
  public Catalog getCatalog(String catalogName) throws NoSuchObjectException, MetaException {
    if (!catalogName.equals("hive")) {
      throw new NoSuchObjectException(String.format("Catalog %s doesn't exist", catalogName));
    }
    return catalog;
  }

  @Override
  public void createDatabase(Database db) throws InvalidObjectException, MetaException {
    databases.put(Pair.of(db.getCatalogName(), db.getName()), db);
  }

  @Override
  public Database getDatabase(String catalogName, String name) throws NoSuchObjectException {
    final Database database = databases.get(Pair.of(catalogName, name));
    if (database == null) {
      throw new NoSuchObjectException(String.format("Database %s.%s is not found", catalogName, name));
    }
    return database;
  }

  @Override
  public void createTable(Table tbl) throws InvalidObjectException, MetaException {
    tables.put(Triple.of(tbl.getCatName(), tbl.getDbName(), tbl.getTableName()), tbl);
  }

  @Override
  public Table alterTable(String catName, String dbname, String name, Table newTable, String queryValidWriteIds)
      throws InvalidObjectException, MetaException {
    tables.put(Triple.of(catName, dbname, name), newTable);
    return newTable;
  }

  @Override
  public Table getTable(String catalogName, String dbName, String tableName, String writeIdList) throws MetaException {
    return tables.get(Triple.of(catalogName, dbName, tableName));
  }

  @Override
  public Table getTable(String catalogName, String dbName, String tableName, String writeIdList, long tableId)
      throws MetaException {
    return tables.get(Triple.of(catalogName, dbName, tableName));
  }

  @Override
  public List<ColumnStatistics> getTableColumnStatistics(String catName, String dbName, String tableName,
      List<String> colName) throws MetaException, NoSuchObjectException {
    return Collections.emptyList();
  }

  @Override
  public List<Table> getAllMaterializedViewObjectsForRewriting(String catName) throws MetaException {
    return Collections.emptyList();
  }

  @Override
  public boolean addRole(String rowName, String ownerName)
      throws InvalidObjectException, MetaException, NoSuchObjectException {
    LOG.info("Adding a new role {} owned by {}", rowName, ownerName);
    // TODO
    return true;
  }

  @Override
  public boolean grantPrivileges(PrivilegeBag privileges)
      throws InvalidObjectException, MetaException, NoSuchObjectException {
    LOG.info("Adding new privileges {}", privileges);
    // TODO
    return true;
  }

  @Override
  public List<Function> getAllFunctions(String catName) throws MetaException {
    return Collections.emptyList();
  }

  @Override
  public WMFullResourcePlan getActiveResourcePlan(String ns) throws MetaException {
    return null;
  }

  @Override
  public List<SQLNotNullConstraint> getNotNullConstraints(NotNullConstraintsRequest request) throws MetaException {
    return Collections.emptyList();
  }

  @Override
  public List<SQLCheckConstraint> getCheckConstraints(CheckConstraintsRequest request) throws MetaException {
    return Collections.emptyList();
  }

  @Override
  public void verifySchema() throws MetaException {
  }

  @Override
  public void flushCache() {
  }

  @Override
  public ScheduledQueryPollResponse scheduledQueryPoll(ScheduledQueryPollRequest request) throws MetaException {
    return new ScheduledQueryPollResponse();
  }

  @Override
  public void setConf(Configuration configuration) {
    conf = configuration;
  }

  @Override
  public Configuration getConf() {
    return conf;
  }

  //////////////////////////// Unsupported //////////////////////////////////////
  @Override
  public void rollbackTransaction() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createCatalog(Catalog cat) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void alterCatalog(String catName, Catalog cat) throws MetaException, InvalidOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> getCatalogs() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dropCatalog(String catalogName) throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean dropDatabase(String catalogName, String dbname) throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean alterDatabase(String catalogName, String dbname, Database db)
      throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> getDatabases(String catalogName, String pattern) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> getAllDatabases(String catalogName) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createDataConnector(DataConnector dataConnector) throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean dropDataConnector(String dcName) throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean alterDataConnector(String dcName, DataConnector connector)
      throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public DataConnector getDataConnector(String dcName) throws NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> getAllDataConnectorNames() throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean createType(Type type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Type getType(String typeName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean dropType(String typeName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean dropTable(String catalogName, String dbName, String tableName)
      throws MetaException, NoSuchObjectException, InvalidObjectException, InvalidInputException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Table getTable(String catalogName, String dbName, String tableName) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addPartition(Partition part) throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addPartitions(String catName, String dbName, String tblName, List<Partition> parts)
      throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addPartitions(String catName, String dbName, String tblName, PartitionSpecProxy partitionSpec,
      boolean ifNotExists) throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Partition getPartition(String catName, String dbName, String tableName, List<String> part_vals)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Partition getPartition(String catName, String dbName, String tableName, List<String> part_vals,
      String writeIdList) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean doesPartitionExist(String catName, String dbName, String tableName, List<FieldSchema> partKeys,
      List<String> part_vals) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean dropPartition(String catName, String dbName, String tableName, List<String> part_vals)
      throws MetaException, NoSuchObjectException, InvalidObjectException, InvalidInputException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean dropPartition(String catName, String dbName, String tableName, String partName)
      throws MetaException, NoSuchObjectException, InvalidObjectException, InvalidInputException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Partition> getPartitions(String catName, String dbName, String tableName, GetPartitionsArgs args)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, String> getPartitionLocations(String catName, String dbName, String tblName,
      String baseLocationToNotShow, int max) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateCreationMetadata(String catName, String dbname, String tablename, CreationMetadata cm)
      throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> getTables(String catName, String dbName, String pattern) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> getTables(String catName, String dbName, String pattern, TableType tableType, int limit)
      throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> getMaterializedViewsForRewriting(String catName, String dbName)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<TableMeta> getTableMeta(String catName, String dbNames, String tableNames, List<String> tableTypes)
      throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Table> getTableObjectsByName(String catName, String dbname, List<String> tableNames)
      throws MetaException, UnknownDBException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Table> getTableObjectsByName(String catName, String dbname, List<String> tableNames,
      GetProjectionsSpec projectionSpec, String tablePattern) throws MetaException, UnknownDBException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> getAllTables(String catName, String dbName) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> listTableNamesByFilter(String catName, String dbName, String filter, short max_tables)
      throws MetaException, UnknownDBException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> listPartitionNames(String catName, String db_name, String tbl_name, short max_parts)
      throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> listPartitionNames(String catName, String dbName, String tblName, String defaultPartName,
      byte[] exprBytes, String order, short maxParts) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PartitionValuesResponse listPartitionValues(String catName, String db_name, String tbl_name,
      List<FieldSchema> cols, boolean applyDistinct, String filter, boolean ascending, List<FieldSchema> order,
      long maxParts) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Partition alterPartition(String catName, String db_name, String tbl_name, List<String> part_vals,
      Partition new_part, String queryValidWriteIds) throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Partition> alterPartitions(String catName, String db_name, String tbl_name,
      List<List<String>> part_vals_list, List<Partition> new_parts, long writeId, String queryValidWriteIds)
      throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Partition> getPartitionsByFilter(String catName, String dbName, String tblName, GetPartitionsArgs args)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Partition> getPartitionSpecsByFilterAndProjection(Table table, GetProjectionsSpec projectionSpec,
      GetPartitionsFilterSpec filterSpec) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean getPartitionsByExpr(String catName, String dbName, String tblName, List<Partition> result,
      GetPartitionsArgs args) throws TException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getNumPartitionsByFilter(String catName, String dbName, String tblName, String filter)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getNumPartitionsByExpr(String catName, String dbName, String tblName, byte[] expr)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getNumPartitionsByPs(String catName, String dbName, String tblName, List<String> partVals)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Partition> getPartitionsByNames(String catName, String dbName, String tblName, GetPartitionsArgs args)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Table markPartitionForEvent(String catName, String dbName, String tblName, Map<String, String> partVals,
      PartitionEventType evtType)
      throws MetaException, UnknownTableException, InvalidPartitionException, UnknownPartitionException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isPartitionMarkedForEvent(String catName, String dbName, String tblName, Map<String, String> partName,
      PartitionEventType evtType)
      throws MetaException, UnknownTableException, InvalidPartitionException, UnknownPartitionException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeRole(String roleName) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean grantRole(Role role, String userName, PrincipalType principalType, String grantor,
      PrincipalType grantorType, boolean grantOption)
      throws MetaException, NoSuchObjectException, InvalidObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean revokeRole(Role role, String userName, PrincipalType principalType, boolean grantOption)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrincipalPrivilegeSet getUserPrivilegeSet(String userName, List<String> groupNames)
      throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrincipalPrivilegeSet getDBPrivilegeSet(String catName, String dbName, String userName,
      List<String> groupNames) throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrincipalPrivilegeSet getConnectorPrivilegeSet(String catName, String connectorName, String userName,
      List<String> groupNames) throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrincipalPrivilegeSet getTablePrivilegeSet(String catName, String dbName, String tableName, String userName,
      List<String> groupNames) throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrincipalPrivilegeSet getPartitionPrivilegeSet(String catName, String dbName, String tableName,
      String partition, String userName, List<String> groupNames) throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrincipalPrivilegeSet getColumnPrivilegeSet(String catName, String dbName, String tableName,
      String partitionName, String columnName, String userName, List<String> groupNames)
      throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPrincipalGlobalGrants(String principalName, PrincipalType principalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPrincipalDBGrants(String principalName, PrincipalType principalType,
      String catName, String dbName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPrincipalDCGrants(String principalName, PrincipalType principalType,
      String dcName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listAllTableGrants(String principalName, PrincipalType principalType, String catName,
      String dbName, String tableName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPrincipalPartitionGrants(String principalName, PrincipalType principalType,
      String catName, String dbName, String tableName, List<String> partValues, String partName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPrincipalTableColumnGrants(String principalName, PrincipalType principalType,
      String catName, String dbName, String tableName, String columnName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPrincipalPartitionColumnGrants(String principalName, PrincipalType principalType,
      String catName, String dbName, String tableName, List<String> partValues, String partName, String columnName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean revokePrivileges(PrivilegeBag privileges, boolean grantOption)
      throws InvalidObjectException, MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean refreshPrivileges(HiveObjectRef objToRefresh, String authorizer, PrivilegeBag grantPrivileges)
      throws InvalidObjectException, MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Role getRole(String roleName) throws NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> listRoleNames() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Role> listRoles(String principalName, PrincipalType principalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<RolePrincipalGrant> listRolesWithGrants(String principalName, PrincipalType principalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<RolePrincipalGrant> listRoleMembers(String roleName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Partition getPartitionWithAuth(String catName, String dbName, String tblName, List<String> partVals,
      String user_name, List<String> group_names) throws MetaException, NoSuchObjectException, InvalidObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> listPartitionNamesPs(String catName, String db_name, String tbl_name, List<String> part_vals,
      short max_parts) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Partition> listPartitionsPsWithAuth(String catName, String db_name, String tbl_name,
      GetPartitionsArgs args) throws MetaException, InvalidObjectException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, String> updateTableColumnStatistics(ColumnStatistics colStats, String validWriteIds, long writeId)
      throws NoSuchObjectException, MetaException, InvalidObjectException, InvalidInputException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, String> updatePartitionColumnStatistics(ColumnStatistics statsObj, List<String> partVals,
      String validWriteIds, long writeId)
      throws NoSuchObjectException, MetaException, InvalidObjectException, InvalidInputException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, String> updatePartitionColumnStatistics(Table table, MTable mTable, ColumnStatistics statsObj,
      List<String> partVals, String validWriteIds, long writeId)
      throws NoSuchObjectException, MetaException, InvalidObjectException, InvalidInputException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ColumnStatistics getTableColumnStatistics(String catName, String dbName, String tableName,
      List<String> colName, String engine) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ColumnStatistics getTableColumnStatistics(String catName, String dbName, String tableName,
      List<String> colName, String engine, String writeIdList) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<List<ColumnStatistics>> getPartitionColumnStatistics(String catName, String dbName, String tblName,
      List<String> partNames, List<String> colNames) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<ColumnStatistics> getPartitionColumnStatistics(String catName, String dbName, String tblName,
      List<String> partNames, List<String> colNames, String engine) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<ColumnStatistics> getPartitionColumnStatistics(String catName, String dbName, String tblName,
      List<String> partNames, List<String> colNames, String engine, String writeIdList)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean deletePartitionColumnStatistics(String catName, String dbName, String tableName, String partName,
      List<String> partVals, String colName, String engine)
      throws NoSuchObjectException, MetaException, InvalidObjectException, InvalidInputException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean deleteTableColumnStatistics(String catName, String dbName, String tableName, String colName,
      String engine) throws NoSuchObjectException, MetaException, InvalidObjectException, InvalidInputException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long cleanupEvents() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addToken(String tokenIdentifier, String delegationToken) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeToken(String tokenIdentifier) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getToken(String tokenIdentifier) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> getAllTokenIdentifiers() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int addMasterKey(String key) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateMasterKey(Integer seqNo, String key) throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeMasterKey(Integer keySeq) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String[] getMasterKeys() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getMetaStoreSchemaVersion() throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMetaStoreSchemaVersion(String version, String comment) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dropPartitions(String catName, String dbName, String tblName, List<String> partNames)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPrincipalDBGrantsAll(String principalName, PrincipalType principalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPrincipalDCGrantsAll(String principalName, PrincipalType principalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPrincipalTableGrantsAll(String principalName, PrincipalType principalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPrincipalPartitionGrantsAll(String principalName, PrincipalType principalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPrincipalTableColumnGrantsAll(String principalName,
      PrincipalType principalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPrincipalPartitionColumnGrantsAll(String principalName,
      PrincipalType principalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listGlobalGrantsAll() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listDBGrantsAll(String catName, String dbName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listDCGrantsAll(String dcName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPartitionColumnGrantsAll(String catName, String dbName, String tableName,
      String partitionName, String columnName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listTableGrantsAll(String catName, String dbName, String tableName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listPartitionGrantsAll(String catName, String dbName, String tableName,
      String partitionName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HiveObjectPrivilege> listTableColumnGrantsAll(String catName, String dbName, String tableName,
      String columnName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createFunction(Function func) throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void alterFunction(String catName, String dbName, String funcName, Function newFunction)
      throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dropFunction(String catName, String dbName, String funcName)
      throws MetaException, NoSuchObjectException, InvalidObjectException, InvalidInputException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Function getFunction(String catName, String dbName, String funcName) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> getFunctions(String catName, String dbName, String pattern) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public AggrStats get_aggr_stats_for(String catName, String dbName, String tblName, List<String> partNames,
      List<String> colNames, String engine) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public AggrStats get_aggr_stats_for(String catName, String dbName, String tblName, List<String> partNames,
      List<String> colNames, String engine, String writeIdList) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<ColStatsObjWithSourceInfo> getPartitionColStatsForDatabase(String catName, String dbName)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public NotificationEventResponse getNextNotification(NotificationEventRequest rqst) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addNotificationEvent(NotificationEvent event) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void cleanNotificationEvents(int olderThan) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CurrentNotificationEventId getCurrentNotificationEventId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public NotificationEventsCountResponse getNotificationEventsCount(NotificationEventsCountRequest rqst) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ByteBuffer[] getFileMetadata(List<Long> fileIds) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putFileMetadata(List<Long> fileIds, List<ByteBuffer> metadata, FileMetadataExprType type)
      throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isFileMetadataSupported() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void getFileMetadataByExpr(List<Long> fileIds, FileMetadataExprType type, byte[] expr, ByteBuffer[] metadatas,
      ByteBuffer[] exprResults, boolean[] eliminated) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileMetadataHandler getFileMetadataHandler(FileMetadataExprType type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getTableCount() throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getPartitionCount() throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getDatabaseCount() throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLPrimaryKey> getPrimaryKeys(String catName, String db_name, String tbl_name) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLPrimaryKey> getPrimaryKeys(PrimaryKeysRequest request) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLForeignKey> getForeignKeys(String catName, String parent_db_name, String parent_tbl_name,
      String foreign_db_name, String foreign_tbl_name) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLForeignKey> getForeignKeys(ForeignKeysRequest request) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLUniqueConstraint> getUniqueConstraints(String catName, String db_name, String tbl_name)
      throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLUniqueConstraint> getUniqueConstraints(UniqueConstraintsRequest request) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLNotNullConstraint> getNotNullConstraints(String catName, String db_name, String tbl_name)
      throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLDefaultConstraint> getDefaultConstraints(String catName, String db_name, String tbl_name)
      throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLDefaultConstraint> getDefaultConstraints(DefaultConstraintsRequest request) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLCheckConstraint> getCheckConstraints(String catName, String db_name, String tbl_name)
      throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public SQLAllTableConstraints getAllTableConstraints(String catName, String dbName, String tblName)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public SQLAllTableConstraints getAllTableConstraints(AllTableConstraintsRequest request)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public SQLAllTableConstraints createTableWithConstraints(Table tbl, SQLAllTableConstraints constraints)
      throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dropConstraint(String catName, String dbName, String tableName, String constraintName, boolean missingOk)
      throws NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLPrimaryKey> addPrimaryKeys(List<SQLPrimaryKey> pks) throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLForeignKey> addForeignKeys(List<SQLForeignKey> fks) throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLUniqueConstraint> addUniqueConstraints(List<SQLUniqueConstraint> uks)
      throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLNotNullConstraint> addNotNullConstraints(List<SQLNotNullConstraint> nns)
      throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLDefaultConstraint> addDefaultConstraints(List<SQLDefaultConstraint> dv)
      throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SQLCheckConstraint> addCheckConstraints(List<SQLCheckConstraint> cc)
      throws InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getMetastoreDbUuid() throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createResourcePlan(WMResourcePlan resourcePlan, String copyFrom, int defaultPoolSize)
      throws AlreadyExistsException, MetaException, InvalidObjectException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public WMFullResourcePlan getResourcePlan(String name, String string) throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<WMResourcePlan> getAllResourcePlans(String string) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public WMFullResourcePlan alterResourcePlan(String name, String ns, WMNullableResourcePlan resourcePlan,
      boolean canActivateDisabled, boolean canDeactivate, boolean isReplace)
      throws AlreadyExistsException, NoSuchObjectException, InvalidOperationException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public WMValidateResourcePlanResponse validateResourcePlan(String name, String ns)
      throws NoSuchObjectException, InvalidObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dropResourcePlan(String name, String ns) throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createWMTrigger(WMTrigger trigger)
      throws AlreadyExistsException, NoSuchObjectException, InvalidOperationException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void alterWMTrigger(WMTrigger trigger) throws NoSuchObjectException, InvalidOperationException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dropWMTrigger(String resourcePlanName, String triggerName, String ns)
      throws NoSuchObjectException, InvalidOperationException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<WMTrigger> getTriggersForResourcePlan(String resourcePlanName, String ns)
      throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createPool(WMPool pool)
      throws AlreadyExistsException, NoSuchObjectException, InvalidOperationException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void alterPool(WMNullablePool pool, String poolPath)
      throws AlreadyExistsException, NoSuchObjectException, InvalidOperationException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dropWMPool(String resourcePlanName, String poolPath, String ns)
      throws NoSuchObjectException, InvalidOperationException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createOrUpdateWMMapping(WMMapping mapping, boolean update)
      throws AlreadyExistsException, NoSuchObjectException, InvalidOperationException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dropWMMapping(WMMapping mapping) throws NoSuchObjectException, InvalidOperationException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createWMTriggerToPoolMapping(String resourcePlanName, String triggerName, String poolPath, String ns)
      throws AlreadyExistsException, NoSuchObjectException, InvalidOperationException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dropWMTriggerToPoolMapping(String resourcePlanName, String triggerName, String poolPath, String ns)
      throws NoSuchObjectException, InvalidOperationException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createISchema(ISchema schema) throws AlreadyExistsException, MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void alterISchema(ISchemaName schemaName, ISchema newSchema) throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ISchema getISchema(ISchemaName schemaName) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dropISchema(ISchemaName schemaName) throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addSchemaVersion(SchemaVersion schemaVersion)
      throws AlreadyExistsException, InvalidObjectException, NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void alterSchemaVersion(SchemaVersionDescriptor version, SchemaVersion newVersion)
      throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public SchemaVersion getSchemaVersion(SchemaVersionDescriptor version) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public SchemaVersion getLatestSchemaVersion(ISchemaName schemaName) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SchemaVersion> getAllSchemaVersion(ISchemaName schemaName) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SchemaVersion> getSchemaVersionsByColumns(String colName, String colNamespace, String type)
      throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dropSchemaVersion(SchemaVersionDescriptor version) throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public SerDeInfo getSerDeInfo(String serDeName) throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addSerde(SerDeInfo serde) throws AlreadyExistsException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addRuntimeStat(RuntimeStat stat) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<RuntimeStat> getRuntimeStats(int maxEntries, int maxCreateTime) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int deleteRuntimeStats(int maxRetainSecs) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<TableName> getTableNamesWithStats() throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<TableName> getAllTableNamesForStats() throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, List<String>> getPartitionColsWithStats(String catName, String dbName, String tableName)
      throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void cleanWriteNotificationEvents(int olderThan) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<WriteEventInfo> getAllWriteEventInfo(long txnId, String dbName, String tableName) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> isPartOfMaterializedView(String catName, String dbName, String tblName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ScheduledQuery getScheduledQuery(ScheduledQueryKey scheduleKey) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void scheduledQueryMaintenance(ScheduledQueryMaintenanceRequest request)
      throws MetaException, NoSuchObjectException, AlreadyExistsException, InvalidInputException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void scheduledQueryProgress(ScheduledQueryProgressInfo info)
      throws MetaException, NoSuchObjectException, InvalidOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addReplicationMetrics(ReplicationMetricList replicationMetricList) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ReplicationMetricList getReplicationMetrics(GetReplicationMetricsRequest replicationMetricsRequest) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, Map<String, String>> updatePartitionColumnStatisticsInBatch(
      Map<String, ColumnStatistics> partColStatsMap, Table tbl, List<TransactionalMetaStoreEventListener> listeners,
      String validWriteIds, long writeId)
      throws NoSuchObjectException, MetaException, InvalidObjectException, InvalidInputException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int deleteReplicationMetrics(int maxRetainSecs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int deleteScheduledExecutions(int maxRetainSecs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int markScheduledExecutionsTimedOut(int timeoutSecs) throws InvalidOperationException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteAllPartitionColumnStatistics(TableName tn, String writeIdList) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createOrUpdateStoredProcedure(StoredProcedure proc) throws NoSuchObjectException, MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public StoredProcedure getStoredProcedure(String catName, String db, String name) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dropStoredProcedure(String catName, String dbName, String funcName) throws MetaException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> getAllStoredProcedures(ListStoredProcedureRequest request) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addPackage(AddPackageRequest request) throws MetaException, NoSuchObjectException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Package findPackage(GetPackageRequest request) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> listPackages(ListPackageRequest request) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dropPackage(DropPackageRequest request) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MTable ensureGetMTable(String catName, String dbName, String tblName) throws NoSuchObjectException {
    throw new UnsupportedOperationException();
  }
}
