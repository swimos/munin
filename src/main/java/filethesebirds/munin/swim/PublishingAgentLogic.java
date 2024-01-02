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

package filethesebirds.munin.swim;

import filethesebirds.munin.digest.Answer;
import filethesebirds.munin.digest.answer.Forms;
import swim.structure.Value;

final class PublishingAgentLogic {

  // The Reddit API documentation explicitly outlines a maximum of 100 calls per
  // minute, but there appears to be an additional, undocumented limit that
  // affects write-type calls (e.g. posting, editing, or deleting comments).
  // To be safe, we enforce that 10 seconds pass between any such actions.
  private static final long PUBLISH_PERIOD_MILLIS = 10L * 1000;

  private PublishingAgentLogic() {
  }

  /**
   * Enqueues n to agent's publishQueue iff n represents a properly formatted
   * Answer.
   */
  static void answersDidUpdate(PublishingAgent runtime, String k, Value n, Value o) {
    if (n == null || !n.isDistinct()) {
      return;
    }
    final Answer nAnswer = Forms.forAnswer().cast(n);
    if (nAnswer == null) {
      return;
    }
    runtime.publishQueue.put(k, nAnswer);
  }

  static void subscribeOnCommand(PublishingAgent runtime, String uri) {
    Logic.trace(runtime, "subscribe", "Begin onCommand(" + uri + ")");
    if (uri == null || !uri.startsWith("/submission/")) { // FIXME: make constant, more validation
      Logic.warn(runtime, "subscribe", "Odd nodeUri=" + uri + " provided to subscribe, will not downlink");
    } else {
      Logic.info(runtime, "subscribe", "Will downlink to " + uri + "#answer");
      runtime.answers.downlink(uri)
          .nodeUri(uri)
          .laneUri("answer")
          .open();
    }
    Logic.trace(runtime, "subscribe", "End onCommand()");
  }

}
