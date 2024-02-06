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

package swim.munin.filethesebirds.swim;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.Properties;
import swim.munin.MuninEnvironment;
import swim.munin.Utils;
import swim.munin.connect.reddit.RedditClient;
import swim.munin.filethesebirds.connect.ebird.EBirdClient;
import swim.munin.filethesebirds.connect.vault.VaultClient;
import swim.munin.swim.Coalescence;
import swim.munin.swim.LiveSubmissions;

/**
 * Utility class containing objects that might be used concurrently by multiple
 * Web Agents.
 */
public final class Shared {

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private Shared() {
  }

  private static MuninEnvironment muninEnvironment = null;
  private static LiveSubmissions liveSubmissions = null;
  private static EBirdClient eBirdClient = null;
  private static RedditClient redditClient = null;
  private static VaultClient vaultClient = null;

  public static HttpClient httpClient() {
    return HTTP_CLIENT;
  }

  public static MuninEnvironment muninEnvironment() {
    return Shared.muninEnvironment;
  }

  public static LiveSubmissions liveSubmissions() {
    return Shared.liveSubmissions;
  }

  public static EBirdClient eBirdClient() {
    return Shared.eBirdClient;
  }

  public static RedditClient redditClient() {
    return Shared.redditClient;
  }

  public static VaultClient vaultClient() {
    return Shared.vaultClient;
  }

  public static void loadMuninEnvironment() {
    loadMuninEnvironment(System.getProperties());
  }

  public static void loadMuninEnvironment(Properties properties) {
    if (Shared.muninEnvironment != null) {
      throw new IllegalStateException("Multiple muninEnvironment loading forbidden");
    }
    Shared.muninEnvironment = MuninEnvironment.fromProperties(properties);
    System.out.println("[INFO] Using environment " + Shared.muninEnvironment);
  }

  public static void loadLiveSubmissions(Coalescence coalescence) {
    if (Shared.liveSubmissions != null) {
      throw new IllegalStateException("Multiple liveSubmissions loading forbidden");
    }
    Shared.liveSubmissions = coalescence.toLiveSubmissions();
  }

  public static void loadEBirdClient() {
    if (Shared.eBirdClient != null) {
      throw new IllegalStateException("Multiple eBird client loading forbidden");
    }
    try (InputStream is = Utils.openConfigFile(System.getProperty("ebird.conf"), "/filethesebirds/ebird-config.properties")) {
      Shared.eBirdClient = EBirdClient.fromStream(httpClient(), is);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load eBird client", e);
    }
  }

  public static void loadRedditClient() {
    if (Shared.redditClient != null) {
      throw new IllegalStateException("Multiple Reddit client loading forbidden");
    }
    try (InputStream is = Utils.openConfigFile(System.getProperty("reddit.conf"), "/reddit-config.properties")) {
      Shared.redditClient = RedditClient.fromStream("whatsthisbird", httpClient(), is);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load Reddit client", e);
    }
  }

  public static void loadVaultClient() {
    if (Shared.redditClient != null) {
      throw new IllegalStateException("Multiple Reddit client loading forbidden");
    }
    try (InputStream is = Utils.openConfigFile(System.getProperty("vault.conf"), "/filethesebirds/vault-config.properties")) {
      Shared.vaultClient = VaultClient.fromStream(is);
    } catch (Exception e) {
      System.out.println("[WARN] Failed to load vault client (trace below). All intended queries will be logged, but not executed. Trace:");
      e.printStackTrace();
      Shared.vaultClient = VaultClient.DRY;
    }
  }

}
