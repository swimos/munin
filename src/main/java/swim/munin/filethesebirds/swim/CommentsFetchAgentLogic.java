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

package swim.munin.filethesebirds.swim;

import swim.munin.connect.reddit.Comment;
import swim.munin.connect.reddit.Submission;
import swim.munin.filethesebirds.digest.Users;
import swim.munin.Utils;
import swim.munin.connect.reddit.RedditClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import swim.munin.swim.Logic;
import swim.structure.Text;

final class CommentsFetchAgentLogic {

  private CommentsFetchAgentLogic() {
  }

  static Comment coalesceComments(long until, long boundaryId10, Map<String, Submission> active,
                                  Map<String, List<Comment>> batches, Map<String, Integer> counts,
                                  Map<String, Long> shelved) {
    final GatherCoalesceTask task = new GatherCoalesceTask(until, boundaryId10, active, batches, counts, shelved);
    task.run();
    return task.bookmark;
  }

  static boolean submissionAuthorIsDeleted(Comment comment) {
    return "[deleted]".equals(comment.submissionAuthor());
  }

  static boolean commentIsRemover(Comment comment) {
    return comment.body().startsWith("!rm") && Users.userIsAdmin(comment.author());
  }

  private static class GatherCoalesceTask {

    private final long until;
    private final long boundaryId10;
    private final Map<String, Submission> active;
    private final Map<String, List<Comment>> batches;
    private final Map<String, Integer> counts;
    private final Map<String, Long> shelved;
    private Comment bookmark;

    private GatherCoalesceTask(long until, long boundaryId10, Map<String, Submission> active,
                               Map<String, List<Comment>> batches, Map<String, Integer> counts,
                               Map<String, Long> shelved) {
      this.until = until;
      this.boundaryId10 = boundaryId10;
      this.active = active;
      this.batches = batches;
      this.counts = counts;
      this.shelved = shelved;
      this.bookmark = null;
    }

    void run() {
      gatherComments(RedditClient::fetchMaxUndocumentedComments);
    }

    private void gatherComments(RedditClient.Callable<Comment[]> action) {
      System.out.println("[TRACE] Coalescence#commentsFetch: Issuing fetch request");
      Logic.doRedditCallable("getNewComments", action)
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
        System.out.println("[TRACE] Coalescence#commentsFetch: Saw comment to submissionsFetch-absent submission "
            + comment.submissionId() + " that would have been shelved anyway");
      } else {
        // FIXME: add as shelf candidate
        System.out.println("[WARN] Coalescence#commentsFetch: " + comment.submissionId() + " is a shelf candidate, but this logic isn't yet implemented");
      }
    }

  }

}
