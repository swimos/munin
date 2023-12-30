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

import filethesebirds.munin.connect.reddit.RedditResponse;
import filethesebirds.munin.digest.Answer;
import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.answer.Forms;
import filethesebirds.munin.digest.answer.Publication;
import java.util.Map;
import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.JoinValueLane;
import swim.api.lane.MapLane;
import swim.concurrent.AbstractTask;
import swim.concurrent.TimerRef;
import swim.structure.Form;
import swim.structure.Record;
import swim.structure.Value;

/**
 * A Web Agent that slowly (to satisfy rate limits) comments an in-progress for
 * each live r/WhatsThisBird submission and edits the comment as the answer
 * evolves, deleting the comment only if the submission is explicitly shelved.
 */
public class PublishingAgent extends AbstractAgent {

  protected TimerRef timer;
  protected volatile boolean isCoalescing;

  /**
   * A streaming API to all the "active" submissions' answers, implemented
   * as a join lane over every pertinent {@code SubmissionAgent}.
   */
  @SwimLane("answers")
  protected final JoinValueLane<String, Value> answers = joinValueLane()
      .keyForm(Form.forString())
      .valueForm(Form.forValue())
      .didUpdate(this::answersDidUpdate);

  /**
   * A command-type endpoint through which {@link #answers} opens a
   * subscription to a provided {@code SubmissionAgent}'s answer.
   */
  @SwimLane("subscribe")
  protected final CommandLane<String> subscribe = this.<String>commandLane()
      .onCommand(this::subscribeOnCommand);

  /**
   * The collection of answers that have already been commented to Reddit by the
   * Publisher account (for example, u/FileTheseBirdsBot for the hosted app).
   */
  @SwimLane("publishedAnswers")
  MapLane<String, Value> publishedAnswers = mapLane();

  /**
   * The collection of answers that must be commented to Reddit by the Publisher
   * account, populated primarily via observed changes to {@code answers}.
   */
  @SwimLane("publishQueue")
  protected final MapLane<String, Answer> publishQueue = mapLane()
      .keyForm(Form.forString())
      .valueForm(Forms.forAnswer())
      .didUpdate(this::publishQueueDidUpdate);

  /**
   * The collection of (commentID, submissionID) pairs identifying Publisher
   * account comments that must be deleted due to submission shelving.
   */
  @SwimLane("deleteQueue")
  protected final MapLane<String, String> deleteQueue = this.<String, String>mapLane()
      .didUpdate(this::deleteQueueDidUpdate);

  protected void answersDidUpdate(String k, Value n, Value o) {
    PublishingAgentLogic.answersDidUpdate(this, k, n, o);
  }

  protected void subscribeOnCommand(String uri) {
    PublishingAgentLogic.subscribeOnCommand(this, uri);
  }

  protected void publishQueueDidUpdate(String k, Answer n, Answer o) {
    if (!this.isCoalescing && this.timer == null) {
      resetTimer();
    }
  }

  protected void deleteQueueDidUpdate(String k, String n, String o) {

  }

  @SwimLane("expireSubmission")
  protected final CommandLane<String> expireSubmission = this.<String>commandLane()
      .onCommand(uri -> {
        final Answer ans = this.publishQueue.remove(uri);
        if (ans != null) {
          Logic.error(this, "expireSubmission",
              "<" + uri + ", " + ans + "> was evicted before agent could publish comment");
        }
        final Value v = this.publishedAnswers.remove(uri);
        final Answer answer = (ans != null) ? ans : Forms.forAnswer().cast(v.get("answer"));
        final String submissionId36 = uri.substring(uri.lastIndexOf("/") + 1);
        this.answers.remove(uri);
        Logic.executeOrLogVaultAction(this, "expireSubmission",
            "FIXME",
            "Failed to assign observations",
            c -> c.assignObservations(submissionId36, answer));
      });

  @SwimLane("removeSubmission")
  protected final CommandLane<String> removeSubmission = this.<String>commandLane()
      .onCommand(uri -> {
        this.publishQueue.remove(uri);
        this.publishedAnswers.remove(uri);
        final String submissionId36 = uri.substring(uri.lastIndexOf("/") + 1);
        this.answers.remove(uri);
        Logic.executeOrLogVaultAction(this, "removeSubmission",
            "Will remove submission with ID " + submissionId36 + " from vault",
            "Failed to delete " + submissionId36,
            client -> client.deleteSubmission(submissionId36));
      });

  @SwimLane("addPublisherComment")
  protected final CommandLane<Comment> addPublisherComment = commandLane()
      .valueForm(Comment.form())
      .onCommand(comment -> {
//        final Answer answer = Publication.answerFromPublication(comment.body());
//        if (!answer.taxa().isEmpty()) {
//          final Answer publishedAnswer = Publication.answerFromPublication(comment.body());
//          final String submissionUri = "/submission/" + comment.submissionId();
//          final Value prevPublishedAnswer = this.publishedAnswers.get(submissionUri);
//          if (prevPublishedAnswer instanceof Record) {
//            final String prevPublishedIdStr = prevPublishedAnswer.get("id").stringValue();
//            final long prevPublishedId = Long.parseLong(prevPublishedIdStr.substring(3), 36),
//                commentId = Long.parseLong(comment.id());
//            final Record toPublishId
//            if (prevPublishedId < commentId) {
//              Logic.executeBlocker(this, "addPublisherComment",
//                  () -> Shared.redditClient().removeEditDel("t1_" + comment.id()));
//            } else if (commentId < prevPublishedId) {
//              Logic.executeBlocker(this, "addPublisherComment",
//                  () -> Shared.redditClient().removeEditDel(prevPublishedIdStr));
//            }
//          }
//          this.publishedAnswers.put("/submission/" + comment.submissionId(),
//              Record.create(2)
//                  .slot("id", "t1_" + comment.id())
//                  .slot("answer", Forms.forAnswer().mold(publishedAnswer).toValue()));
//        }
      });

  @SwimLane("startTimer")
  CommandLane<Value> startTimer = this.<Value>commandLane()
      .onCommand(v -> {
        if (v.isDistinct()) {
          resetTimer();
        }
      });

  private void performDuty(Runnable duty) {
    asyncStage().task(new AbstractTask() {

      @Override
      public void runTask() {
        duty.run();
      }

      @Override
      public boolean taskWillBlock() {
        return true;
      }

    }).cue();
  }

  private void publishOne() {
    if (this.publishQueue.isEmpty()) {
      cancelTimer();
      return;
    }
    for (Map.Entry<String, Answer> entry : this.publishQueue.entrySet()) {
      // be EXTRA sure before leaving a comment
      if (this.publishedAnswers.containsKey(entry.getKey())) {
        final Answer v = entry.getValue();
        final Value publishedAnswer = this.publishedAnswers.get(entry.getKey());
        final Answer answer = Forms.forAnswer().cast(publishedAnswer.get("answer"));
        if (!answer.taxa().equals(v.taxa()) || !answer.reviewers().equals(v.reviewers())) {
          // edit an existing comment
          performDuty(() -> editCommentDuty(publishedAnswer.get("id").stringValue(), entry.getKey(), v));
        } else {
          this.publishQueue.remove(entry.getKey());
        }
      } else {
        // create a new comment
        performDuty(() -> createCommentDuty(entry));
      }
    }
  }

  private void createCommentDuty(Map.Entry<String, Answer> entry) {
    final String parent = "t3_" + entry.getKey().substring(entry.getKey().lastIndexOf('/') + 1);
    final String publication = Publication.publicationFromAnswer(entry.getValue());
    Logic.doRedditCallable(this, "timer", "publishComment",
          client -> client.publishAnyComment(parent, publication))
        .ifPresent((RedditResponse<Comment> redditResponse) -> {
          final Comment comment = redditResponse.essence();
          Logic.info(this, "timer", "Published comment " + comment);
          this.publishedAnswers.put(entry.getKey(),
              Record.create(2).slot("id", "t1_" + comment.id())
                  .slot("answer", Forms.forAnswer().mold(entry.getValue()).toValue()));
        });
  }

  private void editCommentDuty(String commentId36, String submissionId36, Answer toPublish) {
    final String publication = Publication.publicationFromAnswer(toPublish);
    Logic.doRedditCallable(this, "timer", "editComment",
          client -> client.publishEditEditusertext(commentId36, publication))
        .ifPresent((RedditResponse<Comment> redditResponse) -> {
          final Comment comment = redditResponse.essence();
          Logic.info(this, "timer", "Edited comment to " + comment);
          this.publishedAnswers.put(submissionId36,
              Record.create(2).slot("id", "t1_" + comment.id())
                  .slot("answer", Forms.forAnswer().mold(toPublish).toValue()));
        });
  }

  private void resetTimer() {
    cancelTimer();
    this.timer = setTimer(5000L, () -> {
      publishOne();
      this.timer.reschedule(PUBLISH_PERIOD_MILLIS);
    });
  }

  private void cancelTimer() {
    if (this.timer != null) {
      this.timer.cancel();
    }
  }

  @Override
  public void didStart() {
    super.didStart();
    this.isCoalescing = false;
  }

}
