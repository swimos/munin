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

package swim.munin.filethesebirds.swim;

import swim.munin.filethesebirds.digest.motion.HintCache;
import swim.munin.swim.AbstractCommentsFetchAgent;
import swim.munin.swim.Logic;

/**
 * A Web Agent that fetches new comments to r/WhatsThisBird and routes them for
 * processing by appropriate {@link SubmissionAgent SubmissionAgents}.
 */
public class CommentsFetchAgent extends AbstractCommentsFetchAgent {

  @Override
  protected void onIdleResponse() {
    // Might as well do something, sometimes, if we expect idleness
    if (Math.random() < .2) {
      Logic.debug(this, "fetchTimer", "Will cue HintCache prune");
      HintCache.prune();
    }
  }

}
