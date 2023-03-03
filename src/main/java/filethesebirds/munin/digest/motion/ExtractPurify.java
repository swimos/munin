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
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.text.similarity.LevenshteinDistance;
import swim.json.Json;
import swim.structure.Item;
import swim.structure.Value;

public final class ExtractPurify {

  private ExtractPurify() {
  }

  // FIXME
  private static int disambiguate(String hint, Value payload) {
    final LevenshteinDistance distance = new LevenshteinDistance();
    int min = Integer.MAX_VALUE;
    int bestIdx = 0;
    for (int i = 0; i < payload.length(); i++) {
      final Item item = payload.getItem(i);
      final String commonName = item.get("name").stringValue().split(" - ")[0];
      final int dist = distance.apply(hint, commonName);
      if (dist < min) {
        min = dist;
        bestIdx = i;
      }

    }
    return bestIdx;
  }

  private static Value findSpecies(EBirdClient client, String hint)
      throws EBirdApiException {
    Value payload = Json.parse(client.findSpecies(hint));
    if (payload.length() == 0) {
      payload = Json.parse(client.findSpeciesUK(hint));
      if (payload.length() == 0) {
        payload = Json.parse(client.findSpeciesPH(hint));
      }
    }
    return payload;
  }

  private static Value findTaxon(EBirdClient client, String hint)
      throws EBirdApiException {
    // FIXME: more locales
    return Json.parse(client.findTaxon(hint));
  }

  private static int exploreSpeciesHints(EBirdClient client,
                                         Set<String> hints, Set<String> toAppend,
                                         int exploredHints, int maxHintsToExplore) {
    for (String hint : hints) {
      exploredHints++;
      // limit API calls per comments
      if (exploredHints > maxHintsToExplore) {
        break;
      }
      try {
        Value payload = findSpecies(client, hint);
        if (payload.length() == 0) {
          throw new RuntimeException("Empty eBird response: " + payload);
        }
        if (payload.length() > 1) {
          toAppend.add(payload.getItem(disambiguate(hint, payload)).get("code").stringValue());
        } else {
          toAppend.add(payload.getItem(0).get("code").stringValue());
        }
      } catch (Exception e) {
        new RuntimeException("(logged, not thrown) Failed to process hint " + hint + " (continued processing others)", e)
            .printStackTrace();
      }
    }
    return exploredHints;
  }

  private static int exploreTaxonHints(EBirdClient client,
                                       Set<String> hints, Set<String> toAppend,
                                       int exploredHints, int maxHintsToExplore) {
    for (String hint : hints) {
      exploredHints++;
      // limit API calls per comments
      if (exploredHints > maxHintsToExplore) {
        break;
      }
      try {
        final Value payload = findTaxon(client, hint);
        if (payload.length() == 0) {
          throw new RuntimeException("Empty eBird response: " + payload);
        }
        if (payload.length() > 1) {
          toAppend.add(payload.getItem(disambiguate(hint, payload)).get("code").stringValue());
        } else {
          toAppend.add(payload.getItem(0).get("code").stringValue());
        }
      } catch (Exception e) {
        new RuntimeException("(logged, not thrown) Failed to process hint " + hint + " (continued processing others)", e)
            .printStackTrace();
      }
    }
    return exploredHints;
  }

  public static Motion eBirdPurify(Extract extract, EBirdClient client) {
    final Set<String> toAppend = new HashSet<>();
    final int maxHintsToExplore = 10;
    int exploredHints = 0;
    exploredHints = exploreSpeciesHints(client, extract.hints(), toAppend,
        exploredHints, maxHintsToExplore);
    exploreTaxonHints(client, extract.vagueHints(), toAppend, exploredHints,
        maxHintsToExplore);
    return extract.base().additionalTaxa(toAppend);
  }

}
