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

package swim.munin.filethesebirds.digest.answer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import swim.munin.filethesebirds.digest.Answer;
import swim.munin.filethesebirds.digest.motion.Review;
import swim.munin.filethesebirds.digest.motion.Suggestion;
import swim.recon.Recon;

class MutableAnswer implements Answer {

  private final Set<String> taxa;
  private final Set<String> reviewers;
  private final Set<String> readOnlyTaxa;
  private final Set<String> readOnlyReviewers;

  MutableAnswer() {
    this.taxa = new HashSet<>();
    this.reviewers = new HashSet<>();
    this.readOnlyTaxa = Collections.unmodifiableSet(this.taxa);
    this.readOnlyReviewers = Collections.unmodifiableSet(this.reviewers);
  }

  @Override
  public Set<String> taxa() {
    return this.readOnlyTaxa;
  }

  @Override
  public Set<String> reviewers() {
    return this.readOnlyReviewers;
  }

  private void clearTaxa() {
    this.taxa.clear();
  }

  boolean addAllTaxa(Set<String> taxa) {
    if (taxa == null) {
      return false;
    }
    return this.taxa.addAll(taxa);
  }

  private boolean addReviewer(String reviewer) {
    return reviewer != null && this.reviewers.add(reviewer);
  }

  boolean addAllReviewers(Set<String> reviewers) {
    if (reviewers == null) {
      return false;
    }
    return this.reviewers.addAll(reviewers);
  }

  @Override
  public boolean suggestionIsSignificant(Suggestion suggestion) {
    if (suggestion == null || isReviewed()) {
      return false;
    }
    return !suggestion.overrideTaxa().isEmpty() || !taxa().containsAll(suggestion.plusTaxa());
  }

  @Override
  public Answer suggest(Suggestion suggestion) {
    if (suggestion == null || isReviewed()) {
      return this;
    }
    if (suggestion.overrideTaxa().isEmpty()) {
      addAllTaxa(suggestion.plusTaxa());
    } else {
      clearTaxa();
      addAllTaxa(suggestion.overrideTaxa());
    }
    return this;
  }

  @Override
  public boolean reviewIsSignificant(Review review) {
    if (review == null) {
      return false;
    }
    if (!review.overrideTaxa().isEmpty() || !taxa().containsAll(review.plusTaxa())) {
      return true;
    }
    // effectively-empty review
    return !isReviewed() && !taxa().isEmpty();
  }

  @Override
  public Answer review(Review review) {
    if (review == null) {
      return this;
    }
    if (review.overrideTaxa().isEmpty()) {
      // Don't count reviewers who submit effectively empty reviews, unless
      // they're the first reviewer and there's at least one taxon available
      if (addAllTaxa(review.plusTaxa()) || (!isReviewed() && !taxa().isEmpty())) {
        addReviewer(review.reviewer());
      }
    } else {
      clearTaxa();
      addAllTaxa(review.overrideTaxa());
      addReviewer(review.reviewer());
    }
    return this;
  }

  @Override
  public String toString() {
    return Recon.toString(Forms.forAnswer().mold(this));
  }

}
