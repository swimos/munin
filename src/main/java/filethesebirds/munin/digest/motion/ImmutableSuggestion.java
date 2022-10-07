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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import swim.recon.Recon;
import swim.structure.Attr;
import swim.structure.Form;
import swim.structure.Item;
import swim.structure.Record;
import swim.structure.Value;
import static filethesebirds.munin.digest.Forms.forSetString;

class ImmutableSuggestion implements Suggestion {

  private static final ImmutableSuggestion EMPTY = new ImmutableSuggestion(Collections.emptySet());

  private final Set<String> plusTaxa;

  private ImmutableSuggestion(Set<String> plusTaxa) {
    if (plusTaxa == null || plusTaxa.isEmpty()) {
      this.plusTaxa = Collections.emptySet();
    } else {
      this.plusTaxa = Collections.unmodifiableSet(plusTaxa);
    }
  }

  @Override
  public Set<String> plusTaxa() {
    return this.plusTaxa;
  }

  @Override
  public Suggestion additionalTaxa(Set<String> append) {
    if (append == null || append.isEmpty()) {
      return this;
    }
    if (plusTaxa().isEmpty()) {
      return ImmutableSuggestion.create(Collections.unmodifiableSet(append));
    }
    final Set<String> union = new HashSet<>();
    union.addAll(plusTaxa());
    union.addAll(append);
    return ImmutableSuggestion.create(Collections.unmodifiableSet(union));
  }

  @Override
  public String toString() {
    return Recon.toString(form().mold(this));
  }

  static ImmutableSuggestion empty() {
    return EMPTY;
  }

  @Override
  public boolean isEmpty() {
    return this == EMPTY;
  }

  static ImmutableSuggestion create(Set<String> plusTaxa) {
    return (plusTaxa == null || plusTaxa.isEmpty()) ? EMPTY
        : new ImmutableSuggestion(plusTaxa);
  }

  private static final Form<Suggestion> FORM = new Form<>() {

    @Override
    public String tag() {
      return "suggestion";
    }

    @Override
    public Class<?> type() {
      return Suggestion.class;
    }

    @Override
    public Item mold(Suggestion object) {
      if (object == null || object.isEmpty()) {
        return Value.extant();
      }
      return Record.create(2).attr(tag())
          .slot("plusTaxa", forSetString().mold(object.plusTaxa()).toValue());
    }

    @Override
    public Suggestion cast(Item item) {
      if (item == null || !item.isDistinct()) {
        return ImmutableSuggestion.empty();
      }
      try {
        final String tag = ((Attr) item.head()).getKey().stringValue();
        if (tag().equals(tag)) {
          return ImmutableSuggestion.create(forSetString().cast(item.get("plusTaxa")));
        }
      } catch (Exception e) {
        throw new IllegalArgumentException("Uncastable item: " + item, e);
      }
      throw new IllegalArgumentException("Uncastable item: " + item);
    }
  };

  static Form<Suggestion> form() {
    return FORM;
  }

}
