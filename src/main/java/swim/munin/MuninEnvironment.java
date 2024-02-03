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

package swim.munin;

import java.util.Properties;

public class MuninEnvironment {

  private static final String SUBREDDIT = "whatsthisbird";
  private static final int LOOKBACK_HOURS = 36;
  private static final int COMMENTS_FETCH_PERIOD_SECONDS = 60;
  private static final int SUBMISSIONS_FETCH_PERIOD_SECONDS = 180;
  private static final int PUBLISH_PERIOD_SECONDS = 10;
  private static final int SUBMISSIONS_EXPIRY_CHECK_PERIOD_SECONDS = 15 * 60;

  private final String subreddit;
  private final int lookbackHours;
  private final int commentsFetchPeriodSeconds;
  private final int submissionsFetchPeriodSeconds;
  private final int publishPeriodSeconds;
  private final int submissionsExpiryCheckPeriodSeconds;

  public MuninEnvironment(String subreddit, int lookbackHours, int commentsFetchPeriodSeconds,
                          int submissionsFetchPeriodSeconds, int publishPeriodSeconds,
                          int submissionsExpiryCheckPeriodSeconds) {
    this.subreddit = subreddit;
    this.lookbackHours = lookbackHours;
    this.commentsFetchPeriodSeconds = commentsFetchPeriodSeconds;
    this.submissionsFetchPeriodSeconds = submissionsFetchPeriodSeconds;
    this.publishPeriodSeconds = publishPeriodSeconds;
    this.submissionsExpiryCheckPeriodSeconds = submissionsExpiryCheckPeriodSeconds;
  }

  public MuninEnvironment() {
    this(SUBREDDIT, LOOKBACK_HOURS, COMMENTS_FETCH_PERIOD_SECONDS,
        SUBMISSIONS_FETCH_PERIOD_SECONDS, PUBLISH_PERIOD_SECONDS,
        SUBMISSIONS_EXPIRY_CHECK_PERIOD_SECONDS);
  }

  public String subreddit() {
    return this.subreddit;
  }

  public int lookbackHours() {
    return this.lookbackHours;
  }

  public long lookbackSeconds() {
    return 3600L * lookbackHours();
  }

  public long lookbackMillis() {
    return 1000L * lookbackSeconds();
  }

  public long commentsFetchPeriodSeconds() {
    return this.commentsFetchPeriodSeconds;
  }

  public long commentsFetchPeriodMillis() {
    return 1000L * commentsFetchPeriodSeconds();
  }

  public long submissionsFetchPeriodSeconds() {
    return this.submissionsFetchPeriodSeconds;
  }

  public long submissionsFetchPeriodMillis() {
    return 1000L * submissionsFetchPeriodSeconds();
  }

  public long publishPeriodSeconds() {
    return this.publishPeriodSeconds;
  }

  public long publishPeriodMillis() {
    return 1000L * publishPeriodSeconds();
  }

  public long submissionsExpiryCheckPeriodSeconds() {
    return this.submissionsExpiryCheckPeriodSeconds;
  }

  public long submissionsExpiryCheckPeriodMillis() {
    return 1000L * submissionsExpiryCheckPeriodSeconds();
  }

  @Override
  public String toString() {
    return "MuninEnvironment{" +
        "subreddit='" + subreddit + '\'' +
        ", lookbackHours=" + lookbackHours +
        ", commentsFetchPeriodSeconds=" + commentsFetchPeriodSeconds +
        ", submissionsFetchPeriodSeconds=" + submissionsFetchPeriodSeconds +
        ", publishPeriodSeconds=" + publishPeriodSeconds +
        '}';
  }

  public static MuninEnvironment fromSystemProperties() {
    return fromProperties(System.getProperties());
  }

  public static MuninEnvironment fromProperties(Properties properties) {
    return new MuninEnvironment(
        Utils.sanitizeSubreddit(System.getProperty("munin.subreddit", SUBREDDIT)),
        Math.max(intProperty(properties, "munin.lookback.hours", LOOKBACK_HOURS), 1),
        Math.max(intProperty(properties, "munin.comments.fetch.period.seconds", COMMENTS_FETCH_PERIOD_SECONDS), 15),
        Math.max(intProperty(properties, "munin.submissions.fetch.period.seconds", SUBMISSIONS_FETCH_PERIOD_SECONDS), 30),
        Math.max(intProperty(properties, "munin.publish.period.seconds", PUBLISH_PERIOD_SECONDS), 5),
        Math.max(intProperty(properties, "munin.submissions.expiry.check.period.seconds", SUBMISSIONS_EXPIRY_CHECK_PERIOD_SECONDS), 30));
  }

  private static int intProperty(Properties properties, String name, int defaultValue) {
    return Integer.parseInt(properties.getProperty(name, String.valueOf(defaultValue)));
  }

}
