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

package swim.munin.primitive.swim;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.Properties;
import java.util.concurrent.ConcurrentSkipListMap;
import swim.munin.MuninEnvironment;
import swim.munin.Utils;
import swim.munin.connect.reddit.RedditClient;
import swim.munin.swim.LiveSubmissions;

public final class Shared {

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private Shared() {
  }

  private static MuninEnvironment muninEnvironment = null;
  private static LiveSubmissions liveSubmissions = null;
  private static RedditClient redditClient = null;

  public static HttpClient httpClient() {
    return HTTP_CLIENT;
  }

  public static MuninEnvironment muninEnvironment() {
    return Shared.muninEnvironment;
  }

  public static LiveSubmissions liveSubmissions() {
    return Shared.liveSubmissions;
  }

  public static RedditClient redditClient() {
    return Shared.redditClient;
  }

  public static void loadMuninEnvironment() throws IOException {
    final Properties properties = new Properties();
    properties.load(Utils.openConfigFile("/primitive/environment.properties", "/primitive/environment.properties"));
    loadMuninEnvironment(properties);
  }

  public static void loadMuninEnvironment(Properties properties) {
    if (Shared.muninEnvironment != null) {
      throw new IllegalStateException("Multiple muninEnvironment loading forbidden");
    }
    Shared.muninEnvironment = MuninEnvironment.fromProperties(properties);
    System.out.println("[INFO] Using environment " + Shared.muninEnvironment);
  }

  public static void loadLiveSubmissions() {
    if (Shared.liveSubmissions != null) {
      throw new IllegalStateException("Multiple liveSubmissions loading forbidden");
    }
    Shared.liveSubmissions = new LiveSubmissions(Long.MAX_VALUE,
        new ConcurrentSkipListMap<>(), new ConcurrentSkipListMap<>());
  }

  public static void loadRedditClient() {
    if (Shared.redditClient != null) {
      throw new IllegalStateException("Multiple Reddit client loading forbidden");
    }
    if (Shared.muninEnvironment == null) {
      throw new IllegalStateException("Must load munin configuration before Reddit client");
    }
    try (InputStream is = Utils.openConfigFile(System.getProperty("reddit.conf"), "/reddit-config.properties")) {
      Shared.redditClient = RedditClient.fromStream(Shared.muninEnvironment().subreddit(), httpClient(), is);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load Reddit client", e);
    }
  }

}
