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

import swim.api.SwimLane;
import swim.api.lane.CommandLane;
import swim.api.lane.JoinValueLane;
import swim.api.lane.MapLane;
import swim.munin.MuninEnvironment;
import swim.munin.connect.reddit.Comment;
import swim.munin.connect.reddit.RedditClient;
import swim.munin.filethesebirds.digest.Answer;
import swim.munin.filethesebirds.digest.Forms;
import swim.munin.swim.AbstractPublishingAgent;
import swim.munin.swim.LiveSubmissions;
import swim.structure.Form;
import swim.structure.Value;

/**
 * A Web Agent that comments an in-progress nonempty answer to each live
 * r/WhatsThisBird submission and edits the comment as the answer evolves,
 * deleting the comment only if the submission is explicitly shelved.
 *
 * <p>This agent does not directly modify vault or a {@code LiveSubmissions}
 * instance.
 */
public class PublishingAgent extends AbstractPublishingAgent<Answer> {

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
  protected final MapLane<Long, Value> publishedAnswers = mapLane();

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
  protected Form<Answer> publishQueueValueForm() {
    return Forms.forAnswer();
  }

  @Override
  protected void executeOneThrottledAction() {
    PublishingAgentLogic.executeOneThrottledAction(this, this.publishQueue, this.deleteQueue);
  }

  protected void answersDidUpdate(long k, Value n, Value o) {
    PublishingAgentLogic.answersDidUpdate(this, this.publishQueue, k, n, o);
  }

  protected void subscribeOnCommand(long subId10) {
    PublishingAgentLogic.subscribeOnCommand(this, subId10);
  }

  protected void expireSubmissionOnCommand(long k) {
    PublishingAgentLogic.expireSubmissionOnCommand(this, this.publishQueue, k);
  }

  protected void shelveSubmissionOnCommand(long k) {
    PublishingAgentLogic.shelveSubmissionOnCommand(this, this.publishQueue, this.deleteQueue, k);
  }

  protected void addPublisherCommentOnCommand(Comment c) {
    PublishingAgentLogic.addPublisherCommentOnCommand(this, this.publishQueue, this.deleteQueue, c);
  }

}
