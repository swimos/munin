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

package filethesebirds.munin.digest;

import filethesebirds.munin.digest.motion.Review;
import filethesebirds.munin.digest.motion.Suggestion;
import java.util.Set;

/**
 * A possibly in-progress view of community-identified taxa within an
 * r/WhatsThisBird submission.
 *
 * <p>{@code Answers} begin in an "empty" state that lacks any taxa and
 * reviewers. Invocations of {@link #suggest(Suggestion)} and {@link
 * #review(Review)} update {@code Answers} toward correctness and
 * thoroughness.
 *
 * <p>Already-reviewed {@code Answers} <i>forbid further updates</i> except
 * {@code Reviews} by reviewers; there exist no similar restrictions on {@code
 * Answers} that have solely seen suggestions.
 */
public interface Answer {

  Set<String> taxa();

  Set<String> reviewers();

  Answer suggest(Suggestion suggestion);

  Answer review(Review review);

  boolean suggestionIsSignificant(Suggestion suggestion);

  boolean reviewIsSignificant(Review review);

  default boolean isReviewed() {
    return reviewers() != null && !reviewers().isEmpty();
  }

  default Answer apply(Motion motion) {
    if (motion instanceof Review) {
      return review((Review) motion);
    } else if (motion instanceof Suggestion) {
      return suggest((Suggestion) motion);
    } else {
      throw new IllegalArgumentException("Unexpected motion type: " + motion.getClass());
    }
  }

  default boolean motionIsSignificant(Motion motion) {
    if (motion instanceof Review) {
      return reviewIsSignificant((Review) motion);
    } else if (motion instanceof Suggestion) {
      return suggestionIsSignificant((Suggestion) motion);
    } else {
      throw new IllegalArgumentException("Unexpected motion type: " + motion.getClass());
    }
  }

}
