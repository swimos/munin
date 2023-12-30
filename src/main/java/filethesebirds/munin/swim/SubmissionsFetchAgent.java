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
 * A Web Agent that periodically fetches new submissions to r/WhatsThisBird,
 * but may be preempted to execute outside its usual schedule.
 */
public class SubmissionsFetchAgent extends AbstractAgent {

  protected TimerRef fetchTimer;

  protected TimerRef fetchTimer() {
    return this.fetchTimer;
  }

  @SwimLane("preemptFetch")
  protected CommandLane<Value> preemptFetch = this.<Value>commandLane()
      .onCommand(this::preemptFetchOnCommand);

  protected void preemptFetchOnCommand(Value v) {
    SubmissionsFetchAgentLogic.preemptFetchOnCommand(this, v);
  }

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
  }

}
