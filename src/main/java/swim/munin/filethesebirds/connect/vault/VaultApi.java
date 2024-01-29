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

package swim.munin.filethesebirds.connect.vault;

import swim.munin.connect.reddit.Submission;
import swim.munin.filethesebirds.digest.Taxonomy;
import swim.munin.Utils;
import swim.munin.filethesebirds.digest.Answer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;

final class VaultApi {

  private VaultApi() {
  }

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private static String epochSecondsToString(long epoch) {
    ZonedDateTime dateTime = Instant.ofEpochSecond(epoch)
        .atZone(ZoneOffset.UTC);
    return dateTime.format(FORMATTER);
  }

  private static Timestamp epochSecondsToTimestamp(long epoch) {
    return new Timestamp(epoch * 1000);
  }

  private static final String UPSERT_SUBMISSIONS_PREFIX = "INSERT INTO submissions VALUES";
  private static final String UPSERT_SUBMISSIONS_SUFFIX = " ON CONFLICT (submission_id) DO UPDATE SET"
      + " location = EXCLUDED.location,"
      + " upload_date = EXCLUDED.upload_date,"
      + " karma = EXCLUDED.karma,"
      + " comment_count = EXCLUDED.comment_count,"
      + " title = EXCLUDED.title;";

  static PreparedStatement upsertSubmissions(Connection conn, Collection<Submission> submissions)
      throws SQLException {
    if (submissions == null || submissions.isEmpty()) {
      return null;
    }
//    final String template = UPSERT_SUBMISSIONS_PREFIX
//        + " (?, ?, ?, ?, ?, ?, ?)"
//        + UPSERT_SUBMISSIONS_SUFFIX;
//    final PreparedStatement st = conn.prepareStatement(template);
//    for (Submission submission : submissions) {
//      st.setLong(1, Utils.id36To10(submission.id()));
//      st.setObject(2, "unknown", Types.OTHER);
//      st.setTimestamp(3, epochSecondsToTimestamp(submission.createdUtc()));
//      st.setInt(4, submission.karma());
//      st.setInt(5, submission.commentCount());
//      st.setString(6, submission.title());
//      st.setObject(7, "unanswered", Types.OTHER); // status
//      st.addBatch();
//    }
//    return st;
    final String questions = "(?, ?, ?, ?, ?, ?, ?)";
    final String template = UPSERT_SUBMISSIONS_PREFIX + " "
            + submissions.stream().map(s -> questions).collect(Collectors.joining(", "))
            + UPSERT_SUBMISSIONS_SUFFIX;
    final PreparedStatement st = conn.prepareStatement(template);
    final Iterator<Submission> itr = submissions.iterator();
    for (int i = 0; itr.hasNext(); i++) {
      final Submission submission = itr.next();
      final int base = 7 * i;
      st.setLong(base + 1, Utils.id36To10(submission.id()));
      st.setObject(base + 2, "unknown", Types.OTHER);
      st.setTimestamp(base + 3, epochSecondsToTimestamp(submission.createdUtc()));
      st.setInt(base + 4, submission.karma());
      st.setInt(base + 5, submission.commentCount());
      st.setString(base + 6, submission.title());
      st.setObject(base + 7, "unanswered", Types.OTHER); // status
    }
    return st;
  }

  static String upsertSubmissionsQuery(Collection<Submission> submissions) {
    if (submissions == null || submissions.isEmpty()) {
      return null;
    }
    return UPSERT_SUBMISSIONS_PREFIX + " "
        + submissions.stream().map(VaultApi::formatRepeatUpsertSubmission)
            .collect(Collectors.joining(" "))
        + UPSERT_SUBMISSIONS_SUFFIX;
  }

  private static String formatRepeatUpsertSubmission(Submission submission) {
    return String.format("(%d, '%s', '%s', %d, %d, '%s', 'unanswered')",
        Utils.id36To10(submission.id()),
        "unknown",
        epochSecondsToString(submission.createdUtc()),
        submission.karma(),
        submission.commentCount(),
        submission.title().replace("'", "''"));
  }

  private static final String CREATE_PLACEHOLDER_SUBMISSION_PREFIX = "INSERT INTO submissions (submission_id) VALUES";

  static String createPlaceholderSubmissionQuery(String submissionId36) {
    return String.format(CREATE_PLACEHOLDER_SUBMISSION_PREFIX + " (%d) ON CONFLICT DO NOTHING;",
        Utils.id36To10(submissionId36));
  }

  static PreparedStatement createPlaceholderSubmission(Connection conn, String submissionId36)
      throws SQLException {
    final long submissionId;
    try {
      submissionId = Utils.id36To10(submissionId36);
    } catch (Exception e) {
      return null;
    }
    final String template = CREATE_PLACEHOLDER_SUBMISSION_PREFIX
        + " (?) ON CONFLICT DO NOTHING;";
    final PreparedStatement st = conn.prepareStatement(template);
    st.setLong(1, submissionId);
    return st;
  }

  private static final String DELETE_OBSERVATIONS_PREFIX = "DELETE FROM observations"
      + " WHERE submission_id = ";

  static PreparedStatement deleteObservations(Connection conn, String submissionId36)
      throws SQLException {
    final long submissionId;
    try {
      submissionId = Utils.id36To10(submissionId36);
    } catch (Exception e) {
      return null;
    }
    final PreparedStatement st = conn.prepareStatement(DELETE_OBSERVATIONS_PREFIX + "?;");
    st.setLong(1, submissionId);
    return st;
  }

  static String deleteObservationsQuery(String submissionId36) {
    final long submissionId;
    try {
      submissionId = Utils.id36To10(submissionId36);
    } catch (Exception e) {
      return null;
    }
    return String.format(DELETE_OBSERVATIONS_PREFIX + "%d;", submissionId);
  }

  private static final String INSERT_OBSERVATIONS_PREFIX = "INSERT INTO observations"
      + " SELECT val.tax_ordinal, val.submission_id, submissions.upload_date"
      + " FROM (VALUES";
  private static final String INSERT_OBSERVATIONS_SUFFIX = ") val (tax_ordinal, submission_id)"
      + " JOIN submissions USING (submission_id)"
      + " ON CONFLICT DO NOTHING";

  static PreparedStatement insertObservations(Connection conn, String submissionId36, Answer answer)
      throws SQLException {
    if (answer == null || answer.taxa().isEmpty()) {
      return null;
    }
    final long submissionId;
    try {
      submissionId = Utils.id36To10(submissionId36);
    } catch (Exception e) {
      return null;
    }
//    final PreparedStatement st = conn.prepareStatement(INSERT_OBSERVATIONS_PREFIX
//        + " (?, ?)" + INSERT_OBSERVATIONS_SUFFIX);
//    for (String code : answer.taxa()) {
//      final int ordinal = Taxonomy.ordinal(code);
//      if (ordinal < 0) {
//        st.close();
//        return null;
//      }
//      st.setInt(1, ordinal);
//      st.setLong(2, submissionId);
//      st.addBatch();
//    }
//    return st;
    final String questions = "(?, ?)";
    final String template = INSERT_OBSERVATIONS_PREFIX + " "
        + answer.taxa().stream().map(s -> questions).collect(Collectors.joining(", "))
        + INSERT_OBSERVATIONS_SUFFIX;
    final PreparedStatement st = conn.prepareStatement(template);
    final Iterator<String> itr = answer.taxa().iterator();
    for (int i = 0; itr.hasNext(); i++) {
      final int ordinal = Taxonomy.ordinal(itr.next());
      if (ordinal < 0) {
        st.close();
        return null;
      }
      final int base = 2 * i;
      st.setInt(base + 1, ordinal);
      st.setLong(base + 2, submissionId);
    }
    return st;
  }

  static String insertObservationsQuery(String submissionId36, Answer answer) {
    final long submissionId;
    try {
      submissionId = Utils.id36To10(submissionId36);
    } catch (Exception e) {
      return null;
    }
    final String repeatFmt = " (%d, %d)";
    if (answer.taxa().isEmpty()) {
      return null;
    }
    final StringBuilder sb = new StringBuilder(128);
    sb.append(INSERT_OBSERVATIONS_PREFIX);
    final Iterator<String> taxaIter = answer.taxa().iterator();
    sb.append(formatRepeatUpsertObservation(repeatFmt, taxaIter.next(), submissionId));
    while (taxaIter.hasNext()) {
      sb.append(formatRepeatUpsertObservation("," + repeatFmt, taxaIter.next(), submissionId));
    }
    sb.append(INSERT_OBSERVATIONS_SUFFIX);
    return sb.toString();
  }

  private static String formatRepeatUpsertObservation(String fmt, String taxon, long submissionId) {
    return String.format(fmt,
        Taxonomy.ordinal(taxon),
        submissionId);
  }

  private static final String DELETE_SUBMISSIONS_PREFIX = "DELETE FROM submissions WHERE submission_id IN (";
  private static final String DELETE_SUBMISSIONS_SUFFIX = ");";

  static PreparedStatement deleteSubmissions10(Connection conn, Collection<Long> submissionIds)
      throws SQLException {
    if (submissionIds == null || submissionIds.isEmpty()) {
      return null;
    }
    return conn.prepareStatement(deleteSubmissions10Query(submissionIds));
  }

  static String deleteSubmissions10Query(Collection<Long> submissionId10s) {
    if (submissionId10s == null || submissionId10s.isEmpty()) {
      return null;
    }
    return DELETE_SUBMISSIONS_PREFIX
        + submissionId10s.stream().map(String::valueOf).collect(Collectors.joining(", "))
        + DELETE_SUBMISSIONS_SUFFIX;
  }

  static PreparedStatement deleteSubmission(Connection conn, long submissionId10)
      throws SQLException {
    final PreparedStatement st = conn.prepareStatement("DELETE FROM submissions WHERE submission_id = ?;");
    st.setLong(1, submissionId10);
    return st;
  }

  static String deleteSubmissionQuery(long submissionId10) {
    try {
      return "DELETE FROM submissions WHERE submission_id = "
          +  submissionId10 + ';';
    } catch (Exception e) {
      return null;
    }
  }

}
