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

import filethesebirds.munin.connect.reddit.RedditApiException;
import filethesebirds.munin.connect.reddit.RedditClient;
import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.Submission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import swim.api.ref.WarpRef;
import swim.structure.Form;
import swim.structure.Text;

public class Coalesce {

  private Coalesce() {
  }

  // returns the after parameter for the next fetch query, or null if we're
  // finished
  private static String fetchBatchPosts(RedditClient client, long until, String after, List<Submission> into,
                                        Map<String, Integer> counts)
      throws RedditApiException {
    final Submission[] batch = (after == null ? client.fetchMaxUndocumentedPosts() : client.fetchUndocumentedPostsAfter(after))
        .essence();
    if (batch.length == 0) {
      return null;
    }
    for (Submission s : batch) {
      if (s.createdUtc() >= until) {
        into.add(s);
        counts.put(s.id(), s.commentCount());
      } else {
        return null;
      }
    }
    return batch.length < 100 ? null : "t3_" + into.get(into.size() - 1).id();
  }

  private static List<Submission> fetchPosts(RedditClient client, long until,
                                             Map<String, Integer> counts)
      throws Exception {
    String after = null;
    final List<Submission> into = new ArrayList<>(1000);
    while (true) {
      after = fetchBatchPosts(client, until, after, into, counts);
      if (after == null) {
        break;
      }
      Thread.sleep(3000L);
    }
    return into;
  }

  // returns the after parameter for the next fetch query, or null if we're
  // finished
  private static String fetchBatchComments(RedditClient client, long until,
                                           String after, List<Comment> into,
                                           String oldestPostId)
      throws RedditApiException {
    final Comment[] batch = (after == null ? client.fetchMaxUndocumentedComments() : client.fetchUndocumentedCommentsAfter(after))
        .essence();
    if (batch.length == 0) {
      return null;
    }
    for (Comment c : batch) {
      if (c.createdUtc() >= until) {
        if (compareId36(c.submissionId(), oldestPostId) >= 0L) {
          into.add(c);
        } else {
          System.out.println("ignored comment for too old a post: " + c);
        }
      } else {
        return null;
      }
    }
    return batch.length < 100 ? null : "t1_" + into.get(into.size() - 1).id();
  }

  private static List<Comment> fetchComments(RedditClient client, long until,
                                             String oldestPostId)
      throws Exception {
    String after = null;
    System.out.println("oldest post id: " + oldestPostId);
    final List<Comment> into = new ArrayList<>(1000);
    while (true) {
      after = fetchBatchComments(client, until, after, into, oldestPostId);
      if (after == null) {
        break;
      }
      System.out.println("last comment fetched before sleep (3s): " + into.get(into.size() - 1).body());
      Thread.sleep(3000L);
    }
    return into;
  }

  private static Map<String, List<Comment>> makeBatches(List<Comment> comments,
                                                        Map<String, Integer> counts,
                                                        Set<Comment> unassigned) {
    final Map<String, List<Comment>> batches = new HashMap<>(2 * counts.size());
    for (Comment comment : comments) {
      final String submissionId = comment.submissionId();
      if (counts.containsKey(submissionId)) {
        final List<Comment> batch;
        if (!batches.containsKey(submissionId)) {
          batch = new ArrayList<>();
          batches.put(submissionId, batch);
        } else {
          batch = batches.get(submissionId);
        }
        batch.add(comment);
        final int count = counts.get(submissionId);
        if (count == 1) {
          counts.remove(submissionId);
        } else {
          counts.put(submissionId, count - 1);
        }
      } else {
        unassigned.add(comment);
      }
    }
    return batches;
  }

  private static Map<String, Submission> submissionsMap(List<Submission> posts) {
    final Map<String, Submission> result = new HashMap<>(posts.size());
    for (Submission s : posts) {
      result.put(s.id(), s);
    }
    return result;
  }

  public static void coalesce(WarpRef swim) throws Exception {
    final long until = System.currentTimeMillis() / 1000L - MuninConstants.lookbackSeconds();
    // fetch as many posts as we can from undocumented endpoint
    final Map<String, Integer> counts = new HashMap<>(1000);
    final List<Submission> posts = fetchPosts(Shared.redditClient(),
        until, counts);
    final Submission oldestPost = posts.get(posts.size() - 1);
    final Map<String, Submission> submissionsMap = submissionsMap(posts);
    // fetch as many comments as we can from undocumented endpoint
    final List<Comment> comments = fetchComments(Shared.redditClient(), until, oldestPost.id());
    final long oldestCommentTime = comments.get(comments.size() - 1).createdUtc();
    System.out.println("oldest comment: " + oldestCommentTime + ", " + comments.get(comments.size() - 1));

    // map submission IDs to their comments
    final Set<Comment> unassigned = new HashSet<>();
    final Map<String, List<Comment>> batches = makeBatches(comments, counts, unassigned);
    System.out.println("begin unassigned comments:");
    final Submission newestPost = posts.get(0);
    for (Comment c : unassigned) {
      if (compareId36(c.submissionId(), newestPost.id()) > 0) {
        final List<Comment> batch;
        if (!batches.containsKey(c.submissionId())) {
          batch = new ArrayList<>();
          batches.put(c.submissionId(), batch);
        } else {
          batch = batches.get(c.submissionId());
        }
        batch.add(c);
      }
    }
    System.out.println("end unassigned comments:");

    // jumpstart uncommented SubmissionAgents
    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      if (entry.getValue() == 0) {
        System.out.println(entry.getKey() + " had 0 comments");
        swim.command("/submission/" + entry.getKey(), "info",
            Submission.form().mold(submissionsMap.get(entry.getKey())).toValue());
      }
    }
    // jumpstart submissions whose comments were fully included in fetch
    final Form<List<Comment>> listCommentForm = Form.forList(Comment.form());
    for (Submission submission : posts) {
      if (!counts.containsKey(submission.id())) { // Explicitly saw all comments
        System.out.println(submission.id() + " (" + submission.commentCount() + " comments)");
        swim.command("/submission/" + submission.id(), "info",
            Submission.form().mold(submission).toValue());
        swim.command("/submission/" + submission.id(), "addManyComments",
            listCommentForm.mold(batches.get(submission.id())).toValue());
        Thread.sleep(1200);
      } else if (submission.createdUtc() >= oldestCommentTime) { // Some comments deleted/removed
        System.out.println(submission.id() + " (" + submission.commentCount() + " comments, including some removed)");
        swim.command("/submission/" + submission.id(), "info",
            Submission.form().mold(submission).toValue());
        swim.command("/submission/" + submission.id(), "addManyComments",
            listCommentForm.mold(batches.get(submission.id())).toValue());
        Thread.sleep(1200);
      } else {
        System.out.println("Mildly problematic submission: " + submission.id());
      }
    }
    // start periodic fetch logic
    swim.command("/commentsFetch", "startFetching",
        Comment.form().mold(comments.get(0)).toValue());
    swim.command("/submissionsFetch", "preemptFetch", Text.from("preempt"));
    swim.command("/throttledPublish", "startTimer", Text.from("start"));
  }

  private static long compareId36(String id1, String id2) {
    return Long.parseLong(id1, 36) - Long.parseLong(id2, 36);
  }

}
