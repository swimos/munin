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

import swim.munin.filethesebirds.digest.Motion;
import swim.structure.Attr;
import swim.structure.Form;
import swim.structure.Item;

public final class Forms {

  private Forms() {
  }

  public static Form<Suggestion> forSuggestion() {
    return ImmutableSuggestion.form();
  }

  public static Form<Review> forReview() {
    return ImmutableReview.form();
  }

  public static Form<Motion> forMotion() {
    return MOTION_FORM;
  }

  private static final Form<Motion> MOTION_FORM = new Form<>() {

    @Override
    public Class<?> type() {
      return Review.class;
    }

    @Override
    public Item mold(Motion object) {
      if (object instanceof Review) {
        return Forms.forReview().mold((Review) object);
      } else if (object instanceof Suggestion) {
        return Forms.forSuggestion().mold((Suggestion) object);
      } else {
        throw new IllegalArgumentException("Unexpected Motion type: " + object.getClass());
      }
    }

    @Override
    public Motion cast(Item item) {
      if (item == null || !item.isDistinct()) {
        return ImmutableSuggestion.empty();
      }
      try {
        final String tag = ((Attr) item.head()).getKey().stringValue();
        if (Forms.forSuggestion().tag().equals(tag)) {
          return Forms.forSuggestion().cast(item);
        } else if (Forms.forReview().tag().equals(tag)) {
          return Forms.forReview().cast(item);
        }
      } catch (Exception e) {
        throw new IllegalArgumentException("Uncastable item: " + item, e);
      }
      throw new IllegalArgumentException("Uncastable item: " + item);
    }
  };

}
