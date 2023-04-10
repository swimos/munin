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

import filethesebirds.munin.digest.Users;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import swim.recon.Recon;
import swim.structure.Attr;
import swim.structure.Form;
import swim.structure.Item;
import swim.structure.Record;
import swim.structure.Value;
import static filethesebirds.munin.digest.Forms.forSetString;

class ImmutableReview implements Review {

  private static final ImmutableReview FALLBACK = ImmutableReview.override("--", Collections.emptySet());

  private final Suggestion suggestion;
  private final String reviewer;

  private ImmutableReview(String reviewer, Set<String> plusTaxa,
                          Set<String> overrideTaxa) {
    final String lower = reviewer.toLowerCase(Locale.ROOT);
    if (!Users.isValidUsername(lower)) {
      throw new IllegalArgumentException("Invalid Reddit user: " + lower);
    }
    this.reviewer = lower;
    this.suggestion = ImmutableSuggestion.create(plusTaxa, overrideTaxa);
  }

  @Override
  public Suggestion toSuggestion() {
    return this.suggestion;
  }

  @Override
  public Set<String> plusTaxa() {
    return toSuggestion().plusTaxa();
  }

  @Override
  public Review additionalTaxa(Set<String> append) {
    if (!overrideTaxa().isEmpty()) {
      return this;
    }
    if (append == null || append.isEmpty()) {
      return this;
    }
    if (plusTaxa().isEmpty()) {
      return ImmutableReview.plus(reviewer(), Collections.unmodifiableSet(append));
    }
    final Set<String> union = new HashSet<>();
    union.addAll(plusTaxa());
    union.addAll(append);
    return ImmutableReview.plus(reviewer(), Collections.unmodifiableSet(union));
  }

  @Override
  public Set<String> overrideTaxa() {
    return toSuggestion().overrideTaxa();
  }

  @Override
  public String reviewer() {
    return this.reviewer;
  }

  @Override
  public boolean isEmpty() {
    return toSuggestion().isEmpty() && overrideTaxa().isEmpty();
  }

  @Override
  public String toString() {
    return Recon.toString(form().mold(this));
  }

  static ImmutableReview plus(String reviewer, Set<String> taxa) {
    return new ImmutableReview(reviewer, taxa, Collections.emptySet());
  }

  static ImmutableReview override(String reviewer, Set<String> taxa) {
    return new ImmutableReview(reviewer, Collections.emptySet(), taxa);
  }

  static ImmutableReview empty(String reviewer) {
    return override(reviewer, null);
  }

  static ImmutableReview fallback() {
    return FALLBACK;
  }

  private static final Form<Review> FORM = new Form<>() {

    @Override
    public String tag() {
      return "review";
    }

    @Override
    public Class<?> type() {
      return Review.class;
    }

    @Override
    public Item mold(Review object) {
      if (object == null || object == fallback()) {
        return Value.extant();
      }
      if (object.isEmpty()) {
        return Record.create(1).attr(tag(), object.reviewer());
      }
      final Record base = Record.create(2).attr(tag(), object.reviewer());
      if (!object.overrideTaxa().isEmpty()) {
        return base.slot("overrideTaxa", forSetString().mold(object.overrideTaxa()).toValue());
      } else {
        return base.slot("plusTaxa", forSetString().mold(object.plusTaxa()).toValue());
      }
    }

    @Override
    public Review cast(Item item) {
      if (item == null || !item.isDistinct()) {
        return null;
      }
      try {
        final Attr head = (Attr) item.head();
        final String tag = head.getKey().stringValue(),
            reviewer = head.getValue().stringValue();
        if (tag().equals(tag)) {
          // override variant
          final Item overrideItem = item.get("overrideTaxa");
          final Set<String> overrideSet = forSetString().cast(overrideItem);
          if (overrideSet != null && !overrideSet.isEmpty()) {
            return ImmutableReview.override(reviewer, overrideSet);
          }
          // plus variant
          final Item plusItem = item.get("plusTaxa");
          final Set<String> plusSet = forSetString().cast(plusItem);
          if (plusSet != null && !plusSet.isEmpty()) {
            return ImmutableReview.plus(reviewer, plusSet);
          }
          // empty variant
          return ImmutableReview.empty(reviewer);
        }
      } catch (Exception e) {
        throw new IllegalArgumentException("Uncastable item: " + item, e);
      }
      throw new IllegalArgumentException("Uncastable item: " + item);
    }
  };

  static Form<Review> form() {
    return FORM;
  }

}
