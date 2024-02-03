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
import swim.api.lane.JoinValueLane;
import swim.concurrent.TimerRef;
import swim.munin.Utils;
import swim.structure.Form;
import swim.structure.Text;
import swim.structure.Value;

/**
 * A singleton Web Agent whose lanes stream insights regarding all active
 * submissions for some subreddit.
 *
 * <p>By default, each {@code AbstractSubmissionsAgent} modifies a {@code
 * LiveSubmissions} instance only by expiring posts that exceed a certain age;
 * it periodically checks for this by using {@link #expiryTimer}.
 */
public abstract class AbstractSubmissionsAgent extends AbstractAgent
    implements MuninAgent {

  private static final long EXPIRY_TICK_PERIOD_MS = 15L * 60 * 1000;

  protected TimerRef expiryTimer;

  /**
   *
   */
  @SwimLane("statuses")
  protected final JoinValueLane<Long, Value> statuses = joinValueLane()
      .keyForm(Form.forLong())
      .valueForm(Form.forValue())
      .didUpdate(this::statusesDidUpdate);

  @SwimLane("subscribe")
  protected final CommandLane<Long> subscribe = this.<Long>commandLane()
      .onCommand(this::subscribeOnCommand);

  @SwimLane("expireSubmission")
  protected final CommandLane<Long> expireSubmission = this.<Long>commandLane()
      .onCommand(this::expireSubmissionOnCommand);

  @SwimLane("shelveSubmission")
  protected final CommandLane<Long> shelveSubmission = this.<Long>commandLane()
      .onCommand(this::shelveSubmissionOnCommand);

  protected void statusesDidUpdate(long k, Value n, Value o) {
    Logic.trace(this, "statuses", "Begin didUpdate(" + k + ", " + n + ", " + o + ")");
    Logic.trace(this, "statuses", "End didUpdate()");
  }

  protected void subscribeOnCommand(long id10) {
    Logic.trace(this, "subscribe", "Begin onCommand(" + id10 + ")");
    if (id10 <= 0) {
      Logic.warn(this, "subscribe", "Will not open downlink to nonpositive id10");
    } else {
      final String nodeUri = "/submission/" + Utils.id10To36(id10);
      Logic.info(this, "subscribe", "Will open downlink to " + nodeUri + "#status");
      this.statuses.downlink(id10)
          .nodeUri(nodeUri)
          .laneUri("status")
          .open();
    }
    Logic.trace(this, "subscribe", "End onCommand(" + id10 + ")");
  }

  protected void expireSubmissionOnCommand(long id10) {
    Logic.trace(this, "expireSubmission", "Begin onCommand(" + id10 + ")");
    this.statuses.remove(id10);
    Logic.trace(this, "expireSubmission", "End onCommand()");
  }

  protected void shelveSubmissionOnCommand(long id10) {
    Logic.trace(this, "removeSubmission", "Begin onCommand(" + id10 + ")");
    this.statuses.remove(id10);
    Logic.trace(this, "removeSubmission", "End onCommand()");
  }

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
    Logic.debug(this, "didStart()", "Scheduling timer tick for " + EXPIRY_TICK_PERIOD_MS + " ms");
    if (this.expiryTimer != null) {
      Logic.debug(this, "didStart()", "Canceling expiryTimer");
      this.expiryTimer.cancel();
      this.expiryTimer = null;
    }
    this.expiryTimer = setTimer(EXPIRY_TICK_PERIOD_MS, () -> {
      Logic.trace(this, "[expiryTimer]", "Tick");
      final long now = System.currentTimeMillis();
      liveSubmissions().expire(this)
          .forEach(id36 -> {
            Logic.debug(this, "[expiryTimer]", "Notifying /submission/" + id36 + " of expiry");
            command("/submission/" + id36, "expire", Text.from("expire"));
          });
      final long delta = now + EXPIRY_TICK_PERIOD_MS - System.currentTimeMillis();
      Logic.debug(this, "[expiryTimer]", "Scheduling timer tick for " + delta + " ms");
      this.expiryTimer.reschedule(Math.max(1000L, delta));
    });
  }

  @Override
  public void willClose() {
    Logic.info(this, "willClose()", "");
    if (this.expiryTimer != null) {
      Logic.debug(this, "willClose()", "Canceling expiryTimer");
      this.expiryTimer.cancel();
      this.expiryTimer = null;
    }
  }

}
