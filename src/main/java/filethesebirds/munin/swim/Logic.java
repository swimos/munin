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
import filethesebirds.munin.connect.http.StatusCodeException;
import filethesebirds.munin.connect.reddit.ConcurrentTokenRefreshException;
import filethesebirds.munin.connect.reddit.RedditClient;
import filethesebirds.munin.connect.reddit.RedditResponse;
import filethesebirds.munin.connect.vault.VaultClient;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import swim.api.agent.AbstractAgent;
import swim.concurrent.AbstractTask;
import swim.concurrent.TaskRef;
import swim.concurrent.TimerRef;

/**
 * Utility class containing application-level convenience methods regarding the
 * Swim server's "business logic".
 */
final class Logic {

  private Logic() {
  }

  // ===========================================================================
  // Logging
  // ===========================================================================

  static void trace(AbstractAgent runtime, String caller, Object msg) {
    runtime.trace("[TRACE] " + runtime.nodeUri() + "#" + caller + ": " + msg);
  }

  static void debug(AbstractAgent runtime, String caller, Object msg) {
    runtime.debug("[DEBUG] " + runtime.nodeUri() + "#" + caller + ": " + msg);
  }

  static void info(AbstractAgent runtime, String caller, Object msg) {
    runtime.info("[INFO] " + runtime.nodeUri() + "#" + caller + ": " + msg);
  }

  static void warn(AbstractAgent runtime, String caller, Object msg) {
    runtime.warn("[WARN] " + runtime.nodeUri() + "#" + caller + ": " + msg);
  }

  static void error(AbstractAgent runtime, String caller, Object msg) {
    runtime.error("[ERROR] " + runtime.nodeUri() + "#" + caller + ": " + msg);
  }

  // ===========================================================================
  // Asynchronous tasks
  // ===========================================================================

  static void executeBlocker(AbstractAgent runtime, String caller, Runnable logic) {
    final TaskRef task = runtime.asyncStage().task(new AbstractTask() {

      @Override
      public void runTask() {
        final String innerCaller = caller + " (blocker)";
        try {
          logic.run();
          debug(runtime, innerCaller, "Blocking task executed successfully");
        } catch (Exception e) {
          error(runtime, innerCaller, "Blocking task execution failed");
          runtime.didFail(e);
        }
      }

      @Override
      public boolean taskWillBlock() {
        return true;
      }

    });
    task.cue();
  }

  static TimerRef scheduleRecurringBlocker(AbstractAgent runtime, String caller,
                                           Supplier<TimerRef> timerSupplier,
                                           long initialDelay, long period,
                                           Runnable logic) {
    if (cancelTimer(timerSupplier.get())) {
      debug(runtime, caller, "Preempted existing timer");
    }
    final TaskRef task = runtime.asyncStage().task(new AbstractTask() {

      @Override
      public void runTask() {
        final String innerCaller = caller + " (recurringBlocker)";
        trace(runtime, innerCaller, "Task begin");
        final long nextFire = System.currentTimeMillis() + period;
        try {
          logic.run();
          debug(runtime, innerCaller, "Task executed successfully");
        } catch (Exception e) {
          warn(runtime, innerCaller, "Task execution failed (timer remains active)");
          runtime.didFail(e);
        }
        final long next = Math.max(1000L, nextFire - System.currentTimeMillis());
        timerSupplier.get().reschedule(Math.max(1000L, nextFire - System.currentTimeMillis()));
        debug(runtime, innerCaller, "Task rescheduled for execution in " + next + " ms");
      }

      @Override
      public boolean taskWillBlock() {
        return true;
      }

    });
    debug(runtime, caller, "Recurring blocking task scheduled for execution in " + initialDelay + " ms");
    return runtime.setTimer(initialDelay, () -> {
      trace(runtime, caller, "Blocker-corresponding timer fire");
      if (!task.cue()) {
        error(runtime, caller, "Failed to cue blocking recurring task");
      }
    });
  }

  static boolean cancelTimer(TimerRef timer) {
    return timer != null && timer.cancel();
  }

  // ===========================================================================
  // External client calls
  // ===========================================================================

  static <V> Optional<RedditResponse<V>> doRedditCallable(AbstractAgent runtime, String caller, String actionName,
                                                          RedditClient.Callable<V> action) {
    return doRedditCallable(runtime, caller, actionName, action,
        e -> error(runtime, caller, "(Reddit " + actionName + ") " + formatStatusCodeExceptionMsg("", e)));
  }

  static <V> Optional<RedditResponse<V>> doRedditCallable(AbstractAgent runtime, String caller, String actionName,
                                                          RedditClient.Callable<V> action,
                                                          Consumer<StatusCodeException> onStatusCodeException) {
    debug(runtime, caller, "Will perform Reddit " + actionName);
    try {
      return Optional.of(action.call(Shared.redditClient()));
    } catch (ConcurrentTokenRefreshException e) {
      error(runtime, caller, "(Reddit " + actionName + ") Lost token fetch race");
    } catch (StatusCodeException e) {
      onStatusCodeException.accept(e);
    } catch (HttpConnectException e) {
      if (e.getCause() instanceof HttpTimeoutException) {
        error(runtime, caller, "(Reddit " + actionName + ") HTTP request timed out");
      } else {
        error(runtime, caller, "(Reddit " + actionName + ") Reddit client task encountered HTTP failure");
        runtime.didFail(e.getCause());
      }
    }
    return Optional.empty();
  }

  static <V> void executeRedditCallable(AbstractAgent runtime, String caller, String actionName,
                                       RedditClient.Callable<V> action) {
    executeBlocker(runtime, caller, () -> doRedditCallable(runtime, caller, actionName, action));
  }

  static <V> void executeRedditCallable(AbstractAgent runtime, String caller, String actionName,
                                        RedditClient.Callable<V> action,
                                        Consumer<RedditResponse<V>> ifPresent) {
    executeBlocker(runtime, caller, () -> doRedditCallable(runtime, caller, actionName, action)
        .ifPresent(ifPresent));
  }

  static <V> Optional<RedditResponse<V>> doRedditCallable(String actionName, RedditClient.Callable<V> action) {
    System.out.println("[DEBUG] Will perform Reddit " + actionName);
    try {
      return Optional.of(action.call(Shared.redditClient()));
    } catch (StatusCodeException e) {
      System.out.println("[ERROR] " + "(Reddit " + actionName + ") " + formatStatusCodeExceptionMsg("", e));
    } catch (HttpConnectException e) {
      if (e.getCause() instanceof HttpTimeoutException) {
        System.out.println("[ERROR] " + "(Reddit " + actionName + ") HTTP request timed out");
      } else {
        System.out.println("[ERROR] " + "(Reddit " + actionName + ") Reddit client task encountered HTTP failure");
        e.printStackTrace();
      }
    }
    return Optional.empty();
  }

  static Optional<RedditResponse<Void>> doRedditDelete(AbstractAgent runtime, String caller,
                                                       RedditClient.Callable<Void> action) {
    // FIXME: Reddit responds with 200 even when it should throw 404.
    //   If it ever changes to throw 404, add a custom onStatusCodeException arg to the call below
    return doRedditCallable(runtime, caller, "deleteComment", action);
  }

  static void executeRedditDelete(AbstractAgent runtime, String caller, RedditClient.Callable<Void> action) {
    executeBlocker(runtime, caller, () -> doRedditDelete(runtime, caller, action));
  }

  private static String formatStatusCodeExceptionMsg(String prefix, StatusCodeException e) {
    return prefix + "HTTP request failed with code="
        + e.status().code() + "; headers=" + e.headers();
  }

  static void doOrLogVaultAction(AbstractAgent runtime, String caller,
                                 String infoMsg, String failureMsg,
                                 Consumer<VaultClient> action) {
    info(runtime, caller, infoMsg);
    try {
      action.accept(Shared.vaultClient());
    } catch (Exception e) {
      error(runtime, caller, failureMsg);
      new Exception(runtime.nodeUri() + ": " + failureMsg, e)
          .printStackTrace();
      action.accept(VaultClient.DRY);
    }
  }

  static void executeOrLogVaultAction(AbstractAgent runtime, String caller,
                                      String infoMsg, String failureMsg,
                                      Consumer<VaultClient> action) {
    executeBlocker(runtime, caller, () -> doOrLogVaultAction(runtime, caller, infoMsg, failureMsg, action));
  }

}
