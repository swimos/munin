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
import java.util.Iterator;
import java.util.Set;
import swim.http.HttpRequest;
import swim.http.HttpResponse;
import swim.http.HttpStatus;
import swim.http.MediaType;
import swim.structure.Text;
import swim.structure.Value;

final class SubmissionsAgentLogic {

  private static final long EXPIRY_PERIOD_MS = 15L * 60 * 1000;

  private static final String UNANSWERED_PAGE_FMT = "<!doctypehtml><title>munin/unanswered</title><h2>Recent Uncatalogued Submissions</h2>"
      + "<div><h3>Links</h3><ul>%s</ul></div>"
      + "<div>"
        + "<h3>Notes</h3>"
        + "<ul><li>The links utilize Reddit's <i>by_id</i> API to provide uncatalogued submissions from the last 36 hours ordered chronologically, 20 submissions per link."
        + "<li>The links <i>probably will not</i> load in Reddit apps (first-party or otherwise) but should work in any standard browser."
        + "<li>If a <strong>removed or deleted</strong> post shows up in the list, you may leave a comment on the post to accelerate its removal."
        + "<li>Expect frequent changes to this work-in-progress page."
        + "</ul>"
      + "</div>";

  private SubmissionsAgentLogic() {
  }

  static void statusesDidUpdate(SubmissionsAgent runtime, long k, Value n, Value o) {
    Logic.trace(runtime, "statuses", "Begin didUpdate(" + k + ", " + n + ", " + o + ")");
    final String id = n.get("id").stringValue(null);
    if (id == null) {
      Logic.warn(runtime, "statuses", "Ignoring empty update under key " + k);
      return;
    }
    final long id10 = Utils.id36To10(id);
    final Value taxa = n.get("taxa");
    final Value reviewers = n.get("reviewers");
    if (taxa.isDistinct()) {
      if (reviewers.isDistinct()) {
        Logic.debug(runtime, "statuses", "Received update to reviewed submission " + id);
        runtime.unreviewed.remove(id10);
        runtime.reviewed.put(id10, n);
      } else {
        Logic.debug(runtime, "statuses", "Received update to answered (but unreviewed) submission " + id);
      }
      runtime.unanswered.remove(id10);
      runtime.answered.put(id10, n);
    } else {
      Logic.debug(runtime, "statuses", "Received update to unanswered submission " + id);
      runtime.reviewed.remove(id10);
      runtime.answered.remove(id10);
      runtime.unreviewed.put(id10, n);
      runtime.unanswered.put(id10, n);
    }
    Logic.trace(runtime, "statuses", "End didUpdate()");
  }

  static void subscribeOnCommand(SubmissionsAgent runtime, long id10) {
    Logic.trace(runtime, "subscribe", "Begin onCommand(" + id10 + ")");
    if (id10 <= 0) {
      Logic.warn(runtime, "subscribe", "Will not downlink to nonpositive id10");
    } else {
      final String nodeUri = "/submission/" + Utils.id10To36(id10);
      Logic.info(runtime, "subscribe", "Will downlink to " + nodeUri + "#status");
      runtime.statuses.downlink(id10)
          .nodeUri(nodeUri)
          .laneUri("status")
          .open();
    }
    Logic.trace(runtime, "subscribe", "End onCommand()");
  }

  static void removeSubmission(SubmissionsAgent runtime, long v) {
    runtime.statuses.remove(v);
    runtime.reviewed.remove(v);
    runtime.answered.remove(v);
    runtime.unanswered.remove(v);
    runtime.unreviewed.remove(v);
  }

  static HttpResponse<?> unansweredApiDoRespond(SubmissionsAgent runtime, HttpRequest<Value> request) {
    return HttpResponse.create(HttpStatus.OK)
        .body(body(runtime.unanswered.keySet()), MediaType.textHtml());
  }

  private static String body(Set<Long> ids) {
    final Iterator<Long> itr = ids.iterator();
    String oneLink = oneLink(itr, 1);
    StringBuilder links = new StringBuilder();
    for (int i = 1; oneLink != null; i++, oneLink = oneLink(itr, i)) {
      links.append(oneLink);
    }
    return String.format(UNANSWERED_PAGE_FMT, links.toString());
  }

  private static String oneLink(Iterator<Long> ids, int linkId) {
    if (ids == null || !ids.hasNext()) {
      return null;
    }
    StringBuilder url = new StringBuilder("https://www.reddit.com/by_id/");
    for (int i = 0; i < 20 && ids.hasNext(); i++) {
      url.append("t3_").append(Utils.id10To36(ids.next())).append(',');
    }
    if (url.charAt(url.length() - 1) == ',') {
      url.deleteCharAt(url.length() - 1);
    }
    final String toString = url.toString();
    return String.format("<li><a href=%s>link %d</a>", toString, linkId);
  }

  static void didStart(SubmissionsAgent runtime) {
    Logic.info(runtime, "didStart()", "");
    Logic.debug(runtime, "didStart()", "Scheduling timer tick for " + EXPIRY_PERIOD_MS + " ms");
    if (runtime.expiryTimer != null) {
      Logic.debug(runtime, "willClose()", "Canceling expiryTimer");
      runtime.expiryTimer.cancel();
      runtime.expiryTimer = null;
    }
    runtime.expiryTimer = runtime.setTimer(EXPIRY_PERIOD_MS, () -> {
      Logic.trace(runtime, "[expiryTimer]", "Tick");
      final long now = System.currentTimeMillis();
      Shared.liveSubmissions().expire(runtime)
          .forEach(id36 -> {
            Logic.debug(runtime, "[expiryTimer]", "Notifying /submission/" + id36 + " of expiry");
            runtime.command("/submission/" + id36, "expire", Text.from("expire"));
          });
      final long delta = now + EXPIRY_PERIOD_MS - System.currentTimeMillis();
      Logic.debug(runtime, "[expiryTimer]", "Scheduling timer tick for " + delta + " ms");
      runtime.expiryTimer.reschedule(Math.max(1000L, delta));
    });
  }

  static void willClose(SubmissionsAgent runtime) {
    Logic.info(runtime, "willClose()", "");
    if (runtime.expiryTimer != null) {
      Logic.debug(runtime, "willClose()", "Canceling expiryTimer");
      runtime.expiryTimer.cancel();
      runtime.expiryTimer = null;
    }
  }

}
