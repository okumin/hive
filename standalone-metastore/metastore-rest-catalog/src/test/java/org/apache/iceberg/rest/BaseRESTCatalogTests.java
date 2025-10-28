/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iceberg.rest;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.catalog.CatalogTests;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableCommit;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NotAuthorizedException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseRESTCatalogTests extends CatalogTests<RESTCatalog> {
  private RESTCatalog catalog;

  protected abstract Map<String, String> getDefaultClientConfiguration() throws Exception;

  protected abstract Optional<Map<String, String>> getPermissionTestClientConfiguration() throws Exception;

  @BeforeAll
  void setupAll() throws Exception {
    catalog = RCKUtils.initCatalogClient(getDefaultClientConfiguration());
    Assertions.assertEquals(Collections.singletonList(Namespace.of("default")), catalog.listNamespaces());
  }

  @BeforeEach
  void setup() {
    RCKUtils.purgeCatalogTestEntries(catalog);
  }

  @AfterAll
  void teardownAll() throws Exception {
    catalog.close();
  }

  @Override
  protected RESTCatalog catalog() {
    return catalog;
  }

  @Override
  protected RESTCatalog initCatalog(String catalogName, Map<String, String> additionalProperties) {
    try {
      Map<String, String> properties = new HashMap<>(getDefaultClientConfiguration());
      properties.putAll(additionalProperties);
      return RCKUtils.initCatalogClient(properties);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected boolean requiresNamespaceCreate() {
    return true;
  }

  @Override
  protected boolean supportsNamesWithSlashes() {
    return false;
  }

  @Override
  protected boolean supportsServerSideRetry() {
    return true;
  }

  private void testUnauthorizedAccess(Executable executable) {
    var error = Assertions.assertThrows(NotAuthorizedException.class, executable);
    Assertions.assertEquals("Not authorized: Unauthorized", error.getMessage());
  }

  @Test
  void testPermissionsWithDeniedUser() throws Exception {
    var properties = getPermissionTestClientConfiguration();
    if (properties.isEmpty()) {
      return;
    }
    var db = Namespace.of("permission_test_db");
    var table = TableIdentifier.of(db, "test_table");
    var view = TableIdentifier.of(db, "test_view");
    try (var catalog = RCKUtils.initCatalogClient(getDefaultClientConfiguration())) {
      catalog.createNamespace(db);
      catalog.createTable(table, new Schema());
      catalog.buildView(view).withQuery("hive", "SELECT count(*) FROM default.permission_test")
          .withSchema(new Schema()).withDefaultNamespace(db).create();
    } catch (IOException e) {
      throw new AssertionError("Catalog operation failed", e);
    }
    try (var catalog = RCKUtils.initCatalogClient(properties.get())) {
      // Should this fail?
      Assertions.assertTrue(catalog.listNamespaces().contains(db));
      testUnauthorizedAccess(() -> catalog.namespaceExists(db));
      testUnauthorizedAccess(() -> catalog.loadNamespaceMetadata(db));
      testUnauthorizedAccess(() -> catalog.createNamespace(Namespace.of("new-db")));
      testUnauthorizedAccess(() -> catalog.dropNamespace(db));

      // Should this fail?
      Assertions.assertEquals(Collections.singletonList(table), catalog.listTables(db));
      testUnauthorizedAccess(() -> catalog.tableExists(table));
      testUnauthorizedAccess(() -> catalog.loadTable(table));
      testUnauthorizedAccess(() -> catalog.createTable(TableIdentifier.of(db, "new-table"), new Schema()));
      testUnauthorizedAccess(() -> catalog.renameTable(table, TableIdentifier.of(db, "new-table")));
      testUnauthorizedAccess(() -> catalog.dropTable(table));

      // Should this fail?
      Assertions.assertEquals(Collections.singletonList(view), catalog.listViews(db));
      testUnauthorizedAccess(() -> catalog.viewExists(view));
      testUnauthorizedAccess(() -> catalog.loadView(view));
      testUnauthorizedAccess(() -> catalog.buildView(TableIdentifier.of(db, "new-view"))
          .withQuery("hive", "SELECT count(*) FROM default.permission_test").withSchema(new Schema())
          .withDefaultNamespace(db).create());
      testUnauthorizedAccess(() -> catalog.renameView(view, TableIdentifier.of(db, "new-view")));
      testUnauthorizedAccess(() -> catalog.dropView(view));

      testUnauthorizedAccess(() -> catalog.newCreateTableTransaction(TableIdentifier.of(db, "test"),
          new Schema()));
      testUnauthorizedAccess(() -> catalog.newReplaceTableTransaction(TableIdentifier.of(db, "test"),
          new Schema(), true));
      var dummyMetadata = TableMetadata.newTableMetadata(new Schema(), PartitionSpec.unpartitioned(),
          "dummy-location", Collections.emptyMap());
      testUnauthorizedAccess(() -> catalog.commitTransaction(TableCommit.create(table, dummyMetadata, dummyMetadata)));
    } catch (IOException e) {
      throw new AssertionError("Catalog operation failed", e);
    }
  }
}
