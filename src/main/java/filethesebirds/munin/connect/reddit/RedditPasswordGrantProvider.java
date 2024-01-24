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

package filethesebirds.munin.connect.reddit;

import filethesebirds.munin.connect.http.HttpUtils;
import filethesebirds.munin.connect.http.StatusCodeException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import swim.json.Json;
import swim.structure.Value;
import static java.net.http.HttpResponse.BodyHandlers;

public class RedditPasswordGrantProvider {

  private static final URI AUTH_ENDPOINT = URI.create("https://www.reddit.com/api/v1/access_token");
  private static final String POST_DATA_FMT = "grant_type=password&username=%s&password=%s";
  private static final long TOKEN_FETCH_TIMEOUT_MILLIS = 12000L;

  private final String userAgent;
  private final HttpRequest tokenFetchRequest;
  private volatile String currentToken;
  private volatile long currentExpiry;

  public RedditPasswordGrantProvider(RedditCredentials credentials) {
    this.userAgent = credentials.userAgent();
    this.tokenFetchRequest = defaultTokenFetchRequest(credentials);
    this.currentToken = null;
    this.currentExpiry = -1L;
  }

  private static HttpRequest defaultTokenFetchRequest(RedditCredentials credentials) {
    return HttpRequest.newBuilder(AUTH_ENDPOINT)
        .POST(HttpRequest.BodyPublishers.ofString(String.format(POST_DATA_FMT,
            credentials.user(), credentials.pass())))
        .header("Authorization","Basic " + Base64.getEncoder()
            .encodeToString((credentials.clientId() + ":" + credentials.clientSecret())
                .getBytes(StandardCharsets.UTF_8)))
        .header("User-Agent", credentials.userAgent())
        .timeout(Duration.ofMillis(TOKEN_FETCH_TIMEOUT_MILLIS))
        .build();
  }

  protected void fetchNewToken(HttpClient executor) throws StatusCodeException {
    final long beforeFire = System.currentTimeMillis();
    final HttpResponse<String> resp = fireTokenRequest(executor);
    try {
      updateToken(resp, beforeFire);
    } catch (RuntimeException e) {
      System.out.println("[ERROR] could not ");
      throw e;
    }
  }

  private HttpResponse<String> fireTokenRequest(HttpClient executor) throws StatusCodeException {
    final HttpResponse<String> result = HttpUtils.fireRequest(executor, this.tokenFetchRequest, BodyHandlers.ofString(), 1);
    if (result.statusCode() / 100 != 2) {
      throw new StatusCodeException(result.statusCode(), result.headers().toString());
    }
    return result;
  }

  private void updateToken(HttpResponse<String> tokenFetchResponse, long beforeFire) {
    final String body = tokenFetchResponse.body();
    final Value bodyVal = Json.parse(body);
    final String accessToken = bodyVal.get("access_token").stringValue();
    final long expiresIn = bodyVal.get("expires_in").longValue(3600L);
    this.currentToken = accessToken;
    this.currentExpiry = // beforeFire - TOKEN_FETCH_TIMEOUT_MILLIS + expiresIn * 1000;
        beforeFire - TOKEN_FETCH_TIMEOUT_MILLIS + 180L * 1000;
    System.out.println("[INFO] token expiry updated to " + this.currentExpiry + " (from payload="
        + Json.toString(bodyVal.updatedSlot("access_token", "[REDACTED]")) + ")");
  }

  public String userAgent() {
    return this.userAgent;
  }

  public String currentToken() {
    return this.currentToken;
  }

  public long currentExpiry() {
    return this.currentExpiry;
  }

}
