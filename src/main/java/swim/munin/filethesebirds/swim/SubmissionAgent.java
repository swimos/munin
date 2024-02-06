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

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import swim.api.SwimLane;
import swim.api.lane.MapLane;
import swim.api.lane.ValueLane;
import swim.concurrent.AbstractTask;
import swim.concurrent.TaskRef;
import swim.munin.MuninEnvironment;
import swim.munin.Utils;
import swim.munin.connect.reddit.Comment;
import swim.munin.connect.reddit.RedditClient;
import swim.munin.connect.reddit.Submission;
import swim.munin.filethesebirds.connect.ebird.EBirdApiException;
import swim.munin.filethesebirds.digest.Answer;
import swim.munin.filethesebirds.digest.Forms;
import swim.munin.filethesebirds.digest.Motion;
import swim.munin.filethesebirds.digest.Taxonomy;
import swim.munin.filethesebirds.digest.Users;
import swim.munin.filethesebirds.digest.answer.Answers;
import swim.munin.filethesebirds.digest.motion.EBirdExtractPurify;
import swim.munin.filethesebirds.digest.motion.Extract;
import swim.munin.filethesebirds.digest.motion.ExtractParse;
import swim.munin.filethesebirds.digest.motion.Review;
import swim.munin.swim.AbstractSubmissionAgent;
import swim.munin.swim.LiveSubmissions;
import swim.munin.swim.Logic;
import swim.structure.Form;
import swim.structure.Num;
import swim.structure.Record;
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
public class SubmissionAgent extends AbstractSubmissionAgent {

  @SwimLane("answer")
  protected final ValueLane<Answer> answer = valueLane()
      .valueForm(Forms.forAnswer())
      .didSet(this::answerDidSet);

  @SwimLane("status")
  protected final ValueLane<Value> status = this.<Value>valueLane()
      .didSet(this::statusDidSet);

  @SwimLane("motions")
  protected final MapLane<Value, Motion> motions = mapLane()
      .keyForm(Form.forValue())
      .valueForm(Forms.forMotion())
      .didUpdate(this::motionsDidUpdate);

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
  protected void infoDidSet(Submission n, Submission o) {
    Logic.trace(this, "info", "Begin didSet(" + n + ", " + o + ")");
    if (n != null) {
      final Answer ans = this.answer.get();
      this.status.set(merge(n, ans));
    }
    Logic.trace(this, "info", "End didSet()");
  }

  @Override
  protected void purge(String caller, long id10) {
    Logic.executeOrLogVaultAction(this, caller,
        "Will delete " + id10 + " from vault",
        "Failed to delete " + id10 + " from vault",
        Shared.vaultClient(), client -> client.deleteSubmission10(id10));
  }

  @Override
  protected void clearLanes() {
    this.motions.clear();
    this.status.set(Value.absent());
    this.answer.set(null);
    this.info.set(null);
  }

  protected void answerDidSet(Answer n, Answer o) {
    Logic.trace(this, "answer", "Begin didSet(" + n + ", " + o + ")");
    this.status.set(merge(this.info.get(), n));
    Logic.executeOrLogVaultAction(this, "answer",
        "Assigning observations " + n + " under " + getProp("id").stringValue(null),
        "Failed to assign observations",
        Shared.vaultClient(), client -> client.assignObservations(this.getProp("id").stringValue(), n));
    Logic.trace(this, "answer", "End didSet()");
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
      return r.slot("taxa", Value.extant()).slot("commons", Value.extant()).slot("reviewers", Value.extant());
    } else {
      final Set<String> commons = a.taxa().stream()
          .map(Taxonomy::commonName).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
      return r.slot("taxa", Forms.forSetString().mold(a.taxa()).toValue())
          .slot("commons", commons.isEmpty() ? Value.extant() : Forms.forSetString().mold(commons).toValue())
          .slot("reviewers", a.reviewers() == null || a.reviewers().isEmpty() ? Value.extant()
              : Forms.forSetString().mold(a.reviewers()).toValue());
    }
  }

  protected void motionsDidUpdate(Value k, Motion n, Motion o) {
    final Answer answer = Answers.mutable().apply(this.motions);
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
  }

  @Override
  protected boolean commentShelvesSubmission(Comment comment) {
    return super.commentShelvesSubmission(comment)
        || (comment.body().startsWith("!rm") && Users.userIsAdmin(comment.author()));
  }

  @Override
  protected boolean onNewComment(String caller, Comment comment) {
    Logic.info(this, caller, "Received comment from " + comment.author());
    if (Users.userIsPublisher(comment.author())) {
      Logic.debug(this, caller, "Will defer publisher=" + comment.author()
          + " comment analysis to PublishingAgent");
      command("/submissions", "addPublisherComment",
          Comment.form().mold(comment).toValue());
      return true;
    }
    if (commentShelvesSubmission(comment)) {
      Logic.info(this, caller, "Will shelve submission");
      if (Shared.liveSubmissions().shelve(this, caller, comment.submissionId())) {
        command("/submissions", "shelveSubmission", Num.from(Utils.id36To10(comment.submissionId())));
        Logic.executeOrLogVaultAction(this, caller,
            "Deleting submission " + comment.submissionId(),
            "Failed to delete submission " + comment.submissionId(),
            Shared.vaultClient(), client -> client.deleteSubmission36(comment.submissionId()));
      }
      return false;
    }
    final Extract extract = ExtractParse.parseComment(comment); // CPU-intensive, not I/O-bound
    if (extract.isEmpty()) {
      Logic.debug(this, caller, "Did not analyze unremarkable comment from " + comment.author());
    } else if (extractIsImpure(extract)) {
      Logic.debug(this, caller, "Will analyze hint-containing comment via PhasedPurifyTask");
      // I/O-bound
      final PhasedPurifyTask action = new PhasedPurifyTask(this, comment, extract);
      if (!action.cue()) {
        Logic.error(this, caller,"Failed to cue purification task for comment " + comment);
      }
    } else {
      final Value laneKey = Record.create(2).item(comment.createdUtc()).item(comment.id());
      Logic.info(this, caller, "Will put " + laneKey + ", " + extract.base());
      this.motions.put(laneKey, extract.base());
    }
    return true;
  }

  private static boolean extractIsImpure(Extract extract) {
    return !extract.hints().isEmpty() || !extract.vagueHints().isEmpty();
  }

  private static class PhasedPurifyTask {

    private static final int MAX_EXPLORABLE_HINTS = 10;
    private static final int MAX_FAILURES = 5;

    private volatile Extract soFar;
    private volatile int hintsSoFar;
    private volatile int failures;
    private final TaskRef task;

    PhasedPurifyTask(SubmissionAgent runtime, Comment comment, Extract soFar) {
      this.soFar = soFar;
      this.hintsSoFar = 0;
      this.task = runtime.asyncStage().task(new AbstractTask() {

        @Override
        public void runTask() {
          while (!isComplete()) {
            try {
              setSoFar(EBirdExtractPurify.purifyOneHint(Shared.eBirdClient(), getSoFar()));
              PhasedPurifyTask.this.hintsSoFar++;
            } catch (EBirdApiException e) {
              if (++failures <= MAX_FAILURES) {
                Logic.warn(runtime, "[PhasedPurifyTask]",
                    "Exception in processing hint for comment " + comment + ", retrying in ~1 min");
                runtime.setTimer(60000L + (long) (Math.random() * 30000) - 15000L,
                    PhasedPurifyTask.this.task::cue);
              } else {
                Logic.error(runtime, "[PhasedPurifyTask]",
                    "Exception in processing hint for comment " + comment + ", aborting");
                runtime.didFail(e);
              }
              return;
            }
          }
          // On success
          final Motion purified = getSoFar().base();
          if ((purified instanceof Review) || !purified.isEmpty()) {
            Logic.info(runtime, "[PhasedPurifyTask]", "Purified extract into " + purified
                + ", will update motions accordingly");
            final Value laneKey = Record.create(2).item(comment.createdUtc()).item(comment.id());
            runtime.motions.put(laneKey, purified);
          } else {
            Logic.warn(runtime, "[PhasedPurifyTask]", "Purification of comment "
                + comment + " unexpectedly yielded empty motion");
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

    boolean cue() {
      return this.task.cue();
    }

  }

}
