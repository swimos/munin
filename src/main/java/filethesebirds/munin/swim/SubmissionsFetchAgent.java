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

import filethesebirds.munin.connect.http.HttpConnectException;
import filethesebirds.munin.connect.reddit.RedditApiException;
import filethesebirds.munin.connect.reddit.RedditResponse;
import filethesebirds.munin.connect.vault.VaultClient;
import filethesebirds.munin.digest.Submission;
import swim.adapter.common.RelayException;
import swim.adapter.common.ingress.IngestingAgent;
import swim.api.SwimLane;
import swim.api.lane.CommandLane;
import swim.api.ref.WarpRef;
import swim.structure.Value;

/**
 * A Web Agent that periodically fetches new submissions to r/WhatsThisBird,
 * but may be preemptively triggered to execute outside its usual schedule.
 */
public class SubmissionsFetchAgent extends IngestingAgent<RedditResponse<Submission[]>> {

  private volatile String latestFetched = null;

  @SwimLane("preemptFetch")
  CommandLane<Value> preemptFetch = this.<Value>commandLane()
      .onCommand(v -> {
        if (v.isDistinct()) {
          prepareForReception();
        }
      });

  @Override
  public RedditResponse<Submission[]> fetch() throws RelayException {
    try {
      return Shared.redditClient().fetchMaxUndocumentedPosts();
    } catch (RedditApiException | HttpConnectException e) {
      throw new RelayException("Failed to fetch", e, false);
    }
  }

  @Override
  public void prepareForReception() {
    System.out.println(nodeUri() + ": fetch reset");
    cancelPeriodicDuty(null);
    schedulePeriodicDuty(this::fetchThenRelay, 3000L, 180000L);
  }

  @Override
  public void relayReceiptToSwim(WarpRef warpRef, RedditResponse<Submission[]> r)
      throws RelayException {
    final Submission[] submissions;
    try {
      submissions = r.essence();
    } catch (Throwable e) {
      throw new RelayException("Failed to relay", e, false);
    }
    if (submissions == null || submissions.length == 0) {
      return;
    }
    final Submission first = submissions[0];
    if (!first.id().equals(latestFetched)) {
      latestFetched = first.id();
    }
    final long now = System.currentTimeMillis();
    for (Submission submission : submissions) {
      if (now - submission.createdUtc() * 1000L <= MuninConstants.lookbackMillis()) {
        command("/submission/" + submission.id(), "info",
            Submission.form().mold(submission).toValue());
      }
    }
    // Can safely do this because we're already in an asyncStage block
    try {
      Shared.vaultClient().upsertSubmissions(submissions);
    } catch (Exception e) {
      new Exception(nodeUri() + ": failed to upsert", e).printStackTrace();
      VaultClient.DRY.upsertSubmissions(submissions);
    }
  }

  @Override
  public void didStart() {
    System.out.println(nodeUri() + ": didStart " + this.getClass());
  }

}
