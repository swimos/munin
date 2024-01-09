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

import filethesebirds.munin.digest.Answer;
import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.answer.Forms;
import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.JoinValueLane;
import swim.api.lane.MapLane;
import swim.concurrent.TimerRef;
import swim.structure.Form;
import swim.structure.Value;

/**
 * A Web Agent that comments an in-progress nonempty answer to each live
 * r/WhatsThisBird submission and edits the comment as the answer evolves,
 * deleting the comment only if the submission is explicitly shelved.
 *
 * <p>The Reddit API documentation mentions a maximum of 100 calls per minute,
 * but there appears to be a separate undocumented, inconsistent limit that only
 * concerns write-type calls (e.g. posting, editing, or deleting comments). In
 * order to address this, a {@code PublishingAgent} does not immediately perform
 * one of the aforementioned actions when requested. It instead places the task
 * in a queue and uses a self-rescheduling {@link swim.concurrent.Timer} to
 * perform one queued task until no tasks remain.
 *
 * <p>This agent does not directly modify vault or a {@code LiveSubmissions}
 * instance.
 */
public class PublishingAgent extends AbstractAgent {

  protected TimerRef throttleTimer;

  /**
   * A streaming API to all the active submissions' answers, implemented as a
   * join lane over every pertinent {@code SubmissionAgent}.
   */
  @SwimLane("answers")
  protected final JoinValueLane<Long, Value> answers = joinValueLane()
      .keyForm(Form.forLong())
      .valueForm(Form.forValue())
      .didUpdate(this::answersDidUpdate);

  /**
   * A command-type endpoint that triggers {@link #answers} to include the
   * answer for a provided {@code SubmissionAgent}'s answer as part of its
   * available state.
   */
  @SwimLane("subscribe")
  protected final CommandLane<Long> subscribe = this.<Long>commandLane()
      .onCommand(this::subscribeOnCommand);

  /**
   * The collection of (submissionID, (commentID, answer)) pairs that have
   * already been commented to Reddit by the Publisher account (for example,
   * u/FileTheseBirdsBot for the hosted app).
   */
  @SwimLane("publishedAnswers")
  MapLane<Long, Value> publishedAnswers = mapLane();

  /**
   * The collection of answers that must be commented to Reddit by the Publisher
   * account, populated primarily via observed changes to {@code answers}.
   *
   * <p>This lane and {@link #deleteQueue} together form the {@code
   * PublishingAgent}'s work queue, with {@code deleteQueue}'s tasks taking
   * priority over those in this lane.
   */
  @SwimLane("publishQueue")
  protected final MapLane<Long, Answer> publishQueue = mapLane()
      .keyForm(Form.forLong())
      .valueForm(Forms.forAnswer())
      .didUpdate(this::publishQueueDidUpdate);

  /**
   * The collection of (commentID, submissionID) pairs identifying Publisher
   * account comments that must be deleted; possible reasons for this include
   * submission shelving and inadvertent duplicate Publisher comments to a
   * submission.
   *
   * <p>This lane and {@link #publishQueue} together form the {@code
   * PublishingAgent}'s work queue, with this lane's tasks taking priority over
   * those in {@code publishQueue}.
   */
  @SwimLane("deleteQueue")
  protected final MapLane<Long, Long> deleteQueue = this.<Long, Long>mapLane()
      .didUpdate(this::deleteQueueDidUpdate);

  /**
   * A command-type endpoint that triggers this {@code PublishingAgent} to clear
   * all agent-local data corresponding to the provided submission.
   */
  @SwimLane("expireSubmission")
  protected final CommandLane<Long> expireSubmission = this.<Long>commandLane()
      .onCommand(this::expireSubmissionOnCommand);

  /**
   * A command-type endpoint that triggers this {@code PublishingAgent} to clear
   * both agent-local data and any Publisher comment on Reddit corresponding to
   * the provided submission.
   */
  @SwimLane("shelveSubmission")
  protected final CommandLane<Long> shelveSubmission = this.<Long>commandLane()
      .onCommand(this::shelveSubmissionOnCommand);

  /**
   * A command-type endpoint that notifies this {@code PublishingAgent} that a
   * comment posted by the same {@code PublishingAgent} is available to
   * subreddit readers.
   *
   * <p>The agent uses such notifications to ensure that it leaves at most one
   * comment per submission, even in the event of Swim server restarts and
   * spurious network failures.
   */
  @SwimLane("addPublisherComment")
  protected final CommandLane<Comment> addPublisherComment = commandLane()
      .valueForm(Comment.form())
      .onCommand(this::addPublisherCommentOnCommand);

  protected void answersDidUpdate(long k, Value n, Value o) {
    PublishingAgentLogic.answersDidUpdate(this, k, n, o);
  }

  protected void subscribeOnCommand(long subId10) {
    PublishingAgentLogic.subscribeOnCommand(this, subId10);
  }

  protected void publishQueueDidUpdate(long k, Answer n, Answer o) {
    PublishingAgentLogic.queueDidUpdate(this, "publishQueue");
  }

  protected void deleteQueueDidUpdate(long k, long n, long o) {
    PublishingAgentLogic.queueDidUpdate(this, "deleteQueue");
  }

  protected void expireSubmissionOnCommand(long k) {
    PublishingAgentLogic.expireSubmissionOnCommand(this, k);
  }

  protected void shelveSubmissionOnCommand(long k) {
    PublishingAgentLogic.shelveSubmissionOnCommand(this, k);
  }

  protected void addPublisherCommentOnCommand(Comment c) {
    PublishingAgentLogic.addPublisherCommentOnCommand(this, c);
  }

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
  }

}
