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
import filethesebirds.munin.digest.Answer;
import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.Forms;
import filethesebirds.munin.digest.Motion;
import filethesebirds.munin.digest.Submission;
import java.util.List;
import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.MapLane;
import swim.api.lane.ValueLane;
import swim.structure.Form;
import swim.structure.Num;
import swim.structure.Value;

/**
 * A dynamically instantiable Web Agent that serves as the intelligent, informed
 * digital twin of an r/WhatsThisBird submission.
 *
 * <p>Each {@code SubmissionAgent} is liable for the following {@link
 * LiveSubmissions} action:
 * <ul>
 * <li>Shelving the submission upon encountering a properly issued {@code !rm}
 * comment or a comment whose submission author is {@code [deleted]}
 * </ul>
 * and the following vault actions:
 * <ul>
 * <li>Assigning observations based on updates to {@link #answer}
 * <li>Deleting the corresponded submission (cascaded to its observations) upon
 * encountering an aforementioned shelve-capable comment
 * </ul>
 */
public class SubmissionAgent extends AbstractAgent {

  @SwimLane("info")
  ValueLane<Submission> info = valueLane()
      .valueForm(Submission.form())
      .didSet(this::infoDidSet);

  @SwimLane("answer")
  ValueLane<Answer> answer = valueLane()
      .valueForm(Forms.forAnswer())
      .didSet(this::answerDidSet);

  @SwimLane("status")
  ValueLane<Value> status = this.<Value>valueLane()
      .didSet(this::statusDidSet);

  @SwimLane("motions")
  MapLane<Value, Motion> motions = mapLane()
      .keyForm(Form.forValue())
      .valueForm(Forms.forMotion())
      .didUpdate(this::motionsDidUpdate);

  /**
   * A command-type endpoint that triggers closing this {@code SubmissionAgent}
   * and clearing its lanes.
   */
  @SwimLane("expire")
  CommandLane<Value> expire = this.<Value>commandLane()
      .onCommand(this::expireOnCommand);

  /**
   * A command-type endpoint that triggers closing this {@code SubmissionAgent},
   * clearing its lanes, and removing all traces of its underlying submission
   * from vault.
   */
  @SwimLane("shelve")
  CommandLane<Value> shelve = this.<Value>commandLane()
      .onCommand(this::shelveOnCommand);

  @SwimLane("addNewComment")
  CommandLane<Comment> addNewComment = commandLane()
      .valueForm(Comment.form())
      .onCommand(c -> onNewComment(c, "addNewComment"));

  @SwimLane("addManyComments")
  CommandLane<List<Comment>> addManyComments = commandLane()
      .valueForm(Form.forList(Comment.form()))
      .onCommand(comments -> {
        if (comments != null && !comments.isEmpty()) {
          for (int i = comments.size() - 1; i >= 0; i--) {
            onNewComment(comments.get(i), "addManyComments");
          }
        }
      });

  // Callback logic

  protected void infoDidSet(Submission n, Submission o) {
    SubmissionAgentLogic.infoDidSet(this, n, o);
  }

  protected void answerDidSet(Answer n, Answer o) {
    SubmissionAgentLogic.answerDidSet(this, n, o);
  }

  protected void statusDidSet(Value n, Value o) {
    // stub
  }

  protected void expireOnCommand(Value v) {
    SubmissionAgentLogic.expireOnCommand(this, v);
  }

  protected void shelveOnCommand(Value v) {
    SubmissionAgentLogic.shelveOnCommand(this, v);
  }

  protected void onNewComment(Comment comment, String lane) {
    SubmissionAgentLogic.onNewComment(this, lane, comment);
  }

  protected void motionsDidUpdate(Value k, Motion n, Motion o) {
    SubmissionAgentLogic.motionsDidUpdate(this);
  }

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
    try {
      final Num id10 =  Num.from(Utils.id36To10(getProp("id").stringValue(null)));
      command("/submissions", "subscribe", id10);
    } catch (Exception e) {
      didFail(e);
      close();
    }
  }

  @Override
  public void willClose() {
    Logic.info(this, "willClose()", "");
  }

}
