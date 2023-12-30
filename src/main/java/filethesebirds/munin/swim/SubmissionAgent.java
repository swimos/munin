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
import filethesebirds.munin.digest.Forms;
import filethesebirds.munin.digest.Motion;
import filethesebirds.munin.digest.Submission;
import java.util.List;
import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.MapLane;
import swim.api.lane.ValueLane;
import swim.concurrent.AbstractTask;
import swim.structure.Form;
import swim.structure.Record;
import swim.structure.Text;
import swim.structure.Value;

/**
 * A Web Agent that serves as an intelligent digital twin of a submission to
 * r/WhatsThisBird.
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

  @SwimLane("expire")
  CommandLane<Value> expire = this.<Value>commandLane()
      .onCommand(this::expireOnCommand);

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
    final Answer ans = this.answer.get();
    this.status.set(merge(n, ans));
    if ((o == null || o.id() == null || o.id().isEmpty())
        && (ans != null && !ans.taxa().isEmpty())) {
      asyncStage().task(new AbstractTask() {

        @Override
        public void runTask() {
          Shared.vaultClient().assignObservations(getProp("id").stringValue(), ans);
        }

        @Override
        public boolean taskWillBlock() {
          return true;
        }

      }).cue();
    }
  }

  protected void answerDidSet(Answer n, Answer o) {
    Logic.info(this, "answer", "Updated answer to " + n + " from " + o);
    this.status.set(merge(this.info.get(), n));
    Logic.info(this, "answer", null);
    Logic.executeOrLogVaultAction(this, "answer",
        "FIXME",
        "Failed to assign observations",
        client -> client.assignObservations(getProp("id").stringValue(), n));
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

  private void initiateJoins() {
    command("/live", "subscribe", Text.from(nodeUri().toString()));
  }

  private void removeJoins() {
    command("/live", "expireSubmission", Text.from(nodeUri().toString()));
  }

  private void expireJoins() {
    command("/live", "removeSubmission", Text.from(nodeUri().toString()));
  }

  private static Value merge(Submission s, Answer a) {
    if (s == null || s.id() == null || s.id().isEmpty()) {
      return Value.extant();
    }
    final Record r = Record.create(12).attr("status")
        // info
        .slot("id", s.id())
        .slot("title", s.title())
        .slot("flair", s.flair())
        .slot("thumbnail", s.thumbnail())
        .slot("createdUtc", s.createdUtc())
        .slot("karma", s.karma())
        .slot("commentCount", s.commentCount());
    if (a == null || a.taxa().isEmpty()) {
      return r.slot("taxa", Value.extant()).slot("reviewers", Value.extant());
    } else {
      return r.slot("taxa", Forms.forSetString().mold(a.taxa()).toValue())
          .slot("reviewers", a.reviewers() == null || a.reviewers().isEmpty() ? Value.extant()
              : Forms.forSetString().mold(a.reviewers()).toValue());
    }
  }

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
    initiateJoins();
  }

}
