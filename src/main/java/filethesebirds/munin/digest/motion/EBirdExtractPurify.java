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

package filethesebirds.munin.digest.motion;

import filethesebirds.munin.connect.ebird.EBirdApiException;
import filethesebirds.munin.connect.ebird.EBirdClient;
import filethesebirds.munin.digest.Motion;
import org.apache.commons.text.similarity.LevenshteinDistance;
import swim.json.Json;
import swim.structure.Item;
import swim.structure.Value;

public final class EBirdExtractPurify {

  private EBirdExtractPurify() {
  }

  public static boolean extractIsPurified(Extract extract) {
    return extract == null || (extract.hints().isEmpty() && extract.vagueHints().isEmpty());
  }

  public static Extract purifyOneHint(EBirdClient client, Extract extract)
      throws EBirdApiException {
    if (extractIsPurified(extract)) {
      return extract;
    }
    if (!extract.hints().isEmpty()) {
      final String hint = extract.hints().stream().findAny().get();
      final String cachedCode = HintCache.get(hint);
      if (cachedCode != null) {
        System.out.println("[INFO] cache hit for " + hint);
        return extract.purifyHint(hint, cachedCode);
      } else {
        final String taxon = exploreHint(client, hint, false);
        if (taxon != null) {
          HintCache.put(hint, taxon);
        }
        return extract.purifyHint(hint, taxon);
      }
    } else {
      final String hint = extract.vagueHints().stream().findAny().get();
      final String taxon = exploreVagueHint(client, hint, false);
      return extract.purifyVagueHint(hint, taxon);
    }
  }

  private static Extract purifyOneHintFailFast(EBirdClient client, Extract extract) {
    if (extractIsPurified(extract)) {
      return extract;
    }
    if (!extract.hints().isEmpty()) {
      final String hint = extract.hints().stream().findAny().get();
      String taxon = null;
      try {
        taxon = exploreHint(client, hint, false);
      } catch (EBirdApiException e) {
        // swallow
      }
      return extract.purifyHint(hint, taxon);
    } else {
      final String hint = extract.vagueHints().stream().findAny().get();
      String taxon = null;
      try {
        taxon = exploreVagueHint(client, hint, false);
      } catch (EBirdApiException e) {
        // swallow
      }
      return extract.purifyVagueHint(hint, taxon);
    }
  }

  public static String[] purifyDryRun(Extract extract) {
    final String[] result = new String[extract.hints().size() + extract.vagueHints().size()];
    int i = 0;
    try {
      for (String hint : extract.hints()) {
        result[i++] = exploreHint(null, hint, true);
      }
      for (String hint : extract.vagueHints()) {
        result[i++] = exploreVagueHint(null, hint, true);
      }
    } catch (EBirdApiException e) {
      // impossible
    }
    return result;
  }

  @Deprecated
  public static Motion eBirdPurify(Extract extract, EBirdClient client) {
    return eBirdPurify(extract, client, 10);
  }

  private static Motion eBirdPurify(Extract extract, EBirdClient client, int depth) {
    if (depth <= 0 || extractIsPurified(extract)) {
      return extract.base();
    }
    return eBirdPurify(purifyOneHintFailFast(client, extract), client, depth - 1);
  }

  private static String processStringResponse(String response, String tweakedHint) {
    final Value found = Json.parse(response);
    if (found.length() == 0) { // TODO: || found.length() > 6
      return null;
    }
    if (found.length() > 1) {
      return found.getItem(disambiguate(tweakedHint, found)).get("code").stringValue();
    } else {
      return found.getItem(0).get("code").stringValue();
    }
  }

  // FIXME
  private static int disambiguate(String hint, Value payload) {
    final LevenshteinDistance distance = new LevenshteinDistance();
    int min = Integer.MAX_VALUE;
    int bestIdx = 0;
    for (int i = 0; i < payload.length(); i++) {
      final Item item = payload.getItem(i);
      final String commonName = item.get("name").stringValue().split(" - ")[0];
//      if (commonName.contains("uropea") || commonName.contains("urasi") || commonName.contains("ommon ")) {
//        return i;
//      }
      final int dist = distance.apply(hint, commonName);
      if (dist < min) {
        min = dist;
        bestIdx = i;
      }

    }
    return bestIdx;
  }

  @FunctionalInterface
  private interface ClientFind {

    String find(String hint) throws EBirdApiException;

  }

  private static String exploreHint(String hint, ClientFind cf)
      throws EBirdApiException {
    return processStringResponse(cf.find(hint), hint);
  }

  private static String exploreHint(EBirdClient client, String hint, boolean dry)
      throws EBirdApiException {
    if (hint.contains("hybrid")) {
      return dry ? "hybrid: " + hint : exploreHint(hint, client::findHybrid);
    } else if (hint.contains("domestic") || hint.contains("feral")) {
      final String newHint = hint.replace("feral", "");
      return dry ? "domestic: " + newHint : exploreHint(newHint, client::findDomestic);
    } else if (hint.contains("intergrade") || hint.contains("integrade")) {
      final String newHint = hint.replace("intergrade", "").replace("integrade", "");
      return dry ? "intergrade: " + newHint : exploreHint(newHint, client::findIntergrade);
    } else if (hint.contains("/")) { // TODO: consider trying hybrids/intergrades for this case
      return dry ? "slash: " + hint : exploreHint(hint, client::findSlash);
    } else if (hint.startsWith("sp%20") || hint.endsWith("%20sp")  || hint.contains("%20sp.")) {
      return dry ? "spuh: " + hint : exploreHint(hint, client::findSpuh);
    } else if (hint.contains("%20x%20")) {
      // First try hybrid, then intergrade
      if (dry) {
        return "hybrid-intergrade: " + hint;
      }
      final String res = exploreHint(hint, client::findHybrid);
      return res == null ? exploreHint(hint, client::findIntergrade) : res;
    } else if (hint.contains("(") || hint.contains("subsp") || hint.contains("ssp")) {
      final String newHint =  hint.replaceAll("\\bsubsp", "") // FIXME: this doesn't look right
          .replace("ssp", "");
      if (dry) {
        return "issf-form: " + newHint;
      }
      // First try issf, then form
      final String res = exploreHint(newHint, client::findIssf);
      return res == null ? exploreHint(newHint, client::findForm) : res;
    }
    // If a category can't be deduced, try species, then issf, then form
    if (dry) {
      return "species-issf-form: " + hint;
    }
    String res = exploreHint(hint, client::findSpecies);
    if (res == null) {
      res = exploreHint(hint, client::findIssf);
      return res == null ? exploreHint(hint, client::findForm) : res;
    }
    return res;
  }

  private static String exploreVagueHint(EBirdClient client, String hint, boolean dry)
      throws EBirdApiException {
    return dry ? "(uncategorized): " + hint : exploreHint(hint, client::findTaxon);
  }

}
