// Copyright 2015-2022 Swim.inc
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

package filethesebirds.munin.connect.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static java.net.http.HttpResponse.BodyHandler;

public class HttpUtils {

  private HttpUtils() {
  }

  /**
   * Sends an HTTP request, retrying a bounded number of times if network-level
   * errors are encountered in the process.
   */
  public static <T> HttpResponse<T> fireRequest(HttpClient executor,
        HttpRequest request, BodyHandler<T> handler, int attempts) {
    int i;
    Throwable lastNetworkError = null;
    for (i = 0; i < attempts; i++) {
      try {
        return executor.send(request, handler);
      } catch (InterruptedException | IOException e) {
        lastNetworkError = e;
      }
    }
    throw new HttpConnectException("Multiple network-level failures (trace has most recent)", lastNetworkError);
  }

}
