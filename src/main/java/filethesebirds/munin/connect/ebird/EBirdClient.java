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
import java.io.InputStream;
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

  public static EBirdClient fromStream(HttpClient executor, InputStream stream) {
    return new EBirdClient(executor, EBirdCredentials.fromStream(stream).userAgent());
  }

  private static <V> boolean responseIsSuccessful(HttpResponse<V> response) {
    return response.statusCode() == 200;
  }

  private String makeApiCall(Supplier<HttpRequest> requestSupplier)
      throws EBirdApiException {
    final HttpResponse<String> resp = HttpUtils.fireRequest(this.executor, requestSupplier.get(),
        BodyHandlers.ofString(), 3);
    if (responseIsSuccessful(resp)) {
      return resp.body();
    } else {
      throw new EBirdApiException("Problematic API response with code " + resp.statusCode()
          + ". Headers: " + resp.headers());
    }
  }

  private String find(Supplier<HttpRequest> usSupplier,
                      Supplier<HttpRequest> ukSupplier,
                      Supplier<HttpRequest> phSupplier)
      throws EBirdApiException {
    final String us = makeApiCall(usSupplier);
    if (us.length() < 5) {
      final String uk = makeApiCall(ukSupplier);
      if (uk.length() < 5) {
        return makeApiCall(phSupplier);
      }
      return uk;
    }
    return us;
  }

  public String findIssf(String query) throws EBirdApiException {
    return find(() -> EBirdApi.findIssf(query, this.userAgent),
        () -> EBirdApi.findIssfUK(query, this.userAgent),
        () -> EBirdApi.findIssfPH(query, this.userAgent));
  }

  public String findForm(String query) throws EBirdApiException {
    return find(() -> EBirdApi.findForm(query, this.userAgent),
        () -> EBirdApi.findFormUK(query, this.userAgent),
        () -> EBirdApi.findFormPH(query, this.userAgent));
  }

  public String findIntergrade(String query) throws EBirdApiException {
    return find(() -> EBirdApi.findIntergrade(query, this.userAgent),
        () -> EBirdApi.findIntergradeUK(query, this.userAgent),
        () -> EBirdApi.findIntergradePH(query, this.userAgent));
  }

  public String findSpecies(String query) throws EBirdApiException {
    if (query.contains("chicken") && !query.contains("prairie")) {
      return "redjun1";
    }
    if (query.contains("pied") && query.contains("wagtail") && !query.contains("wagtail")) {
      return "whiwag3";
    }
    return find(() -> EBirdApi.findSpecies(query, this.userAgent),
        () -> EBirdApi.findSpeciesUK(query, this.userAgent),
        () -> EBirdApi.findSpeciesPH(query, this.userAgent));
  }

  public String findDomestic(String query) throws EBirdApiException {
    return find(() -> EBirdApi.findDomestic(query, this.userAgent),
        () -> EBirdApi.findDomesticUK(query, this.userAgent),
        () -> EBirdApi.findDomesticPH(query, this.userAgent));
  }

  public String findHybrid(String query) throws EBirdApiException {
    return find(() -> EBirdApi.findHybrid(query, this.userAgent),
        () -> EBirdApi.findHybridUK(query, this.userAgent),
        () -> EBirdApi.findHybridPH(query, this.userAgent));
  }

  public String findSlash(String query) throws EBirdApiException {
    return find(() -> EBirdApi.findSlash(query, this.userAgent),
        () -> EBirdApi.findSlashUK(query, this.userAgent),
        () -> EBirdApi.findSlashPH(query, this.userAgent));
  }

  public String findSpuh(String query) throws EBirdApiException {
    return find(() -> EBirdApi.findSpuh(query, this.userAgent),
        () -> EBirdApi.findSpuhUK(query, this.userAgent),
        () -> EBirdApi.findSpuhPH(query, this.userAgent));
  }

  public String findTaxon(String query) throws EBirdApiException {
    return find(() -> EBirdApi.findTaxon(query, this.userAgent),
        () -> EBirdApi.findTaxonUK(query, this.userAgent),
        () -> EBirdApi.findTaxonPH(query, this.userAgent));
  }

}
