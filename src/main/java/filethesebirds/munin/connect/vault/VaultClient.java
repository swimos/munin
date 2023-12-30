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
import filethesebirds.munin.Utils;
import filethesebirds.munin.digest.Answer;
import filethesebirds.munin.digest.Submission;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;

public abstract class VaultClient {

  public static final VaultClient DRY = new Dry();

  public abstract void upsertSubmissions(Collection<Submission> submissions);

  public abstract void assignObservations(String submissionId36, Answer answer);

  public abstract void deleteSubmission(long submissionId10);

  public final void deleteSubmission(String submissionId36) {
    deleteSubmission(Utils.id36To10(submissionId36));
  }

  public abstract void deleteSubmissions(Collection<String> submissionID36s);

  public static VaultClient fromStream(InputStream is) {
    return new Pooled(Utils.credentialsFromStream(is, p -> new HikariDataSource(new HikariConfig(p))));
  }

  private static class Pooled extends VaultClient {

    private final HikariDataSource source;

    private Pooled(HikariDataSource source) {
      this.source = source;
    }

    private Connection getConnection() throws SQLException {
      return this.source.getConnection();
    }

    @Override
    public void upsertSubmissions(Collection<Submission> submissions) {
      if (submissions == null || submissions.isEmpty()) {
        return;
      }
      try (final Connection conn = getConnection()) {
        conn.setAutoCommit(false);
        final PreparedStatement statement = VaultApi.upsertSubmissions(conn, submissions);
        if (statement != null) {
          statement.executeBatch();
          conn.commit();
        }
      } catch (SQLException e) {
        throw new RuntimeException("Failed to upsert vault submissions", e);
      }
    }

    @Override
    public void assignObservations(String submissionId36, Answer answer) {
      if (answer == null || answer.taxa().isEmpty()) {
        // TODO: should we clear observations still, since that's what the DRY variant does?
        return;
      }
      try (final Connection conn = getConnection()) {
        conn.setAutoCommit(false);
        final PreparedStatement placeholder = VaultApi.createPlaceholderSubmission(conn, submissionId36);
        if (placeholder != null) {
          placeholder.executeUpdate();
        }
        final PreparedStatement delete = VaultApi.deleteObservations(conn, submissionId36);
        if (delete != null) {
          delete.executeUpdate();
        }
        final PreparedStatement insert = VaultApi.insertObservations(conn, submissionId36, answer);
        if (insert != null) {
          insert.executeBatch();
        }
        if (placeholder != null || insert != null || delete != null) {
          conn.commit();
        }
      } catch (SQLException e) {
        throw new RuntimeException("Failed to assign vault observations", e);
      }
    }

    @Override
    public void deleteSubmission(long submissionId10) {
      try (final Connection conn = getConnection()) {
        conn.setAutoCommit(false);
        final PreparedStatement st = VaultApi.deleteSubmission(conn, submissionId10);
        st.executeUpdate();
        conn.commit();
      } catch (SQLException e) {
        throw new RuntimeException("Failed to delete from vault submissions", e);
      }
    }

    @Override
    public void deleteSubmissions(Collection<String> submissionID36s) {
      // FIXME
    }

  }

  private static class Dry extends VaultClient {

    @Override
    public void upsertSubmissions(Collection<Submission> submissions) {
      System.out.println("Dry query: " + VaultApi.upsertSubmissionsQuery(submissions));
    }

    @Override
    public void assignObservations(String submissionId36, Answer answer) {
      System.out.printf("Dry queries: "
          + VaultApi.createPlaceholderSubmissionQuery(submissionId36) + "%n"
          + VaultApi.deleteObservationsQuery(submissionId36) + "%n"
          + (answer == null || answer.taxa().isEmpty() ? ""
              : VaultApi.insertObservationsQuery(submissionId36, answer) + "%n"));
    }

    @Override
    public void deleteSubmission(long submissionId10) {
      System.out.println("Dry query: " + VaultApi.deleteSubmissionQuery(submissionId10));
    }

    @Override
    public void deleteSubmissions(Collection<String> submissionID36s) {
      // FIXME
    }

  }

}
