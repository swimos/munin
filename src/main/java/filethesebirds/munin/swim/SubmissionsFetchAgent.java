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

import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.concurrent.TimerRef;
import swim.structure.Value;

/**
 * A singleton Web Agent that fetches new submissions to r/WhatsThisBird and
 * routes the resulting information for processing by appropriate {@link
 * SubmissionAgent SubmissionAgents}.
 *
 * <p>The {@code SubmissionsFetchAgent} is liable for the following {@link
 * LiveSubmissions} actions:
 * <ul>
 * <li>Inserting up-to-date information about all active submissions
 * <li>Shelving submitter-deleted and moderator-removed submissions that have
 * received no comments since their disappearance
 * </ul>
 * and the following vault actions:
 * <ul>
 * <li>Upserting the latest info about all active live submissions
 * <li>Deleting each submission (cascaded to its observations) that moves to the
 * {@code shelved} status from {@code active}
 * </ul>
 */
public class SubmissionsFetchAgent extends AbstractAgent {

  protected TimerRef fetchTimer;

  protected TimerRef fetchTimer() {
    return this.fetchTimer;
  }

  @SwimLane("preemptSubmissionsFetch")
  protected CommandLane<Value> preemptSubmissionsFetch = this.<Value>commandLane()
      .onCommand(this::preemptSubmissionsFetchOnCommand);

  protected void preemptSubmissionsFetchOnCommand(Value v) {
    SubmissionsFetchAgentLogic.preemptSubmissionsFetchOnCommand(this, v);
  }

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
  }

}
