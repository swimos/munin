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

import filethesebirds.munin.connect.vault.VaultClient;
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
 * A Web Agent that slowly (to satisfy rate limits) comments the possibly
 * in-progress answer for each r/WhatsThisBird submission. This agent must
 * ensure that at most one comment will be left per submission.
 */
public class ThrottledPublishingAgent extends AbstractAgent {

  private static final long PUBLISH_PERIOD_MILLIS = 10L * 1000;

  @SwimLane("answers")
  private JoinValueLane<String, Value> answers = joinValueLane()
      .keyForm(Form.forString())
      .valueForm(Form.forValue())
      .didUpdate((k, n, o) -> {
        if (n == null || !n.isDistinct()) {
          return;
        }
        final Answer nAnswer = Forms.forAnswer().cast(n);
        if (nAnswer == null) {
          return;
        }
        this.publishQueue.put(k, nAnswer);
      });

  @SwimLane("publishedAnswers")
  MapLane<String, Value> publishedAnswers = mapLane();

  @SwimLane("publishQueue")
  private MapLane<String, Answer> publishQueue = mapLane()
      .keyForm(Form.forString())
      .valueForm(Forms.forAnswer())
      .didUpdate((k, n, o) -> {
        if (this.timer == null) {
          resetTimer(); // FIXME: may start things a bit early
        }
      });

  @SwimLane("subscribe")
  private CommandLane<String> subscribe = this.<String>commandLane()
      .onCommand(uri -> {
        this.answers.downlink(uri)
            .nodeUri(uri).laneUri("answer")
            .open();
      });

  @SwimLane("expireSubmission")
  private CommandLane<String> expireSubmission = this.<String>commandLane()
      .onCommand(uri -> {
        final Answer ans = this.publishQueue.remove(uri);
        if (ans != null) {
          System.out.println(nodeUri() + ": expired <" + uri + ", " + ans + "> before agent could publish comment");
        }
        final Value v = this.publishedAnswers.remove(uri);
        final Answer answer = (ans != null) ? ans : Forms.forAnswer().cast(v.get("answer"));
        final String submissionId36 = uri.substring(uri.lastIndexOf("/") + 1);
        this.answers.remove(uri);
        performDuty(() -> {
          try {
            Shared.vaultClient().assignObservations(submissionId36, answer);
          } catch (Exception e) {
            VaultClient.DRY.assignObservations(submissionId36, answer);
          }
        });
      });

  @SwimLane("removeSubmission")
  private CommandLane<String> removeSubmission = this.<String>commandLane()
      .onCommand(uri -> {
        this.publishQueue.remove(uri);
        this.publishedAnswers.remove(uri);
        final String submissionId36 = uri.substring(uri.lastIndexOf("/") + 1);
        this.answers.remove(uri);
        performDuty(() -> {
          try {
            Shared.vaultClient().deleteSubmission(submissionId36);
          } catch (Exception e) {
            VaultClient.DRY.deleteSubmission(submissionId36);
          }
        });
      });

  @SwimLane("addPublisherComment")
  CommandLane<Comment> addPublisherComment = commandLane()
      .valueForm(Comment.form())
      .onCommand(comment -> {
        final Answer answer = Publication.answerFromPublication(comment.body());
        if (!answer.taxa().isEmpty()) {
          this.publishedAnswers.put("/submission/" + comment.submissionId(),
              Record.create(2).slot("id", "t1_" + comment.id())
                  .slot("answer", Forms.forAnswer().mold(Publication.answerFromPublication(comment.body())).toValue()));
        }
      });

  @SwimLane("startTimer")
  CommandLane<Value> startTimer = this.<Value>commandLane()
      .onCommand(v -> {
        if (v.isDistinct()) {
          resetTimer();
        }
      });

  // FIXME: extend DutyFulfillingAgent once fixed upstream
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
          System.out.println(nodeUri() + ": didn't publish something in queue " + entry.getValue());
          this.publishQueue.remove(entry.getKey());
        }
      } else {
        // create a new comment
        performDuty(() -> createCommentDuty(entry));
      }
    }
  }

  private void createCommentDuty(Map.Entry<String, Answer> entry) {
    try {
      final Comment comment = Shared.redditClient().publishAnyComment("t3_"
              + entry.getKey().substring(entry.getKey().lastIndexOf('/') + 1),
          Publication.publicationFromAnswer(entry.getValue())).essence();
      ThrottledPublishingAgent.this.publishedAnswers.put(entry.getKey(),
          Record.create(2).slot("id", "t1_" + comment.id())
              .slot("answer", Forms.forAnswer().mold(entry.getValue()).toValue()));
    } catch (Exception e) {
      new Exception("Failed to publish to " + entry.getKey(), e)
          .printStackTrace();
    }
  }

  private void editCommentDuty(String commentId36, String submissionId36, Answer toPublish) {
    try {
      final Comment comment = Shared.redditClient().publishEditEditusertext(commentId36,
          Publication.publicationFromAnswer(toPublish)).essence();
      System.out.println("edited comment to " + comment);
      ThrottledPublishingAgent.this.publishedAnswers.put(submissionId36,
          Record.create(2).slot("id", "t1_" + comment.id())
              .slot("answer", Forms.forAnswer().mold(toPublish).toValue()));
    } catch (Exception e) {
      new Exception("Failed to edit comment " + commentId36, e)
          .printStackTrace();
    }
  }

  private TimerRef timer;

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

}
