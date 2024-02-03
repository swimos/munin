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
import swim.structure.Value;

/**
 * A singleton Web Agent that fetches new submissions to some subreddit and
 * routes the resulting information for processing by appropriate {@link
 * AbstractSubmissionAgent SubmissionAgents}.
 *
 * <p>By default, the {@code SubmissionsFetchAgent} is liable for the following
 * {@link LiveSubmissions} actions:
 * <ul>
 * <li>Inserting up-to-date information about all active submissions
 * <li>Shelving submitter-deleted and moderator-removed submissions (that the
 * {@link swim.munin.swim.AbstractCommentsFetchAgent CommentsFetchAgent} didn't
 * deduce as such first)
 * </ul>
 */
public abstract class AbstractSubmissionsFetchAgent extends AbstractAgent
    implements MuninAgent {

  protected TimerRef fetchTimer;

  @SwimLane("preemptSubmissionsFetch")
  protected final CommandLane<Value> preemptSubmissionsFetch = this.<Value>commandLane()
      .onCommand(this::preemptSubmissionsFetchOnCommand);

  protected TimerRef fetchTimer() {
    return this.fetchTimer;
  }

  protected void preemptSubmissionsFetchOnCommand(Value v) {
    final String callerLane = "preemptFetch";
    Logic.trace(this, callerLane, "Begin onCommand(" + v + ")");
    this.fetchTimer = Logic.scheduleRecurringBlocker(this, "[GatherSubmissionsTask]",
        this::fetchTimer, 3000L, 180000L, this::fetchTimerAction);
    Logic.trace(this, callerLane, "End onCommand()");
  }

  protected void fetchTimerAction() {
    SubmissionsFetchLogic.doRun(this, environment(), liveSubmissions());
  }

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
  }

}
