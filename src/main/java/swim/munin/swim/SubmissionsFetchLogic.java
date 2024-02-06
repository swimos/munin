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

package swim.munin.swim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import swim.munin.MuninEnvironment;
import swim.munin.Utils;
import swim.munin.connect.reddit.Comment;
import swim.munin.connect.reddit.RedditClient;
import swim.munin.connect.reddit.RedditResponse;
import swim.munin.connect.reddit.Submission;
import swim.structure.Text;
import swim.structure.Value;

/**
 * Utility class focused on logic pertaining to fetching new Reddit submissions.
 */
public final class SubmissionsFetchLogic {

  private static final String CALLER_TASK = "[GatherSubmissionsTask]";
  private static final Value SHELVE_PAYLOAD = Text.from("shelve");

  private SubmissionsFetchLogic() {
  }

  public static RunResult doRun(AbstractSubmissionsFetchAgent runtime,
                                MuninEnvironment environment,
                                LiveSubmissions liveSubmissions) {
    final long until = (System.currentTimeMillis() - environment.lookbackMillis()) / 1000L;
    final Map<String, Submission> liveCandidates = new HashMap<>(256);
    final Map<Long, Submission> shelfCandidates = liveSubmissions.activeSnapshot();

    // Gather (fetch active submissions into liveSubmissions and identify shelf candidates)
    Logic.trace(runtime, CALLER_TASK, "Will seek submissions through epoch (s) " + until);
    new GatherAgentTask(until, runtime, liveSubmissions, liveCandidates, shelfCandidates).run();
    Logic.debug(runtime, CALLER_TASK, "Gathered " + liveCandidates.size() + " live submissions through epoch (s) " + until);

    // Shelve (update liveSubmissions#shelved)
    final Set<String> didShelve;
    if (liveCandidates.size() > 0 && !shelfCandidates.isEmpty()) {
      final String joinedCandidates = shelfCandidates.keySet().stream()
          .map(k -> "t3_" + Utils.id10To36(k))
          .collect(Collectors.joining(","));
      Logic.info(runtime, CALLER_TASK, "Will check submissions " + joinedCandidates + " for shelving");
      didShelve = new HashSet<>(shelfCandidates.size());
      shelve(runtime, liveSubmissions, joinedCandidates, didShelve);
      liveCandidates.keySet().removeAll(didShelve);
    } else {
      didShelve = Collections.emptySet();
    }
    return new RunResult() {

      @Override
      public Map<String, Submission> live() {
        return liveCandidates;
      }

      @Override
      public Set<String> didShelve() {
        return didShelve;
      }

    };
  }

  private static void shelve(AbstractSubmissionsFetchAgent runtime, LiveSubmissions liveSubmissions,
                             String candidates, Set<String> didShelve) {
    Logic.doRedditCallable(runtime, CALLER_TASK, "getById", runtime.redditClient(), client -> client.fetchReadById(candidates))
        .ifPresent(response -> {
          int i = 0;
          final Submission[] essence = response.essence();
          for (Submission s : essence) {
            if ("[deleted]".equals(s.author())) {
              i += shelve(runtime, liveSubmissions, s.id(), "deletion by submitter", didShelve);
            } else if (s.flair() != null && s.flair().startsWith("removed")) {
              i += shelve(runtime, liveSubmissions, s.id(), "removal by moderator", didShelve);
            }
          }
          if (i > 0) {
            Logic.info(runtime, CALLER_TASK, i + " of " + essence.length + " candidates were shelved, "
                + "notifying SubmissionAgents (id36s=" + didShelve + ")");
            didShelve.forEach(s -> {
              runtime.command("/submission/" + s, "shelve", SHELVE_PAYLOAD);
            });
          }
        });
  }

  private static int shelve(AbstractSubmissionsFetchAgent runtime, LiveSubmissions liveSubmissions,
                            String id36, String reason, Set<String> didShelve) {
    Logic.debug(runtime, CALLER_TASK, "Will shelve " + id36 + " due to " + reason);
    if (liveSubmissions.shelve(runtime, CALLER_TASK, id36)) {
      didShelve.add(id36);
      return 1;
    }
    return 0;
  }

  public static long coalesceSubmissions(long until, RedditClient redditClient,
                                         Map<String, Submission> active, Map<String, List<Comment>> batches,
                                         Map<String, Integer> counts) {
    System.out.println("[TRACE] Coalescence#submissionsFetch: Begin coalesceSubmissions");
    if (!active.isEmpty() || !counts.isEmpty()) {
      throw new IllegalArgumentException("coalescenceSubmission args must be empty");
    }
    final GatherCoalesceTask task = new GatherCoalesceTask(until, redditClient, active, batches, counts);
    task.run();
    return task.boundaryId;
  }

  public interface RunResult {

    Map<String, Submission> live();

    Set<String> didShelve();

  }

  /**
   * A short-lived object that wraps a task which may use multiple consecutive
   * Reddit API calls to gather all submissions to a subreddit that were created
   * after a provided timestamp.
   */
  public abstract static class GatherTask {

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
  public static class GatherAgentTask extends GatherTask {

    private final AbstractSubmissionsFetchAgent runtime;
    private final LiveSubmissions liveSubmissions;
    private final Map<String, Submission> active;
    private final Map<Long, Submission> shelfCandidates;

    private GatherAgentTask(long until, AbstractSubmissionsFetchAgent runtime, LiveSubmissions liveSubmissions,
                            Map<String, Submission> active, Map<Long, Submission> shelfCandidates) {
      super(until);
      this.runtime = runtime;
      this.liveSubmissions = liveSubmissions;
      this.active = active;
      this.shelfCandidates = shelfCandidates;
    }

    @Override
    Optional<RedditResponse<Submission[]>> doFetch(RedditClient.Callable<Submission[]> callable) {
      return Logic.doRedditCallable(this.runtime, CALLER_TASK, "getNewPosts", runtime.redditClient(), callable);
    }

    @Override
    void gatherLiveTrace(String msg) {
      // AgentLogic.trace() FIXME
    }

    @Override
    void run() {
      super.run();
      if (this.active.size() > 900) {
        Logic.warn(this.runtime, CALLER_TASK, "Potentially missed some comments due to API restrictions");
      }
      this.shelfCandidates.entrySet().removeIf(e -> e.getValue().createdUtc() <= this.until);
    }

    @Override
    void onLiveSubmission(Submission s) {
      final long id10 = Utils.id36To10(s.id());
      this.shelfCandidates.remove(id10);
      if (!this.liveSubmissions.isShelved(id10)) {
        this.liveSubmissions.putActive(this.runtime, CALLER_TASK, id10, s);
        this.active.put(s.id(), s);
        this.runtime.command("/submission/" + s.id(), "info",
            Submission.form().mold(s).toValue());
      }
    }

  }

  /**
   * {@code GatherTask} variation used to generate steady-state startup.
   */
  public static class GatherCoalesceTask extends GatherTask {

    private final RedditClient redditClient;
    private final Map<String, Submission> active;
    private final Map<String, List<Comment>> batches;
    private final Map<String, Integer> counts;
    private long boundaryId;

    private GatherCoalesceTask(long until, RedditClient redditClient,
                               Map<String, Submission> active, Map<String, List<Comment>> batches,
                               Map<String, Integer> counts) {
      super(until);
      this.redditClient = redditClient;
      this.active = active;
      this.batches = batches;
      this.counts = counts;
      this.boundaryId = Long.MIN_VALUE;
    }

    @Override
    Optional<RedditResponse<Submission[]>> doFetch(RedditClient.Callable<Submission[]> callable) {
      return Logic.doRedditCallable("getNewPosts", this.redditClient, callable);
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
