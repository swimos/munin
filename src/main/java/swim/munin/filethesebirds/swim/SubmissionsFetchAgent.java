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

import swim.concurrent.TimerRef;
import swim.munin.MuninEnvironment;
import swim.munin.swim.AbstractSubmissionsFetchAgent;
import swim.munin.swim.LiveSubmissions;
import swim.munin.swim.Logic;
import swim.munin.swim.SubmissionsFetchLogic;
import static swim.munin.swim.SubmissionsFetchLogic.RunResult;

/**
 * A singleton Web Agent that fetches new submissions to r/WhatsThisBird and
 * routes the resulting information for processing by appropriate {@link
 * SubmissionAgent SubmissionAgents}.
 *
 * <p>The {@code SubmissionsFetchAgent} is liable for the following {@link
 * LiveSubmissions} actions:
 * <ul>
 * <li>Inserting up-to-date information about all active submissions
 * <li>Shelving submitter-deleted and moderator-removed submissions that have
 * received no comments since their disappearance
 * </ul>
 * and the following vault actions:
 * <ul>
 * <li>Upserting the latest info about all active live submissions
 * <li>Deleting each submission (cascaded to its observations) that moves to the
 * {@code shelved} status from {@code active}
 * </ul>
 */
public class SubmissionsFetchAgent extends AbstractSubmissionsFetchAgent {

  protected TimerRef fetchTimer;

  protected TimerRef fetchTimer() {
    return this.fetchTimer;
  }

  @Override
  public MuninEnvironment environment() {
    return Shared.muninEnvironment();
  }

  @Override
  public LiveSubmissions liveSubmissions() {
    return Shared.liveSubmissions();
  }

  @Override
  protected void fetchTimerAction() {
    final RunResult run = SubmissionsFetchLogic.doRun(this, environment(), liveSubmissions());
    if (!run.live().isEmpty()) {
      Logic.doOrLogVaultAction(this, "[GatherSubmissionsTask]",
          "Will upsert " + run.live().size() + " submissions into vault",
          "Failed to upsert submissions",
          client -> client.upsertSubmissions(run.live().values()));
    }
    if (!run.didShelve().isEmpty()) {
      Logic.doOrLogVaultAction(this, "[GatherSubmissionsTask]",
          "Will remove submissions with IDs " + run.didShelve() + " from vault",
          "Failed to remove submissions from vault",
          client -> client.deleteSubmissions36(run.didShelve()));
    }
  }

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
  }

}
