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
import filethesebirds.munin.connect.ebird.EBirdApiException;
import filethesebirds.munin.digest.Answer;
import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.Motion;
import filethesebirds.munin.digest.Users;
import filethesebirds.munin.digest.answer.Answers;
import filethesebirds.munin.digest.motion.EBirdExtractPurify;
import filethesebirds.munin.digest.motion.Extract;
import filethesebirds.munin.digest.motion.ExtractParse;
import filethesebirds.munin.digest.motion.Review;
import swim.concurrent.AbstractTask;
import swim.concurrent.TaskRef;
import swim.structure.Record;
import swim.structure.Value;

final class SubmissionAgentLogic {

  private SubmissionAgentLogic() {
  }

  static void infoDidSet() {

  }

  static void answerDidSet() {

  }

  static void expireOnCommand(SubmissionAgent runtime, Value v) {
    final String caller = "expire";
    removeSubmission(runtime, caller, v, () -> {
      // TODO: upsert submission/observations here just in case?
    });
  }

  static void shelveOnCommand(SubmissionAgent runtime, Value v) {
    final String caller = "shelve";
    removeSubmission(runtime, caller, v, () -> {
      final long id10 = Utils.id36To10(runtime.getProp("id").stringValue());
      Logic.executeOrLogVaultAction(runtime, caller,
          "Will delete",
          "asdf",
          client -> client.deleteSubmission(id10));
    });
  }

  private static void removeSubmission(SubmissionAgent runtime, String caller, Value v,
                                       Runnable purge) {
    Logic.trace(runtime, caller, "Begin onCommand(" + v + ")");
    if (v.isDistinct()) {
      try {
        purge.run();
      } catch (Exception e) {
        Logic.warn(runtime, caller, "Failed to shelve, agent will still close");
        runtime.didFail(e);
      } finally {
        clearLanes(runtime);
        Logic.trace(runtime, caller, "End onCommand()");
        runtime.close();
      }
    } else {
      Logic.warn(runtime, caller, "Skipped remove due to non-distinct command payload");
      Logic.trace(runtime, caller, "End onCommand()");
    }
  }

  private static void clearLanes(SubmissionAgent runtime) {
    runtime.motions.clear();
    runtime.status.set(Value.absent());
    runtime.answer.set(null);
    runtime.info.set(null);
  }

  static void motionsDidUpdate(SubmissionAgent runtime) {
    final Answer answer = Answers.mutable().apply(runtime.motions);
    final Answer current = runtime.answer.get();
    if (current == null) {
      if (!answer.taxa().isEmpty()) {
        runtime.answer.set(answer);
      }
    } else if (!answer.taxa().isEmpty()) { // if taxa present in new answer
      // change the established answer if the taxa or the reviewers are different
      if (!current.taxa().equals(answer.taxa())
          || !current.reviewers().equals(answer.reviewers())) {
        runtime.answer.set(answer);
      }
    }
  }

  // ===========================================================================
  // Potentially blocking logic
  // ===========================================================================

  static void onNewComment(SubmissionAgent runtime, String lane, Comment comment) {
    Logic.info(runtime, lane, "Received comment from " + comment.author());
    if (Users.userIsPublisher(comment.author())) {
      Logic.debug(runtime, lane, "Will defer publisher=" + comment.author()
          + " comment analysis to ThrottledPublishingAgent");
      runtime.command("/throttledPublish", "addPublisherComment",
          Comment.form().mold(comment).toValue());
      return;
    }
    if (CommentsFetchAgentLogic.commentIsRemover(comment)
        || CommentsFetchAgentLogic.submissionAuthorIsDeleted(comment)) {
      Logic.info(runtime, lane, "Will shelve submission");
      if (Shared.liveSubmissions().shelve(runtime, lane, comment.submissionId())) {
        Logic.executeOrLogVaultAction(runtime, lane,
            "",
            "",
            client -> client.deleteSubmission(comment.submissionId()));
      }
      return;
    }
    final Extract extract = ExtractParse.parseComment(comment); // CPU-intensive, not I/O-bound
    if (extract.isEmpty()) {
      Logic.debug(runtime, lane, "Did not analyze unremarkable comment from " + comment.author());
    } else if (extractIsImpure(extract)) {
      Logic.debug(runtime, lane, "Will analyze hint-containing comment via PhasedPurifyTask");
      // I/O-bound
      final PhasedPurifyTask action = new PhasedPurifyTask(runtime, comment, extract);
      if (!action.cue()) {
        Logic.error(runtime, lane,"Failed to cue purification task for comment " + comment);
      }
    } else {
      final Value laneKey = Record.create(2).item(comment.createdUtc()).item(comment.id());
      Logic.info(runtime, lane, "Will put " + laneKey + ", " + extract.base());
      runtime.motions.put(laneKey, extract.base());
    }
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
