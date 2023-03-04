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

import filethesebirds.munin.connect.ebird.EBirdApiException;
import filethesebirds.munin.digest.Answer;
import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.Forms;
import filethesebirds.munin.digest.Motion;
import filethesebirds.munin.digest.Submission;
import filethesebirds.munin.digest.Users;
import filethesebirds.munin.digest.answer.Answers;
import filethesebirds.munin.digest.motion.Extract;
import filethesebirds.munin.digest.motion.ExtractParse;
import filethesebirds.munin.digest.motion.EBirdExtractPurify;
import filethesebirds.munin.digest.motion.Review;
import java.util.List;
import java.util.Map;
import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.MapLane;
import swim.api.lane.ValueLane;
import swim.concurrent.AbstractTask;
import swim.concurrent.TaskRef;
import swim.structure.Form;
import swim.structure.Record;
import swim.structure.Text;
import swim.structure.Value;

public class SubmissionAgent extends AbstractAgent {

  // Stateful lanes

  @SwimLane("info")
  ValueLane<Submission> info = valueLane()
      .valueForm(Submission.form())
      .didSet((n, o) -> {
        this.status.set(merge(n, this.answer.get()));
      });

  @SwimLane("answer")
  ValueLane<Answer> answer = valueLane()
      .valueForm(Forms.forAnswer())
      .didSet((n, o) -> {
        this.status.set(merge(this.info.get(), n));
      });

  @SwimLane("status")
  ValueLane<Value> status = this.<Value>valueLane();

  @SwimLane("motions")
  MapLane<Value, Motion> motions = mapLane()
      .keyForm(Form.forValue())
      .valueForm(Forms.forMotion())
      .didUpdate((k, n, o) -> {
        final Answer answer = Answers.mutable();
        for (Map.Entry<Value, Motion> entry : this.motions.entrySet()) {
          if (answer.motionIsSignificant(entry.getValue())) {
            answer.apply(entry.getValue());
          }
        }
        final Answer current = this.answer.get();
        if (current == null) {
          if (!answer.taxa().isEmpty()) {
            this.answer.set(answer);
          }
        } else if (!answer.taxa().isEmpty()) { // if taxa present in new answer
          // change the established answer if the taxa or the reviewers are different
          if (!current.taxa().equals(answer.taxa())
              || !current.reviewers().equals(answer.reviewers())) {
            this.answer.set(answer);
          }
        }
      });

  // Stateless lanes

  @SwimLane("expire")
  CommandLane<Value> expire = this.<Value>commandLane()
      .onCommand(v -> {
        if (v.isDistinct()) {
          redactJoins();
          this.motions.clear();
          this.status.set(Value.absent());
          this.answer.set(null);
          this.info.set(null);
        }
      });

  @SwimLane("addNewComment")
  CommandLane<Comment> addNewComment = commandLane()
      .valueForm(Comment.form())
      .onCommand(this::onNewComment);

  @SwimLane("addManyComments")
  CommandLane<List<Comment>> addManyComments = commandLane()
      .valueForm(Form.forList(Comment.form()))
      .onCommand(comments -> {
        if (comments != null && !comments.isEmpty()) {
          for (int i = comments.size() - 1; i >= 0; i--) {
            onNewComment(comments.get(i));
          }
        }
      });

  // Agent lifecycle

  @Override
  public void didStart() {
    System.out.println(nodeUri() + ": didStart");
    initiateJoins();
  }

  // Callback logic

  private void onNewComment(Comment comment) {
    System.out.println(nodeUri() + ": processed comment from " + comment.author());
    if (Users.userIsPublisher(comment.author())) {
      command("/throttledPublish", "addPublisherComment",
          Comment.form().mold(comment).toValue());
      return;
    }
    // taxa
    final Extract extract = ExtractParse.parseComment(comment);
    if (extract.isEmpty()) {
      return;
    }
    if (!extract.hints().isEmpty() || !extract.vagueHints().isEmpty()) {
      final StaggeredPurifyAction action = new StaggeredPurifyAction(comment, extract);
      if (!action.cueHintTransformations()) {
        System.out.println(nodeUri() + ": failed to cue task for comment " + comment);
      }
      return;
    }
    final Value laneKey = Record.create(2).item(comment.createdUtc()).item(comment.id());
    System.out.println(nodeUri() + ": will put " + laneKey + ", " + extract.base());
    this.motions.put(laneKey, extract.base());
  }

  private void initiateJoins() {
    command("/submissions", "subscribe", Text.from(nodeUri().toString()));
    command("/throttledPublish", "subscribe", Text.from(nodeUri().toString()));
    command("/commentsFetch", "addLiveSubmission", getProp("id"));
  }

  private void redactJoins() {
    command("/submissions", "unsubscribe", Text.from(nodeUri().toString()));
    command("/throttledPublish", "unsubscribe", Text.from(nodeUri().toString()));
  }

  private static Value merge(Submission s, Answer a) {
    if (s == null || s.id() == null || s.id().isEmpty()) {
      return Value.extant();
    }
    final Record r = Record.create(12).attr("status")
        // info
        .slot("id", s.id())
        .slot("title", s.title())
        .slot("location", s.location().text())
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

  private class StaggeredPurifyAction {

    private static final int MAX_EXPLORABLE_HINTS = 10;
    private static final int MAX_FAILURES = 5;

    private final Comment comment;
    private volatile Extract soFar;
    private volatile int hintsSoFar;
    private volatile int failures;
    private final TaskRef task;

    private StaggeredPurifyAction(Comment comment1, Extract soFar1) {
      this.comment = comment1;
      this.soFar = soFar1;
      this.hintsSoFar = 0;
      this.task = asyncStage().task(new AbstractTask() {

        @Override
        public void runTask() {
          while (!isComplete()) {
            try {
              setSoFar(EBirdExtractPurify.purifyOneHint(Shared.eBirdClient(), getSoFar()));
              StaggeredPurifyAction.this.hintsSoFar++;
            } catch (EBirdApiException e) {
              if (++failures <= MAX_FAILURES) {
                System.out.println(nodeUri() + ": exception in processing hint for comment " + comment
                    + ", retrying in ~1 min");
                setTimer(60000L + (int) (Math.random() * 30000),
                    StaggeredPurifyAction.this.task::cue);
              } else {
                System.out.println(nodeUri() + ": exception in processing hint for comment " + comment
                    + ", aborting.");
              }
              return;
            }
          }
          // On success
          final Motion purified = getSoFar().base();
          if ((purified instanceof Review) || !purified.isEmpty()) {
            System.out.println(nodeUri() + ": purified extract into " + purified);
            final Value laneKey = Record.create(2).item(comment.createdUtc()).item(comment.id());
            SubmissionAgent.this.motions.put(laneKey, purified);
          } else {
            System.out.println(nodeUri() + ": failed to purify " + comment);
          }
        }

        @Override
        public boolean taskWillBlock() {
          return true;
        }

      });
    }

    private Extract getSoFar() {
      return this.soFar;
    }

    private void setSoFar(Extract extract) {
      this.soFar = extract;
    }

    private boolean isComplete() {
      return this.hintsSoFar >= MAX_EXPLORABLE_HINTS
          || EBirdExtractPurify.extractIsPurified(this.soFar);
    }

    private boolean cueHintTransformations() {
      return this.task.cue();
    }

  }

}
