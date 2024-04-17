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
import filethesebirds.munin.digest.TaxResolve;
import filethesebirds.munin.digest.Users;
import filethesebirds.munin.digest.answer.Answers;
import filethesebirds.munin.digest.motion.Extract;
import filethesebirds.munin.digest.motion.ExtractParse;
import filethesebirds.munin.digest.motion.Review;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import swim.structure.Num;
import swim.structure.Record;
import swim.structure.Value;

final class SubmissionAgentLogic {

  private SubmissionAgentLogic() {
  }

  static void infoDidSet(SubmissionAgent runtime, Submission n, Submission o) {
    Logic.trace(runtime, "info", "Begin didSet(" + n + ", " + o + ")");
    if (n != null) {
      final Answer ans = runtime.answer.get();
      runtime.status.set(merge(n, ans));
    }
    Logic.trace(runtime, "info", "End didSet()");
  }

  static void answerDidSet(SubmissionAgent runtime, Answer n, Answer o) {
    Logic.trace(runtime, "answer", "Begin didSet(" + n + ", " + o + ")");
    runtime.status.set(merge(runtime.info.get(), n));
    Logic.executeOrLogVaultAction(runtime, "answer",
        "Assigning observations " + n + " under " + runtime.getProp("id").stringValue(null),
        "Failed to assign observations",
        client -> client.assignObservations(runtime.getProp("id").stringValue(), n));
    Logic.trace(runtime, "answer", "End didSet()");
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
          .map(Shared.taxonomy()::commonName).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
      return r.slot("taxa", Forms.forSetString().mold(a.taxa()).toValue())
          .slot("commons", commons.isEmpty() ? Value.extant() : Forms.forSetString().mold(commons).toValue())
          .slot("reviewers", a.reviewers() == null || a.reviewers().isEmpty() ? Value.extant()
              : Forms.forSetString().mold(a.reviewers()).toValue());
    }
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
          "Will delete " + id10 + " from vault",
          "Failed to delete " + id10 + " from vault",
          client -> client.deleteSubmission10(id10));
    });
  }

  private static void removeSubmission(SubmissionAgent runtime, String caller, Value v,
                                       Runnable purge) {
    Logic.trace(runtime, caller, "Begin onCommand(" + v + ")");
    if (v.isDistinct()) {
      try {
        final long id10 = Utils.id36To10(runtime.getProp("id").stringValue());
        Logic.debug(runtime, caller, "Notifying /submissions of submission " + caller);
        runtime.command("/submissions", caller + "Submission", Num.from(id10));
        purge.run();
      } catch (Exception e) {
        Logic.warn(runtime, caller, "Failed to " + caller + ", agent will still close");
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
      final Answer consolidated = consolidatedAnswer(runtime, answer);
      if (!consolidated.taxa().isEmpty()) {
        runtime.answer.set(consolidated);
      }
    } else if (!answer.taxa().isEmpty()) { // if taxa present in new answer
      // change the established answer if the taxa or the reviewers are different
      final Answer consolidated = consolidatedAnswer(runtime, answer);
      if (!current.taxa().equals(consolidated.taxa())
          || !current.reviewers().equals(consolidated.reviewers())) {
        runtime.answer.set(consolidated);
      }
    }
  }

  private static Answer consolidatedAnswer(SubmissionAgent runtime, Answer toPublishAnswer) {
    if (toPublishAnswer.taxa().isEmpty()) {
      return toPublishAnswer;
    }
    final Set<String> consolidatedTaxa = new HashSet<>();
    boolean didChange = false;
    for (String code : toPublishAnswer.taxa()) {
      if (Shared.taxonomy().containsCode(code)) {
        consolidatedTaxa.add(code);
      } else {
        Logic.warn(runtime, "throttleTimer", "Ignoring bad code " + code);
        didChange = true;
      }
    }
    if (!didChange) {
      return toPublishAnswer;
    }
    if (consolidatedTaxa.isEmpty()) {
      return Answers.mutable();
    } else {
      final Answer result = Answers.mutable();
      toPublishAnswer.reviewers().forEach(r -> result.review(filethesebirds.munin.digest.motion.Forms.forReview()
          .cast(Record.create(2).attr("review", r).slot("overrideTaxa", Forms.forSetString().mold(consolidatedTaxa).toValue()))));
      return result;
    }
  }

  static void onNewComment(SubmissionAgent runtime, String lane, Comment comment) {
    Logic.info(runtime, lane, "Received comment from " + comment.author());
    if (Users.userIsPublisher(comment.author())) {
      Logic.debug(runtime, lane, "Will defer publisher=" + comment.author()
          + " comment analysis to PublishingAgent");
      runtime.command("/submissions", "addPublisherComment",
          Comment.form().mold(comment).toValue());
      return;
    }
    if (CommentsFetchAgentLogic.commentIsRemover(comment)
        || CommentsFetchAgentLogic.submissionAuthorIsDeleted(comment)) {
      Logic.info(runtime, lane, "Will shelve submission");
      if (Shared.liveSubmissions().shelve(runtime, lane, comment.submissionId())) {
        runtime.command("/submissions", "shelveSubmission", Num.from(Utils.id36To10(comment.submissionId())));
        Logic.executeOrLogVaultAction(runtime, lane,
            "Deleting submission " + comment.submissionId(),
            "Failed to delete submission " + comment.submissionId(),
            client -> client.deleteSubmission36(comment.submissionId()));
      }
      return;
    }
    final Extract extract = ExtractParse.parseComment(Shared.taxonomy(), comment); // CPU-intensive, not I/O-bound
    if (extract.isEmpty()) {
      Logic.debug(runtime, lane, "Did not analyze unremarkable comment from " + comment.author());
    } else if (extract.isImpure()) {
      Logic.debug(runtime, lane, "Will analyze hint-containing comment");
      final Motion purified = purify(runtime, lane, Shared.taxonomy(), extract);
      if ((purified instanceof Review) || !purified.isEmpty()) {
        Logic.info(runtime, lane, "Purified extract into " + purified
            + ", will update motions accordingly");
        final Value laneKey = Record.create(2).item(comment.createdUtc()).item(comment.id());
        runtime.motions.put(laneKey, purified);
      } else {
        Logic.warn(runtime, lane, "Purification of comment "
            + comment + " unexpectedly yielded empty motion");
      }
    } else {
      final Value laneKey = Record.create(2).item(comment.createdUtc()).item(comment.id());
      Logic.info(runtime, lane, "Will put " + laneKey + ", " + extract.base());
      runtime.motions.put(laneKey, extract.base());
    }
  }

  private static Motion purify(SubmissionAgent runtime, String lane, TaxResolve taxonomy, Extract extract) {
    final int n = extract.hints().size() + extract.vagueHints().size();
    for (int i = 0; i < n; i++) {
      extract = purifyOneHint(runtime, lane, taxonomy, extract);
    }
    if (extract.isImpure()) {
      Logic.warn(runtime, lane, "extract was not completely purified");
    }
    return extract.base();
  }

  private static Extract purifyOneHint(SubmissionAgent runtime, String lane, TaxResolve taxonomy, Extract extract) {
    if (!extract.hints().isEmpty()) {
      final String hint = extract.hints().stream().findAny().get();
      final String code = taxonomy.resolve(hint);
      if ("".equals(code)) {
        Logic.warn(runtime, lane, "Could not resolve hint=" + hint);
        return extract.purifyHint(hint, null);
      } else {
        return extract.purifyHint(hint, code);
      }
    } else {
      final String hint = extract.vagueHints().stream().findAny().get();
      final String code = taxonomy.resolve(hint);
      if ("".equals(code)) {
        Logic.warn(runtime, lane, "Could not resolve hint=" + hint);
        return extract.purifyVagueHint(hint, null);
      } else {
        return extract.purifyVagueHint(hint, code);
      }
    }
  }

}
