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

package filethesebirds.munin.connect.ebird;

import filethesebirds.munin.connect.http.HttpUtils;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Supplier;
import static java.net.http.HttpResponse.BodyHandlers;

public class EBirdClient {

  private final HttpClient executor;
  private final String userAgent;

  public EBirdClient(HttpClient executor, String userAgent) {
    this.executor = executor;
    this.userAgent = userAgent;
  }

  public static EBirdClient fromResource(HttpClient executor,
                                         Class<?> resourceClass, String resourcePath) {
    return new EBirdClient(executor,
        EBirdCredentials.fromResource(resourceClass, resourcePath).userAgent());
  }

  private static <V> boolean responseIsSuccessful(HttpResponse<V> response) {
    return response.statusCode() == 200;
  }

  private String makeApiCall(Supplier<HttpRequest> requestSupplier)
      throws EBirdApiException {
    final HttpResponse<String> resp = HttpUtils.fireRequest(this.executor, requestSupplier.get(),
        BodyHandlers.ofString(), 1);
    if (responseIsSuccessful(resp)) {
      return resp.body();
    } else {
      throw new EBirdApiException("Problematic API response with code " + resp.statusCode()
          + ". Headers: " + resp.headers());
    }
  }

  public String findSpecies(String query) throws EBirdApiException {
    return makeApiCall(() -> EBirdApi.findSpecies(query, this.userAgent));
  }

  public String findSpeciesUK(String query) throws EBirdApiException {
    return makeApiCall(() -> EBirdApi.findSpeciesUK(query, this.userAgent));
  }

  public String findSpeciesPH(String query) throws EBirdApiException {
    return makeApiCall(() -> EBirdApi.findSpeciesPH(query, this.userAgent));
  }

  public String findIntergrade(String query) throws EBirdApiException {
    return makeApiCall(() -> EBirdApi.findIntergrade(query, this.userAgent));
  }

  public String findTaxon(String query) throws EBirdApiException {
    return makeApiCall(() -> EBirdApi.findTaxon(query, this.userAgent));
  }

}
