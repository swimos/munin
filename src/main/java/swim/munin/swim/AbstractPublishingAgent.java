package swim.munin.swim;

import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.MapLane;
import swim.concurrent.TimerRef;
import swim.structure.Form;

/**
 * A Web Agent that creates, edits, and deletes Reddit comments from a specified
 * account.
 *
 * <p>The Reddit API documentation mentions a maximum of 100 calls per minute,
 * but there appears to be a separate undocumented, inconsistent limit that only
 * concerns write-type calls (e.g. posting, editing, or deleting comments). In
 * order to address this, a {@code PublishingAgent} does not immediately perform
 * one of the aforementioned actions when requested. It instead places the task
 * in a queue and uses a self-rescheduling {@link swim.concurrent.Timer} to
 * perform one queued task until no tasks remain.
 *
 * @param <T>  the type that quantifies create-comment and edit-comment tasks
 *             executed by this {@code AbstractPublishingAgent}
 */
public abstract class AbstractPublishingAgent<T> extends AbstractAgent {

  private static final long ACTION_PERIOD_MS = 10L * 1000;

  protected TimerRef throttleTimer;

  protected abstract Form<T> publishQueueValueForm();

  /**
   * The collection of answers that must be commented to Reddit by the Publisher
   * account, populated primarily via observed changes to {@code answers}.
   *
   * <p>This lane and {@link #deleteQueue} together form the {@code
   * PublishingAgent}'s work queue.
   */
  @SwimLane("publishQueue")
  protected final MapLane<Long, T> publishQueue = mapLane()
      .keyForm(Form.forLong())
      .valueForm(publishQueueValueForm())
      .didUpdate((k, n, o) -> queueDidUpdate("publishQueue"));

  /**
   * The collection of (commentID, submissionID) pairs identifying Publisher
   * account comments that must be deleted.
   *
   * <p>This lane and {@link #publishQueue} together form the {@code
   * PublishingAgent}'s work queue.
   */
  @SwimLane("deleteQueue")
  protected final MapLane<Long, Long> deleteQueue = this.<Long, Long>mapLane()
      .didUpdate((k, n, o) -> queueDidUpdate("deleteQueue"));

  protected final void queueDidUpdate(String caller) {
    if (this.throttleTimer == null || !this.throttleTimer.isScheduled()) {
      Logic.debug(this, caller, "Will create or recreate throttleTimer");
      resetTimer(caller);
    }
  }

  private void resetTimer(String caller) {
    if (this.throttleTimer != null && this.throttleTimer.cancel()) {
      Logic.info(this, caller, "Canceled throttleTimer");
    }
    Logic.info(this, caller, "Will fire throttleTimer in 5s");
    this.throttleTimer = setTimer(5000L, this::recurringTimerAction);
  }

  private void recurringTimerAction() {
    Logic.trace(this, "throttleTimer", "Tick");
    executeOneThrottledAction();
    if (this.publishQueue.isEmpty() && this.deleteQueue.isEmpty()) {
      Logic.info(this, "throttleTimer", "Idling timer due to lack of tasks");
    } else {
      Logic.debug(this, "throttleTimer", "Rescheduling timer execution for " + ACTION_PERIOD_MS + " ms");
      this.throttleTimer.reschedule(ACTION_PERIOD_MS);
    }
  }

  protected abstract void executeOneThrottledAction();

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
  }

}
