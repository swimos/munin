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

package filethesebirds.munin.digest.motion;

import java.util.concurrent.ConcurrentHashMap;

public final class HintCache {

  private HintCache() {
  }

  private static final int PRUNE_TRIGGER = 1800;
  private static final int MAX_ENTRIES = 2000;

  private static final ConcurrentHashMap<String, CacheValue> CACHE = new ConcurrentHashMap<>(MAX_ENTRIES);

  public static String get(String hint) {
    final CacheValue val = CACHE.get(hint);
    if (val == null) {
      return null;
    }
    val.hit();
    return val.code;
  }

  public static void put(String hint, String code) {
    CACHE.put(hint, new CacheValue(code));
  }

  public static void prune() {
    if (CACHE.size() >= PRUNE_TRIGGER) {
      System.out.println("[INFO] pruning cache");
      synchronized (CACHE) {
        purge(20);
        if (CACHE.size() >= PRUNE_TRIGGER) {
          purge(10);
        }
      }
    }
  }

  private static void purge(int lim) {
    CACHE.entrySet().removeIf(e -> e.getValue().hits < lim
        && Math.random() >= (double) (e.getValue().hits / lim));
  }

  private static class CacheValue {

    private final String code;
    private volatile int hits;

    private CacheValue(String code) {
      this.code = code;
      this.hits = 1;
    }

    private void hit() {
      this.hits++; // no atomicupdater since an approximation should be okay
    }

  }

}
