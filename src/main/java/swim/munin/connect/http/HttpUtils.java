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

package swim.munin.connect.http;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static java.net.http.HttpResponse.BodyHandler;

public class HttpUtils {

  private HttpUtils() {
  }

  /**
   * Synchronously sends an HTTP request, immediately retrying a bounded number
   * of times in the event of failure. May block for up to duration {@code
   * request.timeout() * attempts}, or indefinitely if {@code request.timeout()}
   * is null.
   */
  public static <T> HttpResponse<T> fireRequest(HttpClient executor,
        HttpRequest request, BodyHandler<T> handler, int attempts) {
    if (attempts <= 0) {
      throw new IllegalArgumentException("attempts must be positive");
    }
    int i;
    Throwable lastNetworkError = null;
    for (i = 0; i < attempts; i++) {
      try {
        return executor.send(request, handler);
      } catch (Exception e) {
        lastNetworkError = e;
      }
    }
    throw new HttpConnectException("Failed to complete " + request + " with timeout="
        + request.timeout().orElse(null) + " within " + attempts + " attempts", lastNetworkError);
  }

}
