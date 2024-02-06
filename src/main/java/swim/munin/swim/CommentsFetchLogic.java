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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import swim.munin.Utils;
import swim.munin.connect.reddit.Comment;
import swim.munin.connect.reddit.RedditClient;
import swim.munin.connect.reddit.Submission;
import swim.munin.filethesebirds.swim.Shared;
import swim.structure.Text;

public final class CommentsFetchLogic {

  public static Comment coalesceComments(long until, RedditClient redditClient,
                                         long boundaryId10, Map<String, Submission> active,
                                         Map<String, List<Comment>> batches, Map<String, Integer> counts,
                                         Map<String, Long> shelved) {
    return coalesceComments(until, redditClient, boundaryId10, active, batches, counts, shelved,
        CommentsFetchLogic::submissionAuthorIsDeleted);
  }

  public static Comment coalesceComments(long until, RedditClient redditClient,
                                         long boundaryId10, Map<String, Submission> active,
                                         Map<String, List<Comment>> batches, Map<String, Integer> counts,
                                         Map<String, Long> shelved, Function<Comment, Boolean> commentIsRemover) {
    final GatherCoalesceTask task = new GatherCoalesceTask(until, redditClient, boundaryId10, active, batches, counts, shelved, commentIsRemover);
    task.run();
    return task.bookmark;
  }

  public static void gatherNewComments(AbstractCommentsFetchAgent runtime) {
    new GatherUntilAgentTask(runtime).run();
  }

  static boolean submissionAuthorIsDeleted(Comment comment) {
    return "[deleted]".equals(comment.submissionAuthor());
  }

  /**
   * A short-lived task that may use multiple consecutive Reddit API calls to
   * gather all comments to a subreddit that are newer than a provided bookmark.
   */
  private static class GatherUntilAgentTask {

    private static final String CALLER_TASK = "[GatherCommentsTask]";
    private static final Text PREEMPT_SUBMISSIONS_FETCH_PAYLOAD = Text.from("preempt");

    private final AbstractCommentsFetchAgent runtime;
    private final long oldBookmarkId10;
    private long newBookmarkId10;

    private GatherUntilAgentTask(AbstractCommentsFetchAgent runtime) {
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
      Logic.doRedditCallable(this.runtime, CALLER_TASK, "getNewComments", runtime.redditClient(), action)
          .flatMap(response -> processBatch(response.essence()))
          .ifPresent(fullname -> gatherComments(client -> client.fetchUndocumentedCommentsAfter(fullname)));
    }

    private Optional<String> processBatch(Comment[] batch) {
      if (batch == null || batch.length == 0) {
        Logic.warn(this.runtime, CALLER_TASK, "Potentially missed some comments due to API restrictions");
        return Optional.empty();
      }
      final long bookmarkCandidate = Utils.id36To10(batch[0].id());
      if (this.newBookmarkId10 == this.oldBookmarkId10
          && bookmarkCandidate > this.oldBookmarkId10) {
        this.newBookmarkId10 = bookmarkCandidate;
      }
      int result = 0;
      for (Comment c : batch) {
        result = processComment(c, result);
        if (result == 1) {
          return Optional.empty();
        }
      }
      Logic.trace(this.runtime, CALLER_TASK, "Processed " + batch.length + " batched comments");
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

  private static class GatherCoalesceTask {

    private final long until;
    private final RedditClient redditClient;
    private final long boundaryId10;
    private final Map<String, Submission> active;
    private final Map<String, List<Comment>> batches;
    private final Map<String, Integer> counts;
    private final Map<String, Long> shelved;
    private final Function<Comment, Boolean> commentIsRemover;
    private Comment bookmark;

    private GatherCoalesceTask(long until, RedditClient redditClient,
                               long boundaryId10, Map<String, Submission> active,
                               Map<String, List<Comment>> batches, Map<String, Integer> counts,
                               Map<String, Long> shelved, Function<Comment, Boolean> commentIsRemover) {
      this.until = until;
      this.redditClient = redditClient;
      this.boundaryId10 = boundaryId10;
      this.active = active;
      this.batches = batches;
      this.counts = counts;
      this.shelved = shelved;
      this.commentIsRemover = commentIsRemover;
      this.bookmark = null;
    }

    void run() {
      gatherComments(RedditClient::fetchMaxUndocumentedComments);
    }

    private void gatherComments(RedditClient.Callable<Comment[]> action) {
      System.out.println("[TRACE] Coalescence#commentsFetch: Issuing fetch request");
      Logic.doRedditCallable("getNewComments", this.redditClient, action)
          .flatMap(response -> processBatch(response.essence()))
          .ifPresent(id36 -> gatherComments(client -> client.fetchUndocumentedCommentsAfter(id36)));
    }

    private Optional<String> processBatch(Comment[] batch) {
      if (batch == null || batch.length == 0) {
        return Optional.empty();
      }
      for (Comment c : batch) {
        if (processComment(c)) {
          return Optional.empty();
        }
      }
      System.out.println("[TRACE] Coalescence#commentsFetch: Processed " + batch.length + " batched comments");
      // TODO: can we safely check if batch.length < 100 here to potentially save an API call?
      return Optional.of("t1_" + batch[batch.length - 1].id());
    }

    private boolean processComment(Comment c) {
      if (c.createdUtc() < this.until) {
        return true;
      }
      updateMaps(c);
      // Bookmark should only be the first ever comment processed
      if (this.bookmark == null) {
        this.bookmark = c;
      }
      return false;
    }

    private void updateMaps(Comment comment) {
      if (!this.active.containsKey(comment.submissionId())) {
        onSubmissionsFetchAbsent(comment);
        return;
      }
      if (this.commentIsRemover.apply(comment)) {
        System.out.println("[INFO] Coalescence#commentsFetch: Shelving submission " + comment.submissionId());
        this.counts.remove(comment.submissionId());
        this.batches.remove(comment.submissionId()).clear();
        this.shelved.put(comment.submissionId(), this.active.remove(comment.submissionId()).createdUtc());
        return;
      }
      final String submissionId = comment.submissionId();
      final List<Comment> batch;
      if (!this.batches.containsKey(submissionId)) {
        batch = new ArrayList<>();
        this.batches.put(submissionId, batch);
      } else {
        // FIXME: note that this will never be reached when boundaryId10 is unused
        batch = this.batches.get(submissionId);
      }
      batch.add(comment);
      final int count = this.counts.get(submissionId);
      this.counts.put(submissionId, count - 1);
    }

    private void onSubmissionsFetchAbsent(Comment comment) {
      if (Utils.id36To10(comment.submissionId()) <= this.boundaryId10) {
        System.out.println("[TRACE] Coalescence#commentsFetch: Saw comment to expired submission " + comment.submissionId());
      } else if (this.commentIsRemover.apply(comment)) {
        System.out.println("[TRACE] Coalescence#commentsFetch: Saw comment to submissionsFetch-absent submission "
            + comment.submissionId() + " that would have been shelved anyway");
      } else {
        // FIXME: add as shelf candidate
        System.out.println("[WARN] Coalescence#commentsFetch: " + comment.submissionId() + " is a shelf candidate, but this logic isn't yet implemented");
      }
    }

  }

}
