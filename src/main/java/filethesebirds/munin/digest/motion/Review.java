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

import filethesebirds.munin.digest.Motion;

/**
 * An extensively capable {@link Motion} that is only available to established
 * r/WhatsThisBird reviewers.
 */
public interface Review extends Motion {

  String reviewer();

  /**
   * A minimally-potent view of this {@code Review}.
   *
   * @return  this {@code Motion}, minus any features that require {@code
   * Review} privileges.
   */
  Suggestion toSuggestion();

}
