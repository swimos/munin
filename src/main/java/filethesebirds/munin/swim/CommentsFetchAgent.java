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

import filethesebirds.munin.digest.Comment;
import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.concurrent.TimerRef;

/**
 * A Web Agent that fetches new comments to r/WhatsThisBird and routes them for
 * processing by appropriate {@link SubmissionAgent SubmissionAgents}.
 *
 * <p>This agent does not directly modify vault or a {@code LiveSubmissions}
 * instance.
 */
public class CommentsFetchAgent extends AbstractAgent {

  protected volatile long afterId10 = -1L;
  protected TimerRef fetchTimer;

  protected TimerRef fetchTimer() {
    return this.fetchTimer;
  }

  @SwimLane("preemptCommentsFetch")
  protected CommandLane<Comment> preemptCommentsFetch = commandLane()
      .valueForm(Comment.form())
      .onCommand(this::preemptCommentsFetchOnCommand);

  protected void preemptCommentsFetchOnCommand(Comment comment) {
    CommentsFetchAgentLogic.preemptCommentsFetchOnCommand(this, comment);
  }

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
  }

}
