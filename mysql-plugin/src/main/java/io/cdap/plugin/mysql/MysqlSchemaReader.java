/*
 * Copyright © 2022 Cask Data, Inc.
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

package io.cdap.plugin.mysql;

import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.plugin.db.CommonSchemaReader;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Schema reader for mapping Mysql DB type
 */
public class MysqlSchemaReader extends CommonSchemaReader {

  public static final String YEAR_TYPE_NAME = "YEAR";

  private final String sessionID;

  public MysqlSchemaReader(String sessionID) {
    super();
    this.sessionID = sessionID;
  }

  @Override
  public boolean shouldIgnoreColumn(ResultSetMetaData metadata, int index) throws SQLException {
    return metadata.getColumnName(index).equals("c_" + sessionID) ||
      metadata.getColumnName(index).equals("sqn_" + sessionID);
  }

  @Override
  public Schema getSchema(ResultSetMetaData metadata, int index) throws SQLException {
    int sqlType = metadata.getColumnType(index);
    String sqlTypeName = metadata.getColumnTypeName(index);

    // YEAR type in MySQL should get converted to integer to avoid truncation
    // failures in the MySQL Sink plugin due to missing date and month details.
    if (sqlType == Types.DATE && YEAR_TYPE_NAME.equalsIgnoreCase(sqlTypeName)) {
      return Schema.of(Schema.Type.INT);
    }

    return super.getSchema(metadata, index);
  }
}
