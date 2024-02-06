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

package swim.munin.swim;

import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.concurrent.TimerRef;
import swim.munin.Utils;
import swim.munin.connect.reddit.Comment;

/**
 * A Web Agent that fetches new comments to some subreddit and routes them for
 * processing by appropriate {@link swim.munin.swim.AbstractSubmissionAgent
 * SubmissionAgents}.
 *
 * <p>By default, this agent does not directly modify a {@code LiveSubmissions}
 * instance, but it <i>reads</i> from a {@code LiveSubmissions} instance to help
 * decide whether a comment is unworthy of further processing (e.g. its
 * submission is expired or shelved).
 */
public abstract class AbstractCommentsFetchAgent extends AbstractAgent
    implements MuninAgent {

  protected volatile long afterId10 = -1L;
  protected TimerRef fetchTimer;

  /**
   * A command-type endpoint that by default triggers resets the periodic
   * comment fetch timer and updates the boundary comment identifier.
   */
  @SwimLane("preemptCommentsFetch")
  protected final CommandLane<Comment> preemptCommentsFetch = commandLane()
      .valueForm(Comment.form())
      .onCommand(this::preemptCommentsFetchOnCommand);

  protected TimerRef fetchTimer() {
    return this.fetchTimer;
  }

  protected void preemptCommentsFetchOnCommand(Comment comment) {
    final String caller = "preemptCommentsFetch";
    Logic.trace(this, caller, "Begin onCommand(" + comment + ")");
    Logic.cancelTimer(this.fetchTimer);
    try {
      this.afterId10 = Utils.id36To10(comment.id());
      Logic.debug(this, caller, "Set bookmark comment to " + comment.id() + " ("
          + (comment.submissionId().isEmpty() ? "indeterminateSubId" : comment.submissionId())
          + ")");
    } catch (Exception e) {
      Logic.warn(this, caller, "Rescheduled timer without modifying afterId10");
      didFail(e);
    }
    if (this.afterId10 > 0) {
      this.fetchTimer = Logic.scheduleRecurringBlocker(this, caller, this::fetchTimer,
          1000L, environment().commentsFetchPeriodMillis(), this::fetchTimerAction);
    } else {
      Logic.error(this, caller, "Timer did not fire due to invalid initial conditions");
    }
    Logic.trace(this, caller, "End onCommand()");
  }

  protected void fetchTimerAction() {
    CommentsFetchLogic.gatherNewComments(this);
  }

  protected void onIdleResponse() {
    // stub
  }

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
  }

}
