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

package swim.munin.primitive.swim;

import java.util.Optional;
import swim.munin.MuninEnvironment;
import swim.munin.Utils;
import swim.munin.connect.reddit.Comment;
import swim.munin.connect.reddit.RedditClient;
import swim.munin.swim.AbstractCommentsFetchAgent;
import swim.munin.swim.LiveSubmissions;
import swim.munin.swim.Logic;

public class CommentsFetchAgent extends AbstractCommentsFetchAgent {

  @Override
  public MuninEnvironment environment() {
    return Shared.muninEnvironment();
  }

  @Override
  public LiveSubmissions liveSubmissions() {
    return Shared.liveSubmissions();
  }

  @Override
  public RedditClient redditClient() {
    return Shared.redditClient();
  }

  @Override
  protected void preemptCommentsFetchOnCommand(Comment comment) {
    // No-op, recurring fetch instead triggered by didStart()
  }

  @Override
  protected void fetchTimerAction() {
    new GatherNewTask().run();
  }

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
    setTimer(1000L, () -> Logic.executeBlocker(this, "[didStartTimer]", () -> {
      this.afterId10 = new GatherAllTask().run();
      this.fetchTimer = Logic.scheduleRecurringBlocker(this, "[GatherCommentsTask]",
          this::fetchTimer, 1000L, environment().commentsFetchPeriodMillis(), this::fetchTimerAction);
    }));
  }

  private class GatherAllTask {

    private static final String CALLER_TASK = "[GatherCommentsTask]";

    private long newBookmarkId10;
    private boolean batchIsFirst;

    private GatherAllTask() {
      this.newBookmarkId10 = -1L;
      this.batchIsFirst = true;
    }

    long run() {
      gatherComments(RedditClient::fetchMaxUndocumentedComments);
      return this.newBookmarkId10;
    }

    private void gatherComments(RedditClient.Callable<Comment[]> action) {
      Logic.doRedditCallable(CommentsFetchAgent.this, CALLER_TASK, "getNewComments", CommentsFetchAgent.this.redditClient(), action)
          .flatMap(response -> processBatch(response.essence()))
          .ifPresent(fullname -> gatherComments(client -> client.fetchUndocumentedCommentsAfter(fullname)));
    }

    private Optional<String> processBatch(Comment[] batch) {
      if (batch == null || batch.length == 0) {
        Logic.warn(CommentsFetchAgent.this, CALLER_TASK, "Potentially missed some comments due to API restrictions or deleted pagination comment");
        return Optional.empty();
      }
      if (this.batchIsFirst) {
        this.newBookmarkId10 = Utils.id36To10(batch[0].id());
        this.batchIsFirst = false;
      }
      for (Comment c : batch) {
        processComment(c);
      }
      Logic.trace(CommentsFetchAgent.this, CALLER_TASK, "Processed " + batch.length + " batched comments");
      return Optional.of("t1_" + batch[batch.length - 1].id());
    }

    private void processComment(Comment c) {
      final long subId10 = Utils.id36To10(c.submissionId());
      if (Shared.liveSubmissions().isShelved(subId10)) {
        Logic.debug(CommentsFetchAgent.this, CALLER_TASK, "Ignoring comment to shelved submission " + c.submissionId());
      } else {
        CommentsFetchAgent.this.command(submissionNodeUri(c), "addNewComment", Comment.form().mold(c).toValue());
      }
    }

    private String submissionNodeUri(Comment c) {
      return "/submission/" + c.submissionId();
    }

  }

  private class GatherNewTask {

    private static final String CALLER_TASK = "[GatherCommentsTask]";

    private final long oldBookmarkId10;
    private long newBookmarkId10;

    private GatherNewTask() {
      this.oldBookmarkId10 = CommentsFetchAgent.this.afterId10;
      this.newBookmarkId10 = -1L;
    }

    void run() {
      gatherComments(RedditClient::fetchMaxUndocumentedComments);
    }

    private void gatherComments(RedditClient.Callable<Comment[]> action) {
      Logic.doRedditCallable(CommentsFetchAgent.this, CALLER_TASK, "getNewComments", CommentsFetchAgent.this.redditClient(), action)
          .flatMap(response -> processBatch(response.essence()))
          .ifPresent(fullname -> gatherComments(client -> client.fetchUndocumentedCommentsAfter(fullname)));
    }

    private Optional<String> processBatch(Comment[] batch) {
      if (batch == null || batch.length == 0) {
        Logic.warn(CommentsFetchAgent.this, CALLER_TASK, "Potentially missed some comments due to API restrictions or deleted pagination comment");
        return Optional.empty();
      }
      final long bookmarkCandidate = Utils.id36To10(batch[0].id());
      if (this.newBookmarkId10 == this.oldBookmarkId10
          && bookmarkCandidate > this.oldBookmarkId10) {
        this.newBookmarkId10 = bookmarkCandidate;
      }
      for (Comment c : batch) {
        if (processComment(c)) {
          return Optional.empty();
        }
      }
      Logic.trace(CommentsFetchAgent.this, CALLER_TASK, "Processed " + batch.length + " batched comments");
      return Optional.of("t1_" + batch[batch.length - 1].id());
    }

    private boolean processComment(Comment c) {
      final long subId10 = Utils.id36To10(c.submissionId());
      final long id10 = Utils.id36To10(c.id());
      if (id10 <= oldBookmarkId10) {
        return true;
      }
      if (Shared.liveSubmissions().isShelved(subId10)) {
        Logic.debug(CommentsFetchAgent.this, CALLER_TASK, "Ignoring comment to shelved submission " + c.submissionId());
      } else {
        CommentsFetchAgent.this.command(submissionNodeUri(c), "addNewComment", Comment.form().mold(c).toValue());
      }
      return false;
    }

    private String submissionNodeUri(Comment c) {
      return "/submission/" + c.submissionId();
    }
  }

}
