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
import filethesebirds.munin.digest.Submission;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.stream.Collectors;
import swim.api.agent.AbstractAgent;
import swim.api.plane.PlaneContext;
import swim.api.ref.WarpRef;

final class LiveSubmissions {

  private final NavigableMap<Long, Submission> active;
  private final NavigableMap<Long, Long> shelved;

  LiveSubmissions(NavigableMap<Long, Submission> active,
                  NavigableMap<Long, Long> shelved) {
    this.active = active;
    this.shelved = shelved;
  }

  Map<Long, Submission> activeSnapshot() {
    return new HashMap<>(this.active);
  }

  Submission getActive(long id10) {
    return this.active.get(id10);
  }

  Submission getActive(String id36) {
    return getActive(Utils.id36To10(id36));
  }

  Map.Entry<Long, Submission> getEarliestActive() {
    return this.active.firstEntry();
  }

  Map.Entry<Long, Long> getEarliestShelved() {
    return this.shelved.firstEntry();
  }

  long getEarliest() {
    final Map.Entry<Long, ?> active = getEarliestActive(),
        shelved = getEarliestShelved();
    return active == null ? (shelved == null ? -1L : shelved.getKey())
        : (shelved == null ? active.getKey() : Math.min(active.getKey(), shelved.getKey()));
  }

  Map.Entry<Long, Submission> getLatestActive() {
    return this.active.lastEntry();
  }

  Map.Entry<Long, Long> getLatestShelved() {
    return this.shelved.lastEntry();
  }

  long getLatest() {
    final Map.Entry<Long, ?> active = getLatestActive(),
        shelved = getLatestShelved();
    return active == null ? (shelved == null ? -1L : shelved.getKey())
        : (shelved == null ? active.getKey() : Math.max(active.getKey(), shelved.getKey()));
  }

  boolean isShelved(long id10) {
    return this.shelved.containsKey(id10);
  }

  boolean isShelved(String id36) {
    return isShelved(Utils.id36To10(id36));
  }

  void expire(AbstractAgent runtime, String caller, long id10) {
    final NavigableMap<Long, Submission> willExpire = this.active.headMap(id10, true);
    Logic.info(runtime, caller, "Will expire submissions with IDs " + willExpire.keySet().stream()
        .map(Utils::id10To36)
        .collect(Collectors.joining(",")));
    willExpire.forEach((id, submission) -> {
      runtime.command("/live", "expireSubmission", );
    });
    willExpire.clear();
    this.shelved.headMap(id10, true).clear();
  }

  boolean shelve(AbstractAgent runtime, String caller, long id10) {
    return shelve(runtime, caller, id10, Utils.id10To36(id10));
  }

  boolean shelve(AbstractAgent runtime, String caller, String id36) {
    return shelve(runtime, caller, Utils.id36To10(id36), id36);
  }

  private boolean shelve(AbstractAgent runtime, String caller, long id10, String id36) {
    final Submission oldValue = this.active.remove(id10);
    if (oldValue != null) {
      this.shelved.put(id10, oldValue.createdUtc());
      Logic.debug(runtime, caller, "Shelved previously active liveSubmission " + id36);
      runtime.command("/live", "shelveSubmission", );
      return true;
    } else {
      Logic.warn(runtime, caller, "Attempted to shelve nonexistent or already-shelved submission with ID " + id36);
      return false;
    }
  }

  void putActive(AbstractAgent runtime, String caller, long id10, Submission submission) {
    putActive(runtime, id10, submission,
        () -> Logic.debug(runtime, caller, "Created new active submission " + submission));
  }

  void putActive(PlaneContext runtime, long id10, Submission submission) {
    putActive(runtime, id10, submission, () -> { });
  }

  private void putActive(WarpRef runtime, long id10, Submission submission,
                         Runnable onAddLog) {
    if (this.active.put(id10, submission) != null) {
      onAddLog.run();
    }
    runtime.command("/submission/" + submission.id(), "info",
        Submission.form().mold(submission).toValue());
  }

}
