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

package swim.munin.filethesebirds.digest;

import java.util.Set;
import swim.structure.Form;

public final class Forms {

  private Forms() {
  }

  public static Form<Set<String>> forSetString() {
    return SET_STRING_FORM;
  }

  public static Form<Answer> forAnswer() {
    return swim.munin.filethesebirds.digest.answer.Forms.forAnswer();
  }

  public static Form<Motion> forMotion() {
    return swim.munin.filethesebirds.digest.motion.Forms.forMotion();
  }

  private static final Form<Set<String>> SET_STRING_FORM = Form.forSet(Form.forString());

}
