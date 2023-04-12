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
  private static final long TOKEN_FETCH_TIMEOUT_MILLIS = 6000L;

  private final RedditCredentials credentials;

  private volatile String currentToken;
  private volatile long currentExpiry;

  public RedditPasswordGrantProvider(RedditCredentials credentials) {
    this.credentials = credentials;
    this.currentToken = null;
    this.currentExpiry = -1L;
  }

  protected HttpRequest buildTokenFetchRequest() {
    return HttpRequest.newBuilder(AUTH_ENDPOINT)
        .POST(HttpRequest.BodyPublishers.ofString(String.format(POST_DATA_FMT,
            this.credentials.user(), this.credentials.pass())))
        .header("Authorization","Basic " + Base64.getEncoder()
            .encodeToString((this.credentials.clientId() + ":" + this.credentials.clientSecret())
                .getBytes(StandardCharsets.UTF_8)))
        .header("User-Agent", this.credentials.userAgent())
        .timeout(Duration.ofMillis(TOKEN_FETCH_TIMEOUT_MILLIS))
        .build();
  }

  protected void fetchNewToken(HttpClient executor) throws RedditApiException {
    final HttpResponse<String> resp;
    try {
      resp = HttpUtils.fireRequest(executor,
          buildTokenFetchRequest(), BodyHandlers.ofString(), 3);
    } catch (Exception e) {
      throw new RedditApiException("Failed to fetch token", e);
    }
    if (resp.statusCode() / 100 != 2) {
      throw new RedditApiException("Bad token response status code " + resp.statusCode()
          + ". Headers: " + resp.headers());
    }
    final Value bodyVal;
    String body = null;
    try {
      body = resp.body();
      bodyVal = Json.parse(body);
      this.currentToken = bodyVal.get("access_token").stringValue();
      this.currentExpiry = System.currentTimeMillis() - 2 * TOKEN_FETCH_TIMEOUT_MILLIS
          + bodyVal.get("expires_in").longValue(3600L) * 1000;
    } catch (Throwable e) {
      throw new RedditApiException("Failed to extract token from body="
          + (body == null ? "(null)" : " " + body));
    }
  }

  public String currentToken() {
    return this.currentToken;
  }

  public long currentExpiry() {
    return this.currentExpiry;
  }

  public String userAgent() {
    return this.credentials.userAgent();
  }

}
