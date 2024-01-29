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

import swim.munin.Utils;
import swim.munin.connect.reddit.Submission;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import swim.api.agent.AbstractAgent;
import swim.munin.filethesebirds.swim.MuninConstants;
import swim.munin.filethesebirds.swim.SubmissionsAgent;

public final class LiveSubmissions {

  private final NavigableMap<Long, Submission> active;
  private final NavigableMap<Long, Long> shelved;

  public LiveSubmissions(NavigableMap<Long, Submission> active,
                         NavigableMap<Long, Long> shelved) {
    final long then = System.currentTimeMillis() / 1000L - MuninConstants.lookbackSeconds();
    active.entrySet().removeIf(e -> e.getValue().createdUtc() <= then);
    shelved.entrySet().removeIf(e -> e.getValue() <= then);
    this.active = active;
    this.shelved = shelved;
  }

  public Map<Long, Submission> activeSnapshot() {
    return new HashMap<>(this.active);
  }

  public Submission getActive(long id10) {
    return this.active.get(id10);
  }

  public Submission getActive(String id36) {
    return getActive(Utils.id36To10(id36));
  }

  public Map.Entry<Long, Submission> getEarliestActive() {
    return this.active.firstEntry();
  }

  public Map.Entry<Long, Long> getEarliestShelved() {
    return this.shelved.firstEntry();
  }

  public long getEarliest() {
    final Map.Entry<Long, ?> active = getEarliestActive(),
        shelved = getEarliestShelved();
    return active == null ? (shelved == null ? -1L : shelved.getKey())
        : (shelved == null ? active.getKey() : Math.min(active.getKey(), shelved.getKey()));
  }

  public Map.Entry<Long, Submission> getLatestActive() {
    return this.active.lastEntry();
  }

  public Map.Entry<Long, Long> getLatestShelved() {
    return this.shelved.lastEntry();
  }

  public long getLatest() {
    final Map.Entry<Long, ?> active = getLatestActive(),
        shelved = getLatestShelved();
    return active == null ? (shelved == null ? -1L : shelved.getKey())
        : (shelved == null ? active.getKey() : Math.max(active.getKey(), shelved.getKey()));
  }

  public boolean isShelved(long id10) {
    return this.shelved.containsKey(id10);
  }

  public boolean isShelved(String id36) {
    return isShelved(Utils.id36To10(id36));
  }

  public Set<String> expire(SubmissionsAgent runtime) {
    final long then = System.currentTimeMillis() / 1000L - MuninConstants.lookbackSeconds();
    final Set<String> result = new HashSet<>();
    for (Iterator<Map.Entry<Long, Submission>> itr = this.active.entrySet().iterator();
         itr.hasNext(); ) {
      final Map.Entry<Long, Submission> entry = itr.next();
      if (entry.getValue().createdUtc() <= then) {
        final String id36 = entry.getValue().id();
        itr.remove();
        Logic.info(runtime, "[expiryTimer]", "Expired active submission " + id36 + ", will notify SubmissionAgent");
        result.add(id36);
      }
    }
    for (Iterator<Map.Entry<Long, Long>> itr = this.shelved.entrySet().iterator();
         itr.hasNext(); ) {
      final Map.Entry<Long, Long> entry = itr.next();
      if (entry.getValue() <= then) {
        itr.remove();
        Logic.info(runtime, "[expiryTimer]", "Expired shelved submission " + Utils.id10To36(entry.getKey()));
      }
    }
    return result;
  }

  public boolean shelve(AbstractAgent runtime, String caller, long id10) {
    return shelve(runtime, caller, id10, Utils.id10To36(id10));
  }

  public boolean shelve(AbstractAgent runtime, String caller, String id36) {
    return shelve(runtime, caller, Utils.id36To10(id36), id36);
  }

  private boolean shelve(AbstractAgent runtime, String caller, long id10, String id36) {
    final Submission oldValue = this.active.remove(id10);
    if (oldValue != null) {
      this.shelved.put(id10, oldValue.createdUtc());
      Logic.debug(runtime, caller, "Shelved previously active liveSubmission " + id36);
      return true;
    } else {
      Logic.warn(runtime, caller, "Attempted to shelve nonexistent or already-shelved submission with ID " + id36);
      return false;
    }
  }

  public void putActive(AbstractAgent runtime, String caller, long id10, Submission submission) {
    if (this.active.put(id10, submission) == null) {
      Logic.debug(runtime, caller, "Created new active submission " + submission);
    }
  }

}
