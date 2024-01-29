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

package swim.munin.filethesebirds.digest.motion;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import swim.munin.filethesebirds.digest.Forms;
import swim.recon.Recon;
import swim.structure.Attr;
import swim.structure.Form;
import swim.structure.Item;
import swim.structure.Record;
import swim.structure.Value;

class ImmutableSuggestion implements Suggestion {

  private static final ImmutableSuggestion EMPTY = new ImmutableSuggestion(Collections.emptySet(), Collections.emptySet());

  private final Set<String> plusTaxa;
  private final Set<String> overrideTaxa;

  private ImmutableSuggestion(Set<String> plusTaxa, Set<String> overrideTaxa) {
    if (overrideTaxa == null || overrideTaxa.isEmpty()) {
      this.overrideTaxa = Collections.emptySet();
      if (plusTaxa == null || plusTaxa.isEmpty()) {
        this.plusTaxa = Collections.emptySet();
      } else {
        this.plusTaxa = Set.copyOf(plusTaxa);
      }
    } else {
      this.plusTaxa = Collections.emptySet();
      this.overrideTaxa = Set.copyOf(overrideTaxa);
    }
  }

  @Override
  public Set<String> plusTaxa() {
    return this.plusTaxa;
  }

  @Override
  public Set<String> overrideTaxa() {
    return this.overrideTaxa;
  }

  @Override
  public Suggestion additionalTaxa(Set<String> append) {
    if (!overrideTaxa().isEmpty() || append == null || append.isEmpty()
        || plusTaxa().containsAll(append)) {
      return this;
    }
    if (plusTaxa().isEmpty()) {
      return ImmutableSuggestion.plus(append);
    }
    final Set<String> union = new HashSet<>();
    union.addAll(plusTaxa());
    union.addAll(append);
    return ImmutableSuggestion.plus(union);
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

  static ImmutableSuggestion plus(Set<String> taxa) {
    return create(taxa, null);
  }

  static ImmutableSuggestion override(Set<String> taxa) {
    return create(null, taxa);
  }

  static ImmutableSuggestion create(Set<String> plusTaxa, Set<String> overrideTaxa) {
    if ((overrideTaxa == null || overrideTaxa.isEmpty())
        && (plusTaxa == null || plusTaxa.isEmpty())) {
      return empty();
    }
    return new ImmutableSuggestion(plusTaxa, overrideTaxa);
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
      final Record base = Record.create(2).attr(tag());
      if (!object.overrideTaxa().isEmpty()) {
        return base.slot("overrideTaxa", swim.munin.filethesebirds.digest.Forms.forSetString().mold(object.overrideTaxa()).toValue());
      } else {
        return base.slot("plusTaxa", swim.munin.filethesebirds.digest.Forms.forSetString().mold(object.plusTaxa()).toValue());
      }
    }

    @Override
    public Suggestion cast(Item item) {
      if (item == null || !item.isDistinct()) {
        return ImmutableSuggestion.empty();
      }
      try {
        final String tag = ((Attr) item.head()).getKey().stringValue();
        if (tag().equals(tag)) {
          return ImmutableSuggestion.create(swim.munin.filethesebirds.digest.Forms.forSetString().cast(item.get("plusTaxa")),
              Forms.forSetString().cast(item.get("overrideTaxa")));
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
