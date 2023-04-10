// Copyright 2015-2023 Swim.inc
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package filethesebirds.munin.connect.vault;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import filethesebirds.munin.digest.Submission;
import filethesebirds.munin.util.ConfigUtils;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;

public abstract class VaultClient {

  public static final VaultClient DRY = new Dry();

  protected abstract Connection getConnection() throws SQLException;

  public abstract void upsertSubmissions(Submission[] submissions);

  public abstract void upsertObservations();

  public static VaultClient fromStream(InputStream is) {
    return new Pooled(
        new HikariDataSource(
            ConfigUtils.credentialsFromStream(is,
                p -> new HikariDataSource(new HikariConfig(p)))));
  }

  private static class Pooled extends VaultClient {

    private final HikariDataSource source;

    private Pooled(HikariDataSource source) {
      this.source = source;
    }

    @Override
    protected Connection getConnection() throws SQLException {
      return this.source.getConnection();
    }

    @Override
    public void upsertSubmissions(Submission[] submissions) {
      try {
        VaultApi.upsertSubmissions(getConnection(), submissions)
            .executeBatch();
      } catch (SQLException e) {
        throw new RuntimeException("Failed to write to vault", e);
      }
    }

    @Override
    public void upsertObservations() {

    }

  }

  private static class Dry extends VaultClient {

    @Override
    public Connection getConnection() {
      return null;
    }

    @Override
    public void upsertSubmissions(Submission[] submissions) {
      System.out.println("Dry query: " + VaultApi.upsertSubmissionsQuery(submissions));
    }

    @Override
    public void upsertObservations() {

    }

  }

}
