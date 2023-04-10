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

import filethesebirds.munin.digest.Submission;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

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

  private static final String UPSERT_SUBMISSIONS_PREFIX = "INSERT into SUBMISSIONS VALUES";
  private static final String UPSERT_SUBMISSIONS_SUFFIX = " ON CONFLICT (submission_id) DO UPDATE SET"
      + " location = EXCLUDED.location,"
      + " upload_date = EXCLUDED.upload_date,"
      + " karma = EXCLUDED.karma,"
      + " comment_count = EXCLUDED.comment_count,"
      + " title = EXCLUDED.title;";

  static PreparedStatement upsertSubmissions(Connection conn, Submission[] submissions)
      throws SQLException {
    if (submissions == null || submissions.length == 0) {
      return null;
    }
    final String template = UPSERT_SUBMISSIONS_PREFIX
        + " (?, ?, ?, ?, ?, ?, ?)"
        + UPSERT_SUBMISSIONS_SUFFIX;
    final PreparedStatement st = conn.prepareStatement(template);
    for (Submission submission : submissions) {
      st.setLong(1, Long.parseLong(submission.id(), 36));
      st.setObject(2, submission.location().text(), Types.OTHER);
      st.setTimestamp(3, epochSecondsToTimestamp(submission.createdUtc()));
      st.setInt(4, submission.karma());
      st.setInt(5, submission.commentCount());
      st.setString(6, submission.title());
      st.setObject(7, "unanswered", Types.OTHER); // status
      st.addBatch();
    }
    return st;
  }

  static String upsertSubmissionsQuery(Submission[] submissions) {
    if (submissions == null || submissions.length == 0) {
      return null;
    }
    final String repeatFmt = " (%d, '%s', '%s', %d, %d, '%s', 'unanswered')";
    final StringBuilder sb = new StringBuilder(512);
    sb.append(UPSERT_SUBMISSIONS_PREFIX);
    sb.append(formatRepeatUpsertSubmission(repeatFmt, submissions[0]));
    for (int i = 1; i < submissions.length; i++) {
      sb.append(formatRepeatUpsertSubmission("," + repeatFmt, submissions[i]));
    }
    sb.append(UPSERT_SUBMISSIONS_SUFFIX);
    return sb.toString();
  }

  private static String formatRepeatUpsertSubmission(String fmt, Submission submission) {
    return String.format(fmt,
        Long.parseLong(submission.id(), 36),
        submission.location().text(),
        epochSecondsToString(submission.createdUtc()),
        submission.karma(),
        submission.commentCount(),
        submission.title().replace("'", "''"));
  }

}
