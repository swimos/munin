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
import filethesebirds.munin.connect.reddit.RedditResponse;
import filethesebirds.munin.digest.Answer;
import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.answer.Forms;
import filethesebirds.munin.digest.answer.Publication;
import java.util.Map;
import java.util.function.Consumer;
import swim.structure.Attr;
import swim.structure.Item;
import swim.structure.Record;
import swim.structure.Value;

final class PublishingAgentLogic {

  private static final long PUBLISH_PERIOD_MS = 10L * 1000;

  private PublishingAgentLogic() {
  }

  /**
   * Enqueues n to agent's publishQueue iff n represents a properly formatted
   * Answer.
   */
  static void answersDidUpdate(PublishingAgent runtime, long k, Value n, Value o) {
    Logic.trace(runtime, "answers", "Begin didUpdate(" + k + ", " + n + ", " + o + ")");
    if (n == null || !n.isDistinct()) {
      Logic.debug(runtime, "answers", "Ignoring downlink update with empty value");
      Logic.trace(runtime, "answers", "End didUpdate()");
      return;
    }
    final Answer nAnswer = Forms.forAnswer().cast(n);
    if (nAnswer == null) {
      Logic.debug(runtime, "answers", "Ignoring downlink update with empty answer");
      Logic.trace(runtime, "answers", "End didUpdate()");
      return;
    }
    Logic.debug(runtime, "answers", "Will add entry <" + k + ", " + nAnswer + "> to publishQueue");
    runtime.publishQueue.put(k, nAnswer);
    Logic.trace(runtime, "answers", "End didUpdate()");
  }

  static void subscribeOnCommand(PublishingAgent runtime, long subId10) {
    if (subId10 <= 0) {
      Logic.warn(runtime, "subscribe", "Will not open downlink to nonpositive id10");
    } else {
      final String nodeUri = "/submission/" + Utils.id10To36(subId10);
      Logic.info(runtime, "subscribe", "Will open downlink to " + nodeUri + "#answer");
      runtime.answers.downlink(subId10)
          .nodeUri(nodeUri)
          .laneUri("answer")
          .open();
    }
    Logic.trace(runtime, "subscribe", "End onCommand()");
  }

  static void queueDidUpdate(PublishingAgent runtime, String caller) {
    if (runtime.throttleTimer == null || !runtime.throttleTimer.isScheduled()) {
      Logic.debug(runtime, caller, "Will create or recreate throttleTimer");
      resetTimer(runtime, caller);
    }
  }

  private static void resetTimer(PublishingAgent runtime, String caller) {
    cancelTimer(runtime, caller);
    Logic.info(runtime, caller, "Will fire throttleTimer in 5s");
    runtime.throttleTimer = runtime.setTimer(5000L, () -> recurringTimerAction(runtime));
  }

  private static void cancelTimer(PublishingAgent runtime, String caller) {
    if (runtime.throttleTimer != null && runtime.throttleTimer.cancel()) {
      Logic.info(runtime, caller, "Canceled throttleTimer");
    }
  }

  private static void recurringTimerAction(PublishingAgent runtime) {
    Logic.trace(runtime, "throttleTimer", "Tick");
    executeOneThrottledAction(runtime);
    if (runtime.publishQueue.isEmpty() && runtime.deleteQueue.isEmpty()) {
      Logic.info(runtime, "throttleTimer", "Idling timer due to lack of tasks");
    } else {
      Logic.debug(runtime, "throttleTimer", "Rescheduling timer execution for " + PUBLISH_PERIOD_MS + " ms");
      runtime.throttleTimer.reschedule(PUBLISH_PERIOD_MS);
    }
  }

  static void expireSubmissionOnCommand(PublishingAgent runtime, long id10) {
    Logic.trace(runtime, "expireSubmission", "Begin onCommand(" + id10 + ")");
    if (id10 <= 0L) {
      Logic.warn(runtime, "expireSubmission", "Ignoring expire request to nonpositive id10=" + id10);
    } else {
      final Answer ans = runtime.publishQueue.remove(id10);
      if (ans != null) {
        final String id36 = Utils.id10To36(id10);
        Logic.error(runtime, "expireSubmission",
            "</submission/" + id36 + ", " + ans + "> was evicted before agent could publish comment");
      }
      // Note: intentionally not removing from deleteQueue
      runtime.publishedAnswers.remove(id10);
      runtime.answers.remove(id10);
    }
    Logic.trace(runtime, "expireSubmission", "End onCommand()");
  }

  static void shelveSubmissionOnCommand(PublishingAgent runtime, long id10) {
    Logic.trace(runtime, "shelveSubmission", "Begin onCommand(" + id10 + ")");
    if (id10 <= 0L) {
      Logic.warn(runtime, "shelveSubmission", "Ignoring shelve request to nonpositive id10=" + id10);
    } else {
      runtime.publishQueue.remove(id10);
      final Value publishedAnswer = runtime.publishedAnswers.remove(id10);
      if (publishedAnswer instanceof Record) {
        final long commentId = publishedAnswer.get("id").longValue(-1L);
        if (commentId < 0) {
          Logic.warn(runtime, "shelveSubmission", "Ignored publishedAnswers entry " + publishedAnswer);
        } else {
          runtime.deleteQueue.put(commentId, id10);
        }
      }
      runtime.answers.remove(id10);
    }
    Logic.trace(runtime, "shelveSubmission", "End onCommand()");
  }

  static void addPublisherCommentOnCommand(PublishingAgent runtime, Comment comment) {
    Logic.trace(runtime, "addPublisherComment", "Begin onCommand(" + comment + ")");
    if (comment == null || comment.submissionId() == null || "".equals(comment.submissionId())) {
      Logic.warn(runtime, "addPublisherComment", "Ignoring Publisher comment to unidentifiable submission: " + comment);
      Logic.trace(runtime, "addPublisherComment", "End onCommand()");
      return;
    }
    final String subId36 = comment.submissionId();
    final long subId10 = Utils.id36To10(subId36),
        thisCommentId10 = Utils.id36To10(comment.id());
    final Answer thisPublishedAnswer = Publication.answerFromPublication(Shared.taxonomy(), comment.body());
    Logic.info(runtime, "addPublisherComment", "Extracted " + thisPublishedAnswer + " from published comment");
    final Value prevPublishedEntry = runtime.publishedAnswers.get(subId10);
    if (prevPublishedEntry instanceof Record) {
      reconcilePublishedAnswer(runtime, (Record) prevPublishedEntry, subId10, subId36,
          thisCommentId10, thisPublishedAnswer);
    } else {
      updatePublishedAnswer(runtime, subId10, thisCommentId10, thisPublishedAnswer);
    }
  }

  private static void reconcilePublishedAnswer(PublishingAgent runtime, Record prevPublishedEntry,
                                               long subId10, String subId36, long thisCommentId10,
                                               Answer thisPublishedAnswer) {
    final long prevCommentId10 = prevPublishedEntry.get("id").longValue();
    long keepCommentId10 = thisCommentId10;
    Answer keepAnswer = thisPublishedAnswer;
    if (prevCommentId10 < thisCommentId10) {
      keepCommentId10 = prevCommentId10;
      keepAnswer = Forms.forAnswer().cast(prevPublishedEntry.get("answer"));
      Logic.info(runtime, "addPublisherComment", "Will delete " + subId36 + "/" + Utils.id10To36(thisCommentId10)
          + " in favor of " + Utils.id10To36(prevCommentId10));
      runtime.deleteQueue.put(thisCommentId10, subId10);
    } else if (prevCommentId10 > thisCommentId10) {
      Logic.info(runtime, "addPublisherComment", "Will delete " + subId36 + "/" + Utils.id10To36(prevCommentId10)
          + " in favor of " + Utils.id10To36(thisCommentId10));
      runtime.deleteQueue.put(prevCommentId10, subId10);
    }
    final Answer currentAnswer = getAnswer(runtime, subId10);
    if (currentAnswer == null || currentAnswer.taxa().isEmpty()) {
      Logic.warn(runtime, "addPublisherComment", "Submission " + subId36 + " may contain answer-absent Publisher comment");
    }
    runtime.publishQueue.put(subId10, currentAnswer);
    updatePublishedAnswer(runtime, subId10, keepCommentId10, keepAnswer);
  }

  private static void updatePublishedAnswer(PublishingAgent runtime, long subId10, long commentId10, Answer answer) {
    runtime.publishedAnswers.put(subId10,
        Record.create(2).slot("id", commentId10).slot("answer", Forms.forAnswer().mold(answer).toValue()));
  }

  // FIXME: Hack that really should be replaced by a simple joinValueLane.get() once fixed in SwimOS;
  //   see https://github.com/swimos/swim/issues/107
  private static Answer getAnswer(PublishingAgent runtime, long key) {
    final Value v = runtime.answers.get(key);
    if (!v.isDistinct()) {
      return null;
    }
    final Item head = v.head();
    if (head instanceof Attr) {
      if ("answer".equals(head.key().stringValue(null))) {
        Logic.debug(runtime, "addPublisherComment", "publishedAnswers.get() yielded a properly formatted value");
        return Forms.forAnswer().cast(v);
      } else {
        Logic.warn(runtime, "addPublisherComment", "publishedAnswers.get() yielded non-answer structure " + v
            + " (see https://github.com/swimos/swim/issues/107)");
        final Record tail = v.tail();
        if (tail.head() instanceof Attr && "answer".equals(tail.head().key().stringValue(null))) {
          final Answer answer = Forms.forAnswer().cast(tail);
          Logic.info(runtime, "addPublisherComment", "publishedAnswers.get() hacked extraction of " + answer);
          return answer;
        }
      }
    }
    return null;
  }

  private static void executeOneThrottledAction(PublishingAgent runtime) {
    if (executeOneDelete(runtime) || executeOnePublishOrDequeue(runtime)) {
      return;
    }
    if (!runtime.deleteQueue.isEmpty() || !runtime.publishQueue.isEmpty()) {
      executeOneThrottledAction(runtime);
    }
  }

  private static boolean executeOneDelete(PublishingAgent runtime) {
    for (Map.Entry<Long, Long> entry : runtime.deleteQueue.entrySet()) {
      final long commentId10 = entry.getKey(),
          subId10 = entry.getValue();
      final String commentId36 =  Utils.id10To36(commentId10),
          subId36 = Utils.id10To36(subId10);
      runtime.deleteQueue.remove(commentId10);
      Logic.info(runtime, "throttleTimer", "Will asynchronously delete comment " + subId36 + "/" + commentId36);
      Logic.executeRedditDelete(runtime, "throttleTimer", client -> client.removeEditDel("t1_" + commentId36));
      return true;
    }
    return false;
  }

  private static boolean executeOnePublishOrDequeue(PublishingAgent runtime) {
    for (Map.Entry<Long, Answer> entry : runtime.publishQueue.entrySet()) {
      final long subId10 = entry.getKey();
      final Answer toPublishAnswer = entry.getValue();
      if (runtime.publishedAnswers.containsKey(subId10)) {
        if (executeOneEdit(runtime, subId10, toPublishAnswer)) {
          return true;
        } else {
          Logic.debug(runtime, "throttleTimer", "Publish action for submission " + Utils.id10To36(subId10) + " was fulfilled");
          runtime.publishQueue.remove(subId10);
        }
      } else {
        executeOneCreate(runtime, subId10, toPublishAnswer);
        return true;
      }
    }
    return false;
  }

  private static boolean executeOneEdit(PublishingAgent runtime, long subId10, Answer toPublishAnswer) {
    if (toPublishAnswer == null || toPublishAnswer.taxa().isEmpty()) {
      return false;
    }
    final Value publishedData = runtime.publishedAnswers.get(subId10);
    final Answer publishedAnswer = Forms.forAnswer().cast(publishedData.get("answer"));
    if (publishedAnswer == null) {
      Logic.warn(runtime, "throttleTimer", "Surprising null for " + Utils.id10To36(subId10));
      return false;
    }
    if (!publishedAnswer.taxa().equals(toPublishAnswer.taxa())
        || !publishedAnswer.reviewers().equals(toPublishAnswer.reviewers())) {
      final long commentId10 = publishedData.get("id").longValue();
      final String subId36 = Utils.id10To36(subId10),
          commentId36 = Utils.id10To36(commentId10);
      Logic.info(runtime, "throttleTimer", "Will asynchronously edit comment "
          + subId36  + "/" + commentId36
          + " answer to " + toPublishAnswer);
      Logic.executeRedditCallable(runtime, "throttleTimer", "editComment",
          client -> client.publishEditEditusertext("t1_" + commentId36, Publication.publicationFromAnswer(Shared.taxonomy(), toPublishAnswer)),
          r -> onPublishResponse(runtime, subId10, r, c -> Logic.info(runtime, "throttleTimer (blocker)", "Successfully edited comment to " + c)));
      return true;
    }
    return false;
  }

  private static void executeOneCreate(PublishingAgent runtime, long subId10, Answer toPublishAnswer) {
    final String subId36 = Utils.id10To36(subId10);
    Logic.info(runtime, "throttleTimer", "Will asynchronously create comment to submission "
        + subId36  + " with answer " + toPublishAnswer);
    Logic.executeRedditCallable(runtime, "throttleTimer", "createComment",
        client -> client.publishAnyComment("t3_" + Utils.id10To36(subId10), Publication.publicationFromAnswer(Shared.taxonomy(), toPublishAnswer)),
        r -> onPublishResponse(runtime, subId10, r, c -> Logic.info(runtime, "throttleTimer (blocker)", "Successfully created comment " + c)));
  }

  private static void onPublishResponse(PublishingAgent runtime, long subId10, RedditResponse<Comment> response,
                                        Consumer<Comment> log) {
    final Comment comment = response.essence();
    log.accept(comment);
    final Answer publishedAnswer = Publication.answerFromPublication(Shared.taxonomy(), comment.body());
    final Record publishedAnswerValue = Record.create(2)
        .slot("id", Utils.id36To10(comment.id()))
        .slot("answer", Forms.forAnswer().mold(publishedAnswer).toValue());
    runtime.publishedAnswers.put(subId10, publishedAnswerValue);
  }

}
