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

package filethesebirds.munin.digest.motion;

import filethesebirds.munin.digest.Answer;
import filethesebirds.munin.digest.Motion;
import java.util.Set;

/**
 * A Reddit comment's distillation into suggested modifications to some {@link
 * Answer}, possibly including some taxonomy hints. A <i>hint</i> is a String
 * which may be transformed into taxa by eBird's "find species" API.
 *
 * <p><b>Example of a Hint</b>
 *
 * <p> Consider a Reddit comment with body "{@code
 * [Barn Owl](https://www.allaboutbirds.org/guide/Barn_Owl/sounds).}". Exactly
 * one species-level taxon code in the eBird taxonomy matches "Barn Owl", namely
 * "brnowl". If we wish to transform this comment into a {@code Motion} to add
 * a "brnowl" taxon to some {@code Answer}, we must: <ul>
 * <li>Extract "Barn Owl" from either the link text or the link destination
 * <li>Translate this to "barn%20owl", per eBird API requirements
 * <li>Send a GET request to {@code https://api.ebird.org/v2/ref/taxon/find}
 * with URL parameters {@code q=barn%20owl}, {@code cat=species} (plus any
 * authorization requirements)
 * <li>Confirm that the response contains exactly one taxon code
 * </ul>
 *
 * <p>"barn%20owl" is a hint, arguably the most efficient possible hint in this
 * comment.
 *
 * <p>Reddit comments take many forms, which invite a variety of hint
 * extraction heuristics. Note that because API calls are required to transform
 * hints into taxa, it is encouraged to immediately deduce taxon codes and skip
 * hints whenever possible.
 */
public interface Extract {

  Motion base();

  Set<String> hints();

  Set<String> vagueHints();

  Extract purifyHint(String hint, String taxonCode);

  Extract purifyVagueHint(String hint, String taxonCode);

  boolean isEmpty();

  default boolean isImpure() {
    return !hints().isEmpty() || !vagueHints().isEmpty();
  }

}
