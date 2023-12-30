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
import filethesebirds.munin.connect.reddit.RedditClient;
import filethesebirds.munin.connect.reddit.RedditResponse;
import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.Submission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import swim.structure.Value;

/**
 * {@link SubmissionsFetchAgent}-focused utility class.
 */
final class SubmissionsFetchAgentLogic {

  private static final String CALLER_LANE = "preemptFetch";
  private static final String CALLER_TASK = "[GatherTask]";

  private SubmissionsFetchAgentLogic() {
  }

  static void preemptFetchOnCommand(SubmissionsFetchAgent runtime, Value v) {
    Logic.trace(runtime, CALLER_LANE, "Begin onCommand(" + v + ")");
    runtime.fetchTimer = Logic.scheduleRecurringBlocker(runtime, "preemptFetch",
        runtime::fetchTimer, 3000L, 180000L, () -> fetchTimerAction(runtime));
    Logic.trace(runtime, CALLER_LANE, "End onCommand()");
  }

  private static void fetchTimerAction(SubmissionsFetchAgent runtime) {
    final long until = System.currentTimeMillis() - MuninConstants.lookbackMillis();
    final Map<String, Submission> liveCandidates = new HashMap<>(500);
    final Map<Long, Submission> shelfCandidates = Shared.liveSubmissions().activeSnapshot();

    // Gather (fetch active submissions into liveSubmissions and identify shelf candidates, but do not update vault)
    Logic.trace(runtime, CALLER_TASK, "Will seek submissions through epoch " + until);
    new GatherAgentTask(until, runtime, liveCandidates, shelfCandidates).run();
    Logic.debug(runtime, CALLER_TASK, "Gathered " + liveCandidates.size() + " live submissions through epoch " + until);

    // Shelve (update liveSubmissions#shelved and remove entries from vault as needed)
    if (!shelfCandidates.isEmpty()) {
      final String joinedCandidates = shelfCandidates.keySet().stream()
          .map(k -> "t3_" + Utils.id10To36(k))
          .collect(Collectors.joining(","));
      Logic.info(runtime, CALLER_TASK, "Will check submissions " + joinedCandidates + " for shelving");
      final Set<String> didShelve = new HashSet<>(shelfCandidates.size());
      shelve(runtime, joinedCandidates, didShelve);
      liveCandidates.keySet().removeAll(didShelve);
    }

    // Upsert active submissions into vault
    if (!liveCandidates.isEmpty()) {
      Logic.doOrLogVaultAction(runtime, CALLER_TASK,
          "Will upsert " + liveCandidates.size() + " submissions into vault",
          "Failed to upsert submissions",
          client -> client.upsertSubmissions(liveCandidates.values()));
    }
  }

  private static void shelve(SubmissionsFetchAgent runtime, String candidates, Set<String> didShelve) {
    Logic.doRedditCallable(runtime, CALLER_TASK, "getById", client -> client.fetchReadById(candidates))
        .ifPresent(response -> {
          int i = 0;
          final Submission[] essence = response.essence();
          for (Submission s : essence) {
            if ("[deleted]".equals(s.author())) {
              i += shelve(runtime, s.id(), "deletion by submitter", didShelve);
            } else if (s.flair() != null && s.flair().startsWith("removed")) {
              i += shelve(runtime, s.id(), "removal by moderator", didShelve);
            }
          }
          if (i > 0) {
            Logic.info(runtime, CALLER_TASK, i + " of " + essence.length + " candidates were shelved");
            Logic.doOrLogVaultAction(runtime, CALLER_TASK,
                "Will remove submissions with IDs " + didShelve + " from vault",
                "Failed to remove submissions from vault",
                client -> client.deleteSubmissions(didShelve));
          }
        });
  }

  private static int shelve(SubmissionsFetchAgent runtime, String id36, String reason, Set<String> didShelve) {
    Logic.debug(runtime, CALLER_TASK, "Will shelve " + id36 + " due to " + reason);
    if (Shared.liveSubmissions().shelve(runtime, CALLER_TASK, id36)) {
      didShelve.add(id36);
      return 1;
    }
    return 0;
  }

  static long coalesceSubmissions(long until, Map<String, Submission> active,
                                  Map<String, List<Comment>> batches, Map<String, Integer> counts) {
    System.out.println("[TRACE] Coalescence#submissionsFetch: Begin coalesceSubmissions");
    if (!active.isEmpty() || !counts.isEmpty()) {
      throw new IllegalArgumentException("coalescenceSubmission args must be empty");
    }
    final GatherCoalesceTask task = new GatherCoalesceTask(until, active, batches, counts);
    task.run();
    return task.boundaryId;
  }

  /**
   * A task that uses Reddit's {@code r/whatsthisbird/new} endpoint to gather
   * the latest information about all live submissions.
   */
  private abstract static class GatherTask {

    final long until;

    private GatherTask(long until) {
      this.until = until;
    }

    void run() {
      gatherLiveSubmissions(RedditClient::fetchMaxUndocumentedPosts);
    }

    private void gatherLiveSubmissions(RedditClient.Callable<Submission[]> action) {
      gatherLiveTrace("Issuing fetch request");
      doFetch(action)
          .flatMap(response -> processBatch(response.essence()))
          .ifPresent(fullname -> gatherLiveSubmissions(client -> client.fetchUndocumentedPostsAfter(fullname)));
    }

    abstract Optional<RedditResponse<Submission[]>> doFetch(RedditClient.Callable<Submission[]> callable);

    abstract void gatherLiveTrace(String msg);

    final Optional<String> processBatch(Submission[] batch) {
      if (batch.length == 0) {
        return Optional.empty();
      }
      int expired = 0;
      String lastLiveId = null;
      for (Submission s : batch) {
        if (s.createdUtc() >= this.until) {
          lastLiveId = s.id();
          onLiveSubmission(s);
        } else {
          expired++;
          onExpiredSubmission(s);
        }
      }
      gatherLiveTrace("Processed " + batch.length + " batched submissions, including " + expired + " expired");
      return expired == batch.length ? Optional.empty() : Optional.of("t3_" + lastLiveId);
    }

    abstract void onLiveSubmission(Submission s);

    void onExpiredSubmission(Submission s) {
      // stub
    }

  }

  /**
   * {@code GatherTask} variation used from a {@code SubmissionsFetchAgent} and
   * additionally responsible for flagging possible discarded submissions.
   */
  private static class GatherAgentTask extends GatherTask {

    private final SubmissionsFetchAgent runtime;
    private final Map<String, Submission> active;
    private final Map<Long, Submission> shelfCandidates;

    private GatherAgentTask(long until, SubmissionsFetchAgent runtime,
                            Map<String, Submission> active, Map<Long, Submission> shelfCandidates) {
      super(until);
      this.runtime = runtime;
      this.active = active;
      this.shelfCandidates = shelfCandidates;
    }

    @Override
    Optional<RedditResponse<Submission[]>> doFetch(RedditClient.Callable<Submission[]> callable) {
      return Logic.doRedditCallable(this.runtime, "[GatherTask]", "getNewPosts",
          callable);
    }

    @Override
    void gatherLiveTrace(String msg) {
      // AgentLogic.trace() FIXME
    }

    @Override
    void run() {
      super.run();
      this.shelfCandidates.entrySet().removeIf(e -> e.getValue().createdUtc() * 1000L <= this.until);
    }

    @Override
    void onLiveSubmission(Submission s) {
      final long id10 = Utils.id36To10(s.id());
      this.shelfCandidates.remove(id10);
      if (!Shared.liveSubmissions().isShelved(id10)) {
        Shared.liveSubmissions().putActive(this.runtime, CALLER_TASK, id10, s);
        this.active.put(s.id(), s);
      }
    }

  }

  /**
   * {@code GatherTask} variation used to generate steady-state startup.
   */
  private static class GatherCoalesceTask extends GatherTask {

    private final Map<String, Submission> active;
    private final Map<String, List<Comment>> batches;
    private final Map<String, Integer> counts;
    private long boundaryId;

    private GatherCoalesceTask(long until, Map<String, Submission> active,
                               Map<String, List<Comment>> batches, Map<String, Integer> counts) {
      super(until);
      this.active = active;
      this.batches = batches;
      this.counts = counts;
      this.boundaryId = Long.MIN_VALUE;
    }

    @Override
    Optional<RedditResponse<Submission[]>> doFetch(RedditClient.Callable<Submission[]> callable) {
      return Logic.coalesceRedditCallable("getNewPosts", callable);
    }

    @Override
    void gatherLiveTrace(String msg) {
      System.out.println("[TRACE] Coalescence#submissionsFetch: " + msg);
    }

    @Override
    void onLiveSubmission(Submission s) {
      this.active.put(s.id(), s);
      if (!this.batches.containsKey(s.id())) {
        this.batches.put(s.id(), new ArrayList<>(s.commentCount()));
        this.counts.put(s.id(), s.commentCount());
      } else {
        System.out.println("[WARN] Coalescence#submissionsFetch: Unexpectedly saw " + s.id() + " multiple times");
      }

    }

    @Override
    void onExpiredSubmission(Submission s) {
      this.boundaryId = Math.max(Utils.id36To10(s.id()), this.boundaryId);
    }

  }

}
