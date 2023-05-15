package io.cdap.plugin.oracle;

import com.google.cloud.bigquery.TableResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.cdap.e2e.utils.BigQueryClient;
import io.cdap.e2e.utils.PluginPropertyUtils;
import io.cdap.plugin.OracleClient;
import org.junit.Assert;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

public class BQValidation {

  public static boolean validateBQAndDBRecordValues(String schema, String sourceTable, String targetTable)
    throws SQLException, ClassNotFoundException, ParseException, IOException, InterruptedException {
    List<JsonObject> jsonObject = new ArrayList<>();
    List<Object> list = new ArrayList<>();
    getBigQueryTableData(targetTable, list);
    for (Object jsonRow : list) {
      JsonObject json = new Gson().fromJson(String.valueOf(jsonRow), JsonObject.class);
      jsonObject.add(json);
    }
    String getSourceQuery = "SELECT * FROM " + schema + "." + sourceTable;
    try (Connection connect = OracleClient.getOracleConnection()) {
      connect.setHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT);
      Statement statement1 = connect.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE,
                                                     ResultSet.HOLD_CURSORS_OVER_COMMIT);

      ResultSet rsSource = statement1.executeQuery(getSourceQuery);
      return compareResultSetData(rsSource, jsonObject);
    }
  }


  private static void getBigQueryTableData(String table, List<Object> bigQueryRows)
    throws IOException, InterruptedException {

    String projectId = PluginPropertyUtils.pluginProp("projectId");
    String dataset = PluginPropertyUtils.pluginProp("dataset");
    String selectQuery = "SELECT TO_JSON(t) FROM `" + projectId + "." + dataset + "." + table + "` AS t";
    TableResult result = BigQueryClient.getQueryResult(selectQuery);
    result.iterateAll().forEach(value -> bigQueryRows.add(value.get(0).getValue()));
  }

  public static boolean compareResultSetData(ResultSet rsSource, List<JsonObject> bigQueryData) throws SQLException,
    ParseException {
    ResultSetMetaData mdSource = rsSource.getMetaData();
    boolean result = false;
    int columnCountSource = mdSource.getColumnCount();

    if (bigQueryData == null) {
      Assert.fail("bigQueryData is null");
      return result;
    }

    // Get the column count of the first JsonObject in bigQueryData
    int columnCountTarget = 0;
    if (bigQueryData.size() > 0) {
      columnCountTarget = bigQueryData.get(0).entrySet().size();
    }
    // Compare the number of columns in the source and target
    Assert.assertEquals("Number of columns in source and target are not equal",
                        columnCountSource, columnCountTarget);

    //Variable 'jsonObjectIdx' to track the index of the current JsonObject in the bigQueryData list,
    int jsonObjectIdx = 0;
    while (rsSource.next()) {
      int currentColumnCount = 1;
      while (currentColumnCount <= columnCountSource) {
        String columnTypeName = mdSource.getColumnTypeName(currentColumnCount);
        int columnType = mdSource.getColumnType(currentColumnCount);
        String columnName = mdSource.getColumnName(currentColumnCount);
        // Perform different comparisons based on column type
        switch (columnType) {
          // Since we skip BFILE in Oracle Sink, we are not comparing the BFILE source and sink values
          case OracleSourceSchemaReader.BFILE:
            break;
          case Types.BLOB:
          case Types.VARBINARY:
          case Types.LONGVARBINARY:
            String sourceB64String = new String(Base64.getEncoder().encode(rsSource.getBytes(currentColumnCount)));
            String targetB64String = bigQueryData.get(jsonObjectIdx).get(columnName).getAsString();
            Assert.assertEquals("Different values found for column : %s",
                                sourceB64String, targetB64String);
            break;

          case Types.NUMERIC:
            long sourceVal = rsSource.getLong(currentColumnCount);
            long targetVal = Long.parseLong(bigQueryData.get(jsonObjectIdx).get(columnName).getAsString());
            Assert.assertTrue("Different values found for column : %s",
                              String.valueOf(sourceVal).equals(String.valueOf(targetVal)));
            break;

          case Types.TIMESTAMP:
            Timestamp sourceTS = rsSource.getTimestamp(columnName);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'");
            Date parsedDate = dateFormat.parse(bigQueryData.get(jsonObjectIdx).get(columnName).getAsString());
            Timestamp targetTs = new java.sql.Timestamp(parsedDate.getTime());
            result = sourceTS.equals(targetTs);
            Assert.assertEquals("Different values found for column : %s", result);
            break;
          case OracleSourceSchemaReader.BINARY_FLOAT:
            Float sourceBytes = rsSource.getFloat(currentColumnCount);
            Float targetBytes = bigQueryData.get(jsonObjectIdx).get(columnName).getAsFloat();
            Assert.assertTrue(String.format("Different values found for column : %s", columnName),
                              Float.compare(sourceBytes, targetBytes) == 0);
            break;

          case OracleSourceSchemaReader.BINARY_DOUBLE:
            double sourceVAL = rsSource.getDouble(currentColumnCount);
            double targetVAL = bigQueryData.get(jsonObjectIdx).get(columnName).getAsDouble();
            Assert.assertTrue(String.format("Different values found for column : %s", columnName),
                              Double.compare(sourceVAL, targetVAL) == 0);
            break;

          default:
            String sourceString = rsSource.getString(currentColumnCount);
            String targetString = bigQueryData.get(jsonObjectIdx).get(columnName).getAsString();
            Assert.assertEquals(String.format("Different %s values found for column : %s", columnTypeName, columnName),
                                String.valueOf(sourceString), String.valueOf(targetString));
        }
        currentColumnCount++;
      }
      jsonObjectIdx++;
    }
    Assert.assertFalse("Number of rows in Source table is greater than the number of rows in Target table",
                       rsSource.next());
//    Assert.assertFalse("Number of rows in Target table is greater than the number of rows in Source table",
//                       rsTarget.next());
    return true;
  }
}
