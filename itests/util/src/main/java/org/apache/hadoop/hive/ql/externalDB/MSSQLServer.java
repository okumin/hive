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
package org.apache.hadoop.hive.ql.externalDB;

public class MSSQLServer extends AbstractExternalDB {

  @Override
  public String getRootUser() {
    return "sa";
  }

  @Override
  public String getRootPassword() {
    return "Its-a-s3cret";
  }

  @Override
  public String getJdbcUrl() {
    return "jdbc:sqlserver://" + getContainerHostAddress() + ":" + getPort();
  }

  @Override
  public String getJdbcDriver() {
    return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
  }

  @Override
  public String getDockerImageName() {
    return "mcr.microsoft.com/mssql/server:2019-latest";
  }

  @Override
  public String[] getDockerAdditionalArgs() {
    return new String[] { "-p", getPort() + ":1433", "-e", "ACCEPT_EULA=Y", "-e", "SA_PASSWORD=" + getRootPassword(),
        "-d" };
  }

  @Override
  protected int getPort() {
    return 1433;
  }

  @Override
  public boolean isContainerReady(ProcessResults pr) {
    return pr.stdout
        .contains("Recovery is complete. This is an informational message only. No user action is required.");
  }
}
