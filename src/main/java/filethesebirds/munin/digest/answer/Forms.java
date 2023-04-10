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

package filethesebirds.munin.digest.answer;

import filethesebirds.munin.digest.Answer;
import java.util.Set;
import swim.structure.Attr;
import swim.structure.Form;
import swim.structure.Item;
import swim.structure.Record;
import swim.structure.Value;
import static filethesebirds.munin.digest.Forms.forSetString;

public final class Forms {

  private Forms() {
  }

  private static final Form<Answer> ANSWER_FORM = new Form<>() {

    @Override
    public String tag() {
      return "answer";
    }

    @Override
    public Class<?> type() {
      return Answer.class;
    }

    @Override
    public Item mold(Answer object) {
      if (object == null
          || (object.taxa().isEmpty() && object.reviewers().isEmpty())) {
        return Value.extant();
      }
      Record result = Record.create(3).attr(tag());
      if (!object.taxa().isEmpty()) {
        result = result.slot("taxa", forSetString().mold(object.taxa()).toValue());
      }
      if (!object.reviewers().isEmpty()) {
        result = result.slot("reviewers", forSetString().mold(object.reviewers()).toValue());
      }
      return result;
    }

    @Override
    public Answer cast(Item item) {
      if (item == null || !item.isDistinct()) {
        return null;
      }
      try {
        final Attr head = (Attr) item.head();
        final String tag = head.getKey().stringValue();
        if (tag().equals(tag)) {
          final MutableAnswer result = new MutableAnswer(); // FIXME: create immutable variant
          // taxa
          final Item taxaItem = item.get("taxa");
          final Set<String> taxaSet = forSetString().cast(taxaItem);
          result.addAllTaxa(taxaSet);
          // reviewers
          final Item reviewersItem = item.get("reviewers");
          final Set<String> reviewersSet = forSetString().cast(reviewersItem);
          result.addAllReviewers(reviewersSet);
          return result;
        }
      } catch (Exception e) {
        throw new IllegalArgumentException("Uncastable item: " + item, e);
      }
      throw new IllegalArgumentException("Uncastable item: " + item);
    }

  };

  public static Form<Answer> forAnswer() {
    return ANSWER_FORM;
  }

}
