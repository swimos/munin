package swim.munin.swim;

import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.JoinValueLane;
import swim.concurrent.TimerRef;
import swim.munin.Utils;
import swim.structure.Form;
import swim.structure.Value;

/**
 * A singleton Web Agent whose lanes stream insights regarding all active
 * submissions for some subreddit.
 */
public class AbstractSubmissionsAgent extends AbstractAgent {

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
  }

}
