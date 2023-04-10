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

package filethesebirds.munin.connect.ebird;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

class EBirdApi {

  private static final String DOMAIN = "https://api.ebird.org";
  private static final String ENDPOINT = "/v2/ref/taxon/find";

  private static final String LOCALE_FMT = "?locale=%s";
  private static final String LOCALE_US = String.format(LOCALE_FMT, "en_US");
  private static final String LOCALE_UK = String.format(LOCALE_FMT, "en_UK");
  private static final String LOCALE_PH = String.format(LOCALE_FMT, "en_PH");

  private static final String KEY_FMT = "&key=%s";
  // key used by any browser visiting ebird.org, shouldn't be a secret
  private static final String TAXON_FIND_KEY = String.format(KEY_FMT, "cnh95enq2brj");

  private static final String CAT_FMT = "&cat=%s";
  private static final String CAT_ISSF = String.format(CAT_FMT, "issf");
  private static final String CAT_FORM = String.format(CAT_FMT, "form");
  private static final String CAT_INTERGRADE = String.format(CAT_FMT, "intergrade");
  private static final String CAT_SPECIES = String.format(CAT_FMT, "species");
  private static final String CAT_DOMESTIC = String.format(CAT_FMT, "domestic");
  private static final String CAT_HYBRID = String.format(CAT_FMT, "hybrid");
  private static final String CAT_SLASH = String.format(CAT_FMT, "slash");
  private static final String CAT_SPUH = String.format(CAT_FMT, "spuh");

  private static final String COMMON_PREFIX = DOMAIN + ENDPOINT + LOCALE_US
      + TAXON_FIND_KEY;
  private static final String UK_PREFIX = DOMAIN + ENDPOINT + LOCALE_UK
      + TAXON_FIND_KEY;
  private static final String PH_PREFIX = DOMAIN + ENDPOINT + LOCALE_PH
      + TAXON_FIND_KEY;

  private static final String Q_FMT = "&q=%s";

  private EBirdApi() {
  }

  private static HttpRequest get(URI uri, String userAgent) {
    return HttpRequest.newBuilder(uri)
        .GET()
        .header("User-Agent", userAgent)
        .timeout(Duration.ofMillis(5000L))
        .build();
  }

  private static URI findEndpoint(String prefix, String query) {
    return URI.create(prefix + String.format(Q_FMT, query));
  }

  public static HttpRequest findIssf(String query, String userAgent) {
    return get(findEndpoint(COMMON_PREFIX + CAT_ISSF, query), userAgent);
  }

  public static HttpRequest findIssfUK(String query, String userAgent) {
    return get(findEndpoint(UK_PREFIX + CAT_ISSF, query), userAgent);
  }

  public static HttpRequest findIssfPH(String query, String userAgent) {
    return get(findEndpoint(PH_PREFIX + CAT_ISSF, query), userAgent);
  }

  public static HttpRequest findForm(String query, String userAgent) {
    return get(findEndpoint(COMMON_PREFIX + CAT_FORM, query), userAgent);
  }

  public static HttpRequest findFormUK(String query, String userAgent) {
    return get(findEndpoint(UK_PREFIX + CAT_FORM, query), userAgent);
  }

  public static HttpRequest findFormPH(String query, String userAgent) {
    return get(findEndpoint(PH_PREFIX + CAT_FORM, query), userAgent);
  }

  public static HttpRequest findIntergrade(String query, String userAgent) {
    return get(findEndpoint(COMMON_PREFIX + CAT_INTERGRADE, query), userAgent);
  }

  public static HttpRequest findIntergradeUK(String query, String userAgent) {
    return get(findEndpoint(UK_PREFIX + CAT_INTERGRADE, query), userAgent);
  }

  public static HttpRequest findIntergradePH(String query, String userAgent) {
    return get(findEndpoint(PH_PREFIX + CAT_INTERGRADE, query), userAgent);
  }

  public static HttpRequest findSpecies(String query, String userAgent) {
    return get(findEndpoint(COMMON_PREFIX + CAT_SPECIES, query), userAgent);
  }

  public static HttpRequest findSpeciesUK(String query, String userAgent) {
    return get(findEndpoint(UK_PREFIX + CAT_SPECIES, query), userAgent);
  }

  public static HttpRequest findSpeciesPH(String query, String userAgent) {
    return get(findEndpoint(PH_PREFIX + CAT_SPECIES, query), userAgent);
  }

  public static HttpRequest findDomestic(String query, String userAgent) {
    return get(findEndpoint(COMMON_PREFIX + CAT_DOMESTIC, query), userAgent);
  }

  public static HttpRequest findDomesticUK(String query, String userAgent) {
    return get(findEndpoint(UK_PREFIX + CAT_DOMESTIC, query), userAgent);
  }

  public static HttpRequest findDomesticPH(String query, String userAgent) {
    return get(findEndpoint(PH_PREFIX + CAT_DOMESTIC, query), userAgent);
  }

  public static HttpRequest findHybrid(String query, String userAgent) {
    return get(findEndpoint(COMMON_PREFIX + CAT_HYBRID, query), userAgent);
  }

  public static HttpRequest findHybridUK(String query, String userAgent) {
    return get(findEndpoint(UK_PREFIX + CAT_HYBRID, query), userAgent);
  }

  public static HttpRequest findHybridPH(String query, String userAgent) {
    return get(findEndpoint(PH_PREFIX + CAT_HYBRID, query), userAgent);
  }

  public static HttpRequest findSlash(String query, String userAgent) {
    return get(findEndpoint(COMMON_PREFIX + CAT_SLASH, query), userAgent);
  }

  public static HttpRequest findSlashUK(String query, String userAgent) {
    return get(findEndpoint(UK_PREFIX + CAT_SLASH, query), userAgent);
  }

  public static HttpRequest findSlashPH(String query, String userAgent) {
    return get(findEndpoint(PH_PREFIX + CAT_SLASH, query), userAgent);
  }

  public static HttpRequest findSpuh(String query, String userAgent) {
    return get(findEndpoint(COMMON_PREFIX + CAT_SPUH, query), userAgent);
  }

  public static HttpRequest findSpuhUK(String query, String userAgent) {
    return get(findEndpoint(UK_PREFIX + CAT_SPUH, query), userAgent);
  }

  public static HttpRequest findSpuhPH(String query, String userAgent) {
    return get(findEndpoint(PH_PREFIX + CAT_SPUH, query), userAgent);
  }


  public static HttpRequest findTaxon(String query, String userAgent) {
    return get(findEndpoint(COMMON_PREFIX, query), userAgent);
  }

  public static HttpRequest findTaxonUK(String query, String userAgent) {
    return get(findEndpoint(UK_PREFIX, query), userAgent);
  }

  public static HttpRequest findTaxonPH(String query, String userAgent) {
    return get(findEndpoint(PH_PREFIX, query), userAgent);
  }

}
