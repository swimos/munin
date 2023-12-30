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

import filethesebirds.munin.Utils;
import filethesebirds.munin.connect.reddit.RedditClient;
import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.Submission;
import filethesebirds.munin.digest.Users;
import filethesebirds.munin.digest.motion.HintCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CommentsFetchAgentLogic {

  private CommentsFetchAgentLogic() {
  }

  static void preemptSubmissionsFetchOnCommand(CommentsFetchAgent runtime, Comment comment) {
    final String caller = "startFetching";
    Logic.trace(runtime, caller, "Begin onCommand(" + comment + ")");
    Logic.cancelTimer(runtime.fetchTimer);
    try {
      runtime.afterId10 = Utils.id36To10(comment.id());
      Logic.debug(runtime, caller, "Set bookmark comment to " + comment.id() + " (" + comment.submissionId() + ")");
    } catch (Exception e) {
      Logic.warn(runtime, caller, "Rescheduled timer without modifying afterId10");
      runtime.didFail(e);
    }
    if (runtime.afterId10 > 0) {
      Logic.scheduleRecurringBlocker(runtime, caller, runtime::fetchTimer,
          1000L, 60000L, () -> new GatherAgentTask(runtime).run());
    } else {
      Logic.error(runtime, caller, "Timer could not fire due to invalid initial conditions");
    }
    Logic.trace(runtime, caller, "End onCommand()");
  }

  static long coalesceComments(long until, long boundaryId10, Map<String, Submission> active,
                               Map<String, List<Comment>> batches, Map<String, Integer> counts,
                               Map<String, Long> shelved) {
    final GatherCoalesceTask task = new GatherCoalesceTask(until, boundaryId10, active, batches, counts, shelved);
    task.run();
    return task.oldestCommentTimestamp;
  }

  static boolean submissionAuthorIsDeleted(Comment comment) {
    return "[deleted]".equals(comment.submissionAuthor());
  }

  static boolean commentIsRemover(Comment comment) {
    return comment.body().startsWith("!rm") && Users.userIsAdmin(comment.author());
  }

  private static class GatherAgentTask {

    private final CommentsFetchAgent runtime;
    private final long oldBookmarkId10;
    private long newBookmarkId10;

    private GatherAgentTask(CommentsFetchAgent runtime) {
      this.runtime = runtime;
      this.oldBookmarkId10 = runtime.afterId10;
      this.newBookmarkId10 = this.oldBookmarkId10;
    }

    void run() {
      gatherComments(RedditClient::fetchMaxUndocumentedComments);
      if (this.runtime.afterId10 == this.newBookmarkId10 && Math.random() < .2) {
        Logic.debug(this.runtime, "[GatherTask]", "Will cue HintCache prune");
        HintCache.prune();
      }
      this.runtime.afterId10 = this.newBookmarkId10;
    }

    private void gatherComments(RedditClient.Callable<Comment[]> action) {
      Logic.doRedditCallable(this.runtime, "[GatherTask]", "getNewComments", action)
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
        return 1;
      }
      final long subId10 = Utils.id36To10(c.submissionId());
      if (Shared.liveSubmissions().isShelved(subId10)) {
        Logic.debug(this.runtime, "[GatherTask]", "Ignoring comment to shelved submission " + c.submissionId());
      } else if (Shared.liveSubmissions().getActive(subId10) != null) {
        Logic.debug(this.runtime, "[GatherTask]", "Found comment to active submission " + c.submissionId());
        this.runtime.command(submissionNodeUri(c), "addNewComment", Comment.form().mold(c).toValue());
      } else if (helper(Shared.liveSubmissions().getLatest(), subId10, c.submissionId())) {
        Logic.info(this.runtime, "[GatherTask]", "Found comment to new submission " + c.submissionId()
            + ((state == 2) ? "" : ", will preempt SubmissionsFetch"));
        if (state != 2) {
          this.runtime.command(submissionNodeUri(c), "preemptSubmissionsFetch", );
        }
        return 2;
      } else if (helper(Shared.liveSubmissions().getEarliest(), subId10, c.submissionId())) {
        Logic.info(this.runtime, "[GatherTask]", "Found comment to possibly-active submission " + c.submissionId()
            + ((state == 2) ? "" : ", will preempt SubmissionsFetch"));
        if (state != 2) {
          this.runtime.command(submissionNodeUri(c), "preemptSubmissionsFetch", );
        }
        return 2;
      } else {
        Logic.debug(this.runtime, "[GatherTask]", "Ignoring comment to expired submission " + c.submissionId());
      }
      return state;
    }

    private static String submissionNodeUri(Comment c) {
      return "/submission/" + c.submissionId();
    }

    private boolean helper(long lower10, long subId10, String subId36) {
      if (lower10 < 0) {
        Logic.warn(this.runtime, "[GatherTask]", "Saw zero active/shelved submissions during comment analysis, "
            + "will assume submission " + subId36 + " is expired");
        return false;
      }
      return subId10 > lower10;
    }

  }

  private static class GatherCoalesceTask {

    private final long until;
    private final long boundaryId10;
    private final Map<String, Submission> active;
    private final Map<String, List<Comment>> batches;
    private final Map<String, Integer> counts;
    private final Map<String, Long> shelved;
    private long oldestCommentTimestamp;

    private GatherCoalesceTask(long until, long boundaryId10, Map<String, Submission> active,
                               Map<String, List<Comment>> batches, Map<String, Integer> counts,
                               Map<String, Long> shelved) {
      this.until = until;
      this.boundaryId10 = boundaryId10;
      this.active = active;
      this.batches = batches;
      this.counts = counts;
      this.shelved = shelved;
      this.oldestCommentTimestamp = Long.MAX_VALUE;
    }

    void run() {
      gatherComments(RedditClient::fetchMaxUndocumentedComments);
    }

    private void gatherComments(RedditClient.Callable<Comment[]> action) {
      System.out.println("[TRACE] Coalescence#commentsFetch: Issuing fetch request");
      Logic.coalesceRedditCallable("getNewComments", action)
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
      this.oldestCommentTimestamp = Math.min(c.createdUtc(), this.oldestCommentTimestamp);
      return false;
    }

    private void updateMaps(Comment comment) {
      if (!this.active.containsKey(comment.submissionId())) {
        onSubmissionsFetchAbsent(comment);
        return;
      }
      if (commentIsRemover(comment) || submissionAuthorIsDeleted(comment)) {
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
      } else if (commentIsRemover(comment) || submissionAuthorIsDeleted(comment)) {
        System.out.println("[TRACE] Coalescence#commentsFetch: Saw comment to submissionsFetch-absent post that would have been shelved anyway");
      } else {
        // FIXME: add as shelf candidate
        System.out.println("[WARN] Coalescence#commentsFetch: " + comment.submissionId() + " is a shelf candidate, but this logic isn't yet implemented");
      }
    }

  }

}
