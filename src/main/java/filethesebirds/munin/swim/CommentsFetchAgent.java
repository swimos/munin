// Copyright 2015-2022 Swim.inc
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

import filethesebirds.munin.connect.http.HttpConnectException;
import filethesebirds.munin.connect.reddit.RedditApiException;
import filethesebirds.munin.connect.reddit.RedditResponse;
import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.Submission;
import filethesebirds.munin.digest.Users;
import java.util.Map;
import swim.adapter.common.RelayException;
import swim.adapter.common.ingress.IngestingAgent;
import swim.api.SwimLane;
import swim.api.SwimTransient;
import swim.api.lane.CommandLane;
import swim.api.lane.JoinValueLane;
import swim.api.ref.WarpRef;
import swim.structure.Form;
import swim.structure.Text;

/**
 * A Web Agent that fetches all comments to r/WhatsThisBird posted after a
 * specified "bookmark" comment.
 *
 * <p>This implementation strives to minimize required bandwidth. The basic idea
 * is to hit Reddit's {@code r/whatsthisbird/comments} endpoint with a
 * constantly-updating {@code before} parameter. Beware a behavior of Reddit
 * listings (and many other keyset pagination implementations): if the endpoint
 * is supplied a {@code before} parameter that corresponds to a deleted comment,
 * then the returned listing will be empty. We work around this by periodically
 * running a query that loses the {@code before} parameter if we don't see an
 * update for some time.
 *
 * <p>Unfortunately, this means that the worst-case latency between posting a
 * comment to Reddit and having this agent fetch it (Reddit-internal eventual
 * consistency issues aside) becomes the aforementioned "some time", not just
 * our poll interval. If you deem this latency to be a bigger problem than
 * bandwidth, then consider using a prodigal variation that never uses {@code
 * before}, as the implementation becomes <i>much</i> cleaner (as with {@link
 * filethesebirds.munin.swim.SubmissionsFetchAgent}).
 */
public class CommentsFetchAgent extends IngestingAgent<RedditResponse<Comment[]>> {

  private static final long FETCH_PERIOD_MILLIS = 60L * 1000;
  private static final long LAVISH_TIMEOUT_MILLIS = 5L * 60 * 1000;
  private static final long EXPIRE_PERIOD_MILLIS = 15L * 60 * 1000;

  private volatile String before = null;
  private volatile long beforeTimestamp = -1L;
  private volatile long softBeforeTimestamp = -1L;

  private volatile long latestSubmission = -1L;
  private volatile long oldestSubmission = -1L;

  @SwimTransient
  @SwimLane("liveSubmissions")
  private JoinValueLane<Long, Submission> liveSubmissions = joinValueLane()
      .keyForm(Form.forLong())
      .valueForm(Submission.form());

  @SwimLane("addLiveSubmission")
  private CommandLane<String> addLiveSubmission = this.<String>commandLane()
      .onCommand(id -> {
        final long asLong = Long.parseLong(id, 36);
        this.liveSubmissions.downlink(asLong)
            .nodeUri("/submission/" + id)
            .laneUri("info")
            .open();
        if (asLong > this.latestSubmission) {
          this.latestSubmission = asLong;
          System.out.println("latestSubmission updated to: " + this.latestSubmission);
        }
        if (this.oldestSubmission < 0 || asLong < this.oldestSubmission) {
          this.oldestSubmission = asLong;
          System.out.println("oldestSubmission updated to: " + this.oldestSubmission);
        }
      });

  @Override
  public RedditResponse<Comment[]> fetch() throws RelayException {
    try {
      if (this.before == null) {
        return Shared.redditClient().fetchOneUndocumentedComment();
      } else {
        final long now = System.currentTimeMillis();
        if (now - this.softBeforeTimestamp < LAVISH_TIMEOUT_MILLIS) {
          // Usually, this will return all comments we haven't already seen, but
          // "before" parameter will be invalid if the most recent comment from
          // the last fetch was deleted...
          return Shared.redditClient().fetchUndocumentedCommentsBefore("t1_" + this.before);
        } else {
          // ...so fetch lavishly after periods of inactivity
          return Shared.redditClient().fetchMaxUndocumentedComments();
        }
      }
    } catch (RedditApiException | HttpConnectException e) {
      throw new RelayException("Failed to fetch", e, false);
    }
  }

  @Override
  public void prepareForReception() {
    schedulePeriodicDuty(this::fetchThenRelay, 5000L, FETCH_PERIOD_MILLIS);
  }

  @Override
  public void relayReceiptToSwim(WarpRef warpRef, RedditResponse<Comment[]> r)
      throws RelayException {
    final Comment[] comments;
    try {
      comments = r.essence();
    } catch (Throwable e) {
      throw new RelayException("Failed to relay", e, false);
    }
    if (sawNewComments(comments)) {
      onNewComments(comments);
    }
  }

  private boolean sawNewComments(Comment[] comments) {
    if (comments.length == 0) {
      System.out.println("Fetched 0 comments");
      return false;
    }
    // Lavish fetch issued, but "bookmark" was not deleted, and no new comments
    // were posted
    if (comments[0].id().equals(this.before)) {
      System.out.println("Foolishly lavishly fetched");
      this.softBeforeTimestamp = System.currentTimeMillis();
      return false;
    }
    // Lavish fetch issued, bookmark was deleted, no new comments were posted
    final long latestTimestampMillis = comments[0].createdUtc() * 1000L;
    if (latestTimestampMillis < this.beforeTimestamp) {
      System.out.println("Lavishly fetched, bookmark deleted, nothing new");
      this.before = comments[0].id();
      this.beforeTimestamp = latestTimestampMillis;
      this.softBeforeTimestamp = System.currentTimeMillis();
      return false;
    }
    return true;
  }

  private void onNewComments(Comment[] comments) {
    // Either lavish or basic fetch issued, bookmark possibly deleted, new
    // comments were posted
    final long latestTimestampMillis = comments[0].createdUtc() * 1000L;
    System.out.println("updated 'before' state");
    final String oldBefore = this.before;
    final long oldBeforeTimestamp = this.beforeTimestamp;
    this.before = comments[0].id();
    this.beforeTimestamp = latestTimestampMillis;
    this.softBeforeTimestamp = this.beforeTimestamp;
    for (int i = 0; i < comments.length; i++) {
      if ("[deleted]".equals(comments[i].submissionAuthor())) {
        command("/submission/" + comments[i].submissionId(), "expire",
            Text.from("expire"));
        this.liveSubmissions.remove(Long.parseLong(comments[i].submissionId(), 36));
        continue;
      }
      if (Users.userIsNonparticipant(comments[i].author())) {
        continue;
      }
      if (comments[i].body().startsWith("!rm") && Users.userIsAdmin(comments[i].author())) {
        command("/submission/" + comments[i].submissionId(), "expire",
            Text.from("expire"));
        this.liveSubmissions.remove(Long.parseLong(comments[i].submissionId(), 36));
        continue;
      }
      if (comments[i].id().equals(oldBefore) // found bookmark
          || comments[i].createdUtc() * 1000L < oldBeforeTimestamp) { // or older
        return;
      }
      final String subId36 = comments[i].submissionId();
      final long subId10 = Long.parseLong(subId36, 36);
      System.out.println("New comment for submission " + subId36 + ": " + comments[i].id());
      if (this.liveSubmissions.containsKey(subId10)) {
        command("/submission/" + comments[i].submissionId(), "addNewComment",
            Comment.form().mold(comments[i]).toValue());
      } else if (subId10 > this.oldestSubmission) {
        System.out.println("Found comment for a new post within window, preempting fetch: " + subId36);
        command("/submissionsFetch", "preemptFetch", Text.from("preempt"));
        command("/submission/" + comments[i].submissionId(), "addNewComment",
            Comment.form().mold(comments[i]).toValue());
      } else {
        System.out.println("Comment received for expired post: " + comments[i]);
      }
    }
  }

  @SwimLane("startFetching")
  private CommandLane<Comment> startFetching = commandLane()
      .valueForm(Comment.form())
      .onCommand(comment -> {
        cancelPeriodicDuty(null);
        this.beforeTimestamp = comment.createdUtc() * 1000;
        this.softBeforeTimestamp = this.beforeTimestamp;
        this.before = comment.id();
        prepareForReception();
      });

  @Override
  public void didStart() {
    System.out.println(nodeUri() + ": didStart");
    cancelPeriodicAtomicDuty(this.atomicRecurrentHandle);
    schedulePeriodicAtomicDuty(() -> {
      final long now = System.currentTimeMillis();
      long lastExpired = -1;
      for (Map.Entry<Long, Submission> entry : liveSubmissions) {
        final Submission info = entry.getValue();
        if (info == null) {
          System.out.println("curious null: " + entry.getKey());
          continue;
        }
        if (now - entry.getValue().createdUtc() * 1000 > MuninConstants.lookbackMillis()) {
          lastExpired = Math.max(lastExpired, Long.parseLong(entry.getValue().id(), 36));
          System.out.println("Will expire submission " + entry.getValue());
          command("/submission/" + entry.getValue().id(), "expire",
              Text.from("expire"));
          this.liveSubmissions.remove(entry.getKey());
        }
      }
      if (lastExpired > 0) {
        if (this.liveSubmissions.isEmpty()) {
          this.oldestSubmission = -1L;
          this.latestSubmission = -1L;
        } else {
          for (Long key : this.liveSubmissions.keySet()) {
            // FIXME: might not need this, depending on when the remove takes
            if (key > lastExpired) {
              this.oldestSubmission = key;
              return;
            }
          }
        }
      }
    }, 1000L, EXPIRE_PERIOD_MILLIS);
  }

}
