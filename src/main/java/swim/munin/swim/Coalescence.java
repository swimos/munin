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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import swim.api.ref.WarpRef;
import swim.munin.MuninEnvironment;
import swim.munin.Utils;
import swim.munin.connect.reddit.Comment;
import swim.munin.connect.reddit.RedditClient;
import swim.munin.connect.reddit.Submission;
import swim.structure.Form;
import swim.structure.Text;

public class Coalescence {

  private static final Form<List<Comment>> FORM_LIST_COMMENT = Form.forList(Comment.form());

  private final long lookbackSeconds;
  private final long until;
  private final RedditClient redditClient;
  private final Map<String, Submission> active;
  private final Map<String, List<Comment>> batches;
  private final Map<String, Integer> counts;
  private final Map<String, Long> shelved;
  private Comment bookmark = null;
  private final WarpRef swim;

  private Coalescence(MuninEnvironment environment, RedditClient redditClient, WarpRef swim) {
    this.lookbackSeconds = environment.lookbackSeconds();
    this.redditClient = redditClient;
    this.until = System.currentTimeMillis() / 1000L - this.lookbackSeconds;
    this.active = new HashMap<>(256);
    this.batches = new HashMap<>(256);
    this.counts = new HashMap<>(256);
    this.shelved = new HashMap<>();
    this.swim = swim;
  }

  private long getSubmissions() {
    final long boundary = SubmissionsFetchLogic.coalesceSubmissions(this.until, this.redditClient,
        this.active, this.batches, this.counts);
    System.out.println("[INFO] Coalescence#submissionsFetch: Identified " + this.active.size() + " active submissions");
    return boundary;
  }

  private Comment getComments(long boundaryId10) {
    return CommentsFetchLogic.coalesceComments(this.until, this.redditClient, boundaryId10,
        this.active, this.batches, this.counts, this.shelved);
  }

  private void logIncompleteSubmissions(long oldestCommentTimestamp) {
    final Set<String> incomplete = this.counts.entrySet().stream()
        .filter(e -> e.getValue() > 0 && this.active.get(e.getKey()).createdUtc() < oldestCommentTimestamp)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
    if (!incomplete.isEmpty()) {
      System.out.println("[WARN] Coalescence#submissionsFetch: Submission(s) "
          + String.join(",", incomplete)
          + " are incompletely commented and may behave erratically");
    }
  }

  public LiveSubmissions toLiveSubmissions() {
    return new LiveSubmissions(this.lookbackSeconds, map36To10(this.active), map36To10(this.shelved));
  }

  private static <V> ConcurrentSkipListMap<Long, V> map36To10(Map<String, V> map36) {
    final ConcurrentSkipListMap<Long, V> result = new ConcurrentSkipListMap<>();
    map36.forEach((k, v) -> result.put(Utils.id36To10(k), v));
    return result;
  }

  public void startSubmissionAgents() {
    for (Map.Entry<String, List<Comment>> entry : this.batches.entrySet()) {
      final String nodeUri = "/submission/" + entry.getKey();
      this.swim.command(nodeUri, "info",
          Submission.form().mold(this.active.get(entry.getKey())).toValue());
      if (entry.getValue() != null && !entry.getValue().isEmpty()) {
        this.swim.command(nodeUri, "addManyComments",
            FORM_LIST_COMMENT.mold(entry.getValue()).toValue());
        // TODO: consider a Thread.sleep() here
      }
    }
  }

  public void startFetchTasks() {
    swim.command("/submissions", "preemptSubmissionsFetch", Text.from("preempt"));
    swim.command("/submissions", "preemptCommentsFetch", Comment.form().mold(this.bookmark).toValue());
  }

  public static Coalescence coalesce(MuninEnvironment environment, RedditClient redditClient,
                                     WarpRef swim) {
    final Coalescence coalesce = new Coalescence(environment, redditClient, swim);
    final long boundary = coalesce.getSubmissions();
    coalesce.bookmark = coalesce.getComments(boundary);
    coalesce.logIncompleteSubmissions(coalesce.bookmark.createdUtc());
    return coalesce;
  }

}
