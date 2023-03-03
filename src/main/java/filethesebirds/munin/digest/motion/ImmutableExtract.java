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

import filethesebirds.munin.digest.Motion;
import java.util.Collections;
import java.util.Set;

class ImmutableExtract implements Extract {

  private static final ImmutableExtract EMPTY = new ImmutableExtract(ImmutableSuggestion.empty(),
      Collections.emptySet(), Collections.emptySet());

  private final Motion base;
  private final Set<String> hints;
  private final Set<String> vagueHints;

  private ImmutableExtract(Suggestion suggestion, Set<String> hints,
                           Set<String> vagueHints) {
    this.base = suggestion;
    this.hints = (hints == null || hints.isEmpty()) ? Collections.emptySet()
        : Collections.unmodifiableSet(hints);
    this.vagueHints = (vagueHints == null || vagueHints.isEmpty()) ? Collections.emptySet()
        : Collections.unmodifiableSet(vagueHints);
  }

  private ImmutableExtract(Review review, Set<String> hints,
                           Set<String> vagueHints) {
    this.base = review;
    this.hints = (hints == null || hints.isEmpty()) ? Collections.emptySet()
        : Collections.unmodifiableSet(hints);
    this.vagueHints = (vagueHints == null || vagueHints.isEmpty()) ? Collections.emptySet()
        : Collections.unmodifiableSet(vagueHints);
  }

  @Override
  public Motion base() {
    return this.base;
  }

  @Override
  public Set<String> hints() {
    return this.hints;
  }

  @Override
  public Set<String> vagueHints() {
    return this.vagueHints;
  }

  @Override
  public boolean isEmpty() {
    return this == EMPTY;
  }

  // FIXME
  @Override
  public String toString() {
    return "ImmutableExtract{" +
        "base=" + base +
        ", hints=" + hints +
        ", vagueHints=" + vagueHints +
        '}';
  }

  static Extract create(Suggestion suggestion,
                        Set<String> hints, Set<String> vagueHints) {
    if (suggestion == null || suggestion.isEmpty()) {
      if (hints == null || hints.isEmpty()) {
        if (vagueHints == null || vagueHints.isEmpty()) {
          return empty();
        }
      }
    }
    return new ImmutableExtract(suggestion, hints, vagueHints);
  }

  static Extract create(Review review,
                        Set<String> hints, Set<String> vagueHints) {
    if (review == null) {
      return empty();
    }
    return new ImmutableExtract(review, hints, vagueHints);
  }

  static Extract empty() {
    return EMPTY;
  }

}
