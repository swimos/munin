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

package filethesebirds.munin.swim.task;

import filethesebirds.munin.connect.ebird.EBirdApiException;
import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.Motion;
import filethesebirds.munin.digest.motion.EBirdExtractPurify;
import filethesebirds.munin.digest.motion.Extract;
import filethesebirds.munin.digest.motion.Review;
import filethesebirds.munin.swim.Shared;
import swim.api.agent.AbstractAgent;
import swim.api.lane.MapLane;
import swim.concurrent.AbstractTask;
import swim.concurrent.TaskRef;
import swim.structure.Record;
import swim.structure.Value;

public final class PhasedPurifyTask {

  private static final int MAX_EXPLORABLE_HINTS = 10;
  private static final int MAX_FAILURES = 5;

  private volatile Extract soFar;
  private volatile int hintsSoFar;
  private volatile int failures;
  private final TaskRef task;

  public PhasedPurifyTask(AbstractAgent runtime, Comment comment, Extract soFar,
                          MapLane<Value, Motion> motions) {
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
              System.out.println(runtime.nodeUri() + ": exception in processing hint for comment " + comment
                  + ", retrying in ~1 min");
              runtime.setTimer(60000L + (int) (Math.random() * 30000),
                  PhasedPurifyTask.this.task::cue);
            } else {
              System.out.println(runtime.nodeUri() + ": exception in processing hint for comment " + comment
                  + ", aborting.");
            }
            return;
          }
        }
        // On success
        final Motion purified = getSoFar().base();
        if ((purified instanceof Review) || !purified.isEmpty()) {
          System.out.println(runtime.nodeUri() + ": purified extract into " + purified);
          final Value laneKey = Record.create(2).item(comment.createdUtc()).item(comment.id());
          motions.put(laneKey, purified);
        } else {
          System.out.println(runtime.nodeUri() + ": failed to purify " + comment);
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

  public boolean cue() {
    return this.task.cue();
  }

}
