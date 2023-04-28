/*
 * Copyright © 2023 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.plugin.common.stepsdesign;

import com.google.cloud.bigquery.BigQueryException;
import io.cdap.e2e.utils.BigQueryClient;
import io.cdap.e2e.utils.PluginPropertyUtils;
import io.cdap.plugin.PostgresqlClient;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import stepsdesign.BeforeActions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * POSTGRESQL test hooks.
 */
public class TestSetupHooks {

  @Before(order = 1)
  public static void setTableName() {
    String randomString = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    String sourceTableName = String.format("sourcetable_%s", randomString);
    String targetTableName = String.format("targettable_%s", randomString);
    PluginPropertyUtils.addPluginProp("sourceTable", sourceTableName);
    PluginPropertyUtils.addPluginProp("targetTable", targetTableName);
    String schema = PluginPropertyUtils.pluginProp("schema");
    PluginPropertyUtils.addPluginProp("selectQuery",
                                      String.format("select * from %s.%s", schema, sourceTableName));
  }

  @Before(order = 2, value = "@POSTGRESQL_SOURCE_TEST")
  public static void createSourceTable() throws SQLException, ClassNotFoundException {
    PostgresqlClient.createSourceTable(PluginPropertyUtils.pluginProp("sourceTable"),
                                       PluginPropertyUtils.pluginProp("schema"));
    BeforeActions.scenario.write("POSTGRESQL Source Table - " + PluginPropertyUtils.pluginProp("sourceTable")
                                   + " created successfully");
  }

  @After(order = 2, value = "@POSTGRESQL_SOURCE_TEST")
  public static void dropSourceTable() throws SQLException, ClassNotFoundException {
    PostgresqlClient.dropTable(PluginPropertyUtils.pluginProp("sourceTable"),
                               PluginPropertyUtils.pluginProp("schema"));
    BeforeActions.scenario.write("POSTGRESQL Source Table - " + PluginPropertyUtils.pluginProp("sourceTable")
                                   + " deleted successfully");
  }

  @Before(order = 2, value = "@POSTGRESQL_TARGET_TEST")
  public static void createTargetTable() throws SQLException, ClassNotFoundException {
    PostgresqlClient.createTargetTable(PluginPropertyUtils.pluginProp("targetTable"),
                                       PluginPropertyUtils.pluginProp("schema"));
    BeforeActions.scenario.write("POSTGRESQL Target Table - " + PluginPropertyUtils.pluginProp("targetTable")
                                   + " created successfully");
  }

  @After(order = 2, value = "@POSTGRESQL_TARGET_TEST")
  public static void dropTargetTable() throws SQLException, ClassNotFoundException {
    PostgresqlClient.dropTable(PluginPropertyUtils.pluginProp("targetTable"),
                               PluginPropertyUtils.pluginProp("schema"));
    BeforeActions.scenario.write("POSTGRESQL Target Table - " + PluginPropertyUtils.pluginProp("targetTable")
                                   + " deleted successfully");
  }

  @Before(order = 2, value = "@POSTGRESQL_TEST_TABLE")
  public static void createTargetPostgresqlTestTable() throws SQLException, ClassNotFoundException {
    PostgresqlClient.createTargetPostgresqlTable(PluginPropertyUtils.pluginProp("targetTable"),
                                                 PluginPropertyUtils.pluginProp("schema"));
    BeforeActions.scenario.write("POSTGRESQL Target Table - " + PluginPropertyUtils.pluginProp("targetTable")
                                   + " created successfully");
  }

  @After(order = 2, value = "@POSTGRESQL_TEST_TABLE")
  public static void dropTargetPostgresqlTestTable() throws SQLException, ClassNotFoundException {
    PostgresqlClient.dropTable(PluginPropertyUtils.pluginProp("targetTable"),
                               PluginPropertyUtils.pluginProp("schema"));
    BeforeActions.scenario.write("POSTGRESQL Target Table - " + PluginPropertyUtils.pluginProp("targetTable")
                                   + " deleted successfully");
  }

  @Before(order = 1, value = "@BQ_SINK_TEST")
  public static void setTempTargetBQTableName() {
    String bqTargetTableName = "E2E_TARGET_" + UUID.randomUUID().toString().replaceAll("-", "_");
    PluginPropertyUtils.addPluginProp("bqTargetTable", bqTargetTableName);
    BeforeActions.scenario.write("BQ Target table name - " + bqTargetTableName);
  }

  @After(order = 1, value = "@BQ_SINK_TEST")
  public static void deleteTempTargetBQTable() throws IOException, InterruptedException {
    String bqTargetTableName = PluginPropertyUtils.pluginProp("bqTargetTable");
    try {
      BigQueryClient.dropBqQuery(bqTargetTableName);
      BeforeActions.scenario.write("BQ Target table - " + bqTargetTableName + " deleted successfully");
      PluginPropertyUtils.removePluginProp("bqTargetTable");
    } catch (BigQueryException e) {
      if (e.getMessage().contains("Not found: Table")) {
        BeforeActions.scenario.write("BQ Target Table " + bqTargetTableName + " does not exist");
      } else {
        Assert.fail(e.getMessage());
      }
    }
  }

  /**
   * Create BigQuery table.
   */
  @Before(order = 1, value = "@BQ_SOURCE_TEST")
  public static void createTempSourceBQTable() throws IOException, InterruptedException {
    createSourceBQTableWithQueries(PluginPropertyUtils.pluginProp("CreateBQTableQueryFile"),
                                   PluginPropertyUtils.pluginProp("InsertBQDataQueryFile"));
  }

  @After(order = 1, value = "@BQ_SOURCE_TEST")
  public static void deleteTempSourceBQTable() throws IOException, InterruptedException {
    String bqSourceTable = PluginPropertyUtils.pluginProp("bqSourceTable");
    BigQueryClient.dropBqQuery(bqSourceTable);
    BeforeActions.scenario.write("BQ source Table " + bqSourceTable + " deleted successfully");
    PluginPropertyUtils.removePluginProp("bqSourceTable");
  }

  private static void createSourceBQTableWithQueries(String bqCreateTableQueryFile, String bqInsertDataQueryFile)
    throws IOException, InterruptedException {
    String bqSourceTable = "E2E_SOURCE_" + UUID.randomUUID().toString().substring(0, 5).replaceAll("-",
                                                                                                   "_");

    String createTableQuery = StringUtils.EMPTY;
    try {
      createTableQuery = new String(Files.readAllBytes(Paths.get(TestSetupHooks.class.getResource
        ("/" + bqCreateTableQueryFile).toURI()))
        , StandardCharsets.UTF_8);
      createTableQuery = createTableQuery.replace("DATASET", PluginPropertyUtils.pluginProp("dataset"))
        .replace("TABLE_NAME", bqSourceTable);
    } catch (Exception e) {
      BeforeActions.scenario.write("Exception in reading " + bqCreateTableQueryFile + " - " + e.getMessage());
      Assert.fail("Exception in BigQuery testdata prerequisite setup " +
                    "- error in reading create table query file " + e.getMessage());
    }

    String insertDataQuery = StringUtils.EMPTY;
    try {
      insertDataQuery = new String(Files.readAllBytes(Paths.get(TestSetupHooks.class.getResource
        ("/" + bqInsertDataQueryFile).toURI()))
        , StandardCharsets.UTF_8);
      insertDataQuery = insertDataQuery.replace("DATASET", PluginPropertyUtils.pluginProp("dataset"))
        .replace("TABLE_NAME", bqSourceTable);
    } catch (Exception e) {
      BeforeActions.scenario.write("Exception in reading " + bqInsertDataQueryFile + " - " + e.getMessage());
      Assert.fail("Exception in BigQuery testdata prerequisite setup " +
                    "- error in reading insert data query file " + e.getMessage());
    }
    BigQueryClient.getSoleQueryResult(createTableQuery);
    try {
      BigQueryClient.getSoleQueryResult(insertDataQuery);
    } catch (NoSuchElementException e) {
      // Insert query does not return any record.
      // Iterator on TableResult values in getSoleQueryResult method throws NoSuchElementException
    }
    PluginPropertyUtils.addPluginProp("bqSourceTable", bqSourceTable);
    BeforeActions.scenario.write("BQ Source Table " + bqSourceTable + " created successfully");
  }
}
