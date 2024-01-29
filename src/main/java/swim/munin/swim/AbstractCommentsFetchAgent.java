package swim.munin.swim;

import java.util.Optional;
import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.concurrent.TimerRef;
import swim.munin.Utils;
import swim.munin.connect.reddit.RedditClient;
import swim.munin.connect.reddit.Comment;
import swim.munin.filethesebirds.swim.Shared;
import swim.structure.Text;

/**
 * A Web Agent that fetches new comments to some subreddit and routes them for
 * processing by appropriate {@link swim.munin.swim.AbstractSubmissionAgent
 * SubmissionAgents}.
 *
 * <p>By default, this agent does not directly modify a {@code LiveSubmissions}
 * instance. It may <i>read</i> from a {@code LiveSubmissions} instance to help
 * decide whether a comment is unworthy of further processing (e.g. its
 * submission is expired or shelved).
 */
public class AbstractCommentsFetchAgent extends AbstractAgent {

  protected volatile long afterId10 = -1L;
  protected TimerRef fetchTimer;

  protected TimerRef fetchTimer() {
    return this.fetchTimer;
  }

  @SwimLane("preemptCommentsFetch")
  protected CommandLane<Comment> preemptCommentsFetch = commandLane()
      .valueForm(Comment.form())
      .onCommand(this::preemptCommentsFetchOnCommand);

  protected final void preemptCommentsFetchOnCommand(Comment comment) {
    final String caller = "preemptCommentsFetch";
    Logic.trace(this, caller, "Begin onCommand(" + comment + ")");
    Logic.cancelTimer(this.fetchTimer);
    try {
      this.afterId10 = Utils.id36To10(comment.id());
      Logic.debug(this, caller, "Set bookmark comment to " + comment.id() + " (" + comment.submissionId() + ")");
    } catch (Exception e) {
      Logic.warn(this, caller, "Rescheduled timer without modifying afterId10");
      didFail(e);
    }
    if (this.afterId10 > 0) {
      this.fetchTimer = Logic.scheduleRecurringBlocker(this, caller, this::fetchTimer,
          1000L, 60000L, this::fetchTimerAction);
    } else {
      Logic.error(this, caller, "Timer did not fire due to invalid initial conditions");
    }
    Logic.trace(this, caller, "End onCommand()");
  }

  protected void fetchTimerAction() {
    new GatherTask(this).run();
  }

  protected void onIdleResponse() {
    // stub
  }

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
  }

  private static class GatherTask {

    private static final String CALLER_TASK = "[GatherCommentsTask]";
    private static final Text PREEMPT_SUBMISSIONS_FETCH_PAYLOAD = Text.from("preempt");

    private final AbstractCommentsFetchAgent runtime;
    private final long oldBookmarkId10;
    private long newBookmarkId10;

    private GatherTask(AbstractCommentsFetchAgent runtime) {
      this.runtime = runtime;
      this.oldBookmarkId10 = runtime.afterId10;
      this.newBookmarkId10 = this.oldBookmarkId10;
    }

    void run() {
      gatherComments(RedditClient::fetchMaxUndocumentedComments);
      if (this.runtime.afterId10 == this.newBookmarkId10) {
        this.runtime.onIdleResponse();
      }
      this.runtime.afterId10 = this.newBookmarkId10;
    }

    private void gatherComments(RedditClient.Callable<Comment[]> action) {
      Logic.doRedditCallable(this.runtime, CALLER_TASK, "getNewComments", action)
          .flatMap(response -> processBatch(response.essence()))
          .ifPresent(fullname -> gatherComments(client -> client.fetchUndocumentedCommentsAfter(fullname)));
    }

    private Optional<String> processBatch(Comment[] batch) {
      if (batch == null || batch.length == 0) {
        return Optional.empty();
      }
      final long bookmarkCandidate = Utils.id36To10(batch[0].id());
      if (this.newBookmarkId10 == this.oldBookmarkId10
          && bookmarkCandidate > this.oldBookmarkId10) {
        this.newBookmarkId10 = bookmarkCandidate;
      }
      int result = 0;
      for (Comment c : batch) {
        if (processComment(c, result) == 1) {
          return Optional.empty();
        }
      }
      System.out.println("[TRACE] Coalescence#commentsFetch: Processed " + batch.length + " batched comments");
      // TODO: can we safely check if batch.length < 100 here to potentially save an API call?
      return Optional.of("t1_" + batch[batch.length - 1].id());
    }

    private int processComment(Comment c, int state) {
      final long id10 = Utils.id36To10(c.id());
      if (id10 <= oldBookmarkId10) {
        return 1; // Tells caller we're done
      }
      final long subId10 = Utils.id36To10(c.submissionId());
      if (Shared.liveSubmissions().isShelved(subId10)) {
        Logic.debug(this.runtime, CALLER_TASK, "Ignoring comment to shelved submission " + c.submissionId());
      } else if (Shared.liveSubmissions().getActive(subId10) != null) {
        Logic.debug(this.runtime, CALLER_TASK, "Found comment to active submission " + c.submissionId());
        this.runtime.command(submissionNodeUri(c), "addNewComment", Comment.form().mold(c).toValue());
      } else if (helper(Shared.liveSubmissions().getLatest(), subId10, c.submissionId())) {
        Logic.info(this.runtime, CALLER_TASK, "Found comment to brand-new submission " + c.submissionId()
            + ((state == 2) ? "" : ", will preempt SubmissionsFetch"));
        if (state != 2) {
          this.runtime.command("/submissions", "preemptSubmissionsFetch", PREEMPT_SUBMISSIONS_FETCH_PAYLOAD);
        }
        this.runtime.command(submissionNodeUri(c), "addNewComment", Comment.form().mold(c).toValue());
        return 2; // Tells caller we're not done, but have preempted SubmissionsFetch once this iteration
      } else if (helper(Shared.liveSubmissions().getEarliest(), subId10, c.submissionId())) {
        Logic.info(this.runtime, CALLER_TASK, "Found comment to possibly-active submission " + c.submissionId()
            + ((state == 2) ? "" : ", will preempt SubmissionsFetch"));
        if (state != 2) {
          this.runtime.command("/submissions", "preemptSubmissionsFetch", PREEMPT_SUBMISSIONS_FETCH_PAYLOAD);
        }
        this.runtime.command(submissionNodeUri(c), "addNewComment", Comment.form().mold(c).toValue());
        return 2;
      } else {
        Logic.debug(this.runtime, CALLER_TASK, "Ignoring comment to expired submission " + c.submissionId());
      }
      return state;
    }

    private static String submissionNodeUri(Comment c) {
      return "/submission/" + c.submissionId();
    }

    private boolean helper(long lower10, long subId10, String subId36) {
      if (lower10 < 0) {
        Logic.warn(this.runtime, CALLER_TASK, "Empty LiveSubmissions during comment analysis, "
            + "will assume submission " + subId36 + " is expired");
        return false;
      }
      return subId10 > lower10;
    }
  }

}
