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

import java.util.Iterator;
import java.util.Set;
import swim.api.SwimLane;
import swim.api.http.HttpLane;
import swim.api.lane.MapLane;
import swim.http.HttpRequest;
import swim.http.HttpResponse;
import swim.http.HttpStatus;
import swim.http.MediaType;
import swim.munin.MuninEnvironment;
import swim.munin.Utils;
import swim.munin.connect.reddit.RedditClient;
import swim.munin.swim.AbstractSubmissionsAgent;
import swim.munin.swim.LiveSubmissions;
import swim.munin.swim.Logic;
import swim.structure.Value;

/**
 * A singleton Web Agent whose lanes either stream (in the case of the {@code
 * MapLanes} and {@code JoinValueLanes}) or REST-serve (in the case of the
 * {@code HttpLanes}) granular insights into all active r/WhatsThisBird
 * submissions.
 *
 * <p>The {@code SubmissionsAgent} does not interact with vault in any way.
 */
public class SubmissionsAgent extends AbstractSubmissionsAgent {

  @SwimLane("unanswered")
  protected MapLane<Long, Value> unanswered = mapLane();

  @SwimLane("unreviewed")
  protected MapLane<Long, Value> unreviewed = mapLane();

  @SwimLane("answered")
  protected MapLane<Long, Value> answered = mapLane();

  @SwimLane("reviewed")
  protected MapLane<Long, Value> reviewed = mapLane();

  @SwimLane("api/unanswered")
  protected HttpLane<Value> unansweredApi = this.<Value>httpLane()
      .doRespond(this::unansweredApiDoRespond);

  @SwimLane("api/unreviewed")
  protected HttpLane<Value> unreviewedApi = this.<Value>httpLane()
      .doRespond(this::unreviewedApiDoRespond);

  @SwimLane("api/answered")
  protected HttpLane<Value> answeredApi = this.<Value>httpLane()
      .doRespond(this::answeredApiDoRespond);

  @SwimLane("api/reviewed")
  protected HttpLane<Value> reviewedApi = this.<Value>httpLane()
      .doRespond(this::reviewedApiDoRespond);

  @Override
  public MuninEnvironment environment() {
    return Shared.muninEnvironment();
  }

  @Override
  public LiveSubmissions liveSubmissions() {
    return Shared.liveSubmissions();
  }

  @Override
  public RedditClient redditClient() {
    return Shared.redditClient();
  }

  protected void statusesDidUpdate(long k, Value n, Value o) {
    AgentLogic.statusesDidUpdate(this, k, n, o);
  }

  protected void expireSubmissionOnCommand(long v) {
    AgentLogic.removeSubmission(this, "expireSubmission", v);
  }

  protected void shelveSubmissionOnCommand(long v) {
    AgentLogic.removeSubmission(this, "shelveSubmission", v);
  }

  HttpResponse<?> unansweredApiDoRespond(HttpRequest<Value> request) {
    return AgentLogic.unansweredApiDoRespond(this, request);
  }

  HttpResponse<?> unreviewedApiDoRespond(HttpRequest<Value> request) {
    return AgentLogic.unreviewedApiDoRespond(this, request);
  }

  HttpResponse<?> answeredApiDoRespond(HttpRequest<Value> request) {
    return AgentLogic.answeredApiDoRespond(this, request);
  }

  HttpResponse<?> reviewedApiDoRespond(HttpRequest<Value> request) {
    return AgentLogic.reviewedApiDoRespond(this, request);
  }

  private static final class AgentLogic {

    private static final String PAGE_FMT_SUFFIX = "<div><h3>Links</h3><ul>%s</ul></div>"
        + "<div>"
        + "<h3>Notes</h3>"
        + "<ul>"
        + "<li>The links utilize Reddit's <i>by_id</i> API to provide submissions from the last 36 hours ordered chronologically, 20 submissions per link."
        + "<li>The links <i>probably will not</i> load in Reddit apps but should work in any standard browser."
        + "</ul>"
        + "</div>";
    private static final String UNANSWERED_PAGE_FMT = "<!doctypehtml><title>munin/unanswered</title><h2>Recent Unanswered Submissions</h2>"
        + PAGE_FMT_SUFFIX;
    private static final String UNREVIEWED_PAGE_FMT = "<!doctypehtml><title>munin/unreviewed</title><h2>Recent Unreviewed Submissions</h2>"
        + PAGE_FMT_SUFFIX;
    private static final String ANSWERED_PAGE_FMT = "<!doctypehtml><title>munin/answered</title><h2>Recent Answered Submissions</h2>"
        + PAGE_FMT_SUFFIX;
    private static final String REVIEWED_PAGE_FMT = "<!doctypehtml><title>munin/reviewed</title><h2>Recent Reviewed Submissions</h2>"
        + PAGE_FMT_SUFFIX;
    
    private AgentLogic() {
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

    static void removeSubmission(SubmissionsAgent runtime, String caller, long v) {
      Logic.info(runtime, caller, "Will remove submission " + v + " from local lanes");
      runtime.statuses.remove(v);
      runtime.reviewed.remove(v);
      runtime.answered.remove(v);
      runtime.unanswered.remove(v);
      runtime.unreviewed.remove(v);
    }

    static HttpResponse<?> unansweredApiDoRespond(SubmissionsAgent runtime, HttpRequest<Value> request) {
      return HttpResponse.create(HttpStatus.OK)
          .body(body(UNANSWERED_PAGE_FMT, runtime.unanswered.keySet()), MediaType.textHtml());
    }

    static HttpResponse<?> unreviewedApiDoRespond(SubmissionsAgent runtime, HttpRequest<Value> request) {
      return HttpResponse.create(HttpStatus.OK)
          .body(body(UNREVIEWED_PAGE_FMT, runtime.unreviewed.keySet()), MediaType.textHtml());
    }

    static HttpResponse<?> answeredApiDoRespond(SubmissionsAgent runtime, HttpRequest<Value> request) {
      return HttpResponse.create(HttpStatus.OK)
          .body(body(ANSWERED_PAGE_FMT, runtime.answered.keySet()), MediaType.textHtml());
    }

    static HttpResponse<?> reviewedApiDoRespond(SubmissionsAgent runtime, HttpRequest<Value> request) {
      return HttpResponse.create(HttpStatus.OK)
          .body(body(REVIEWED_PAGE_FMT, runtime.reviewed.keySet()), MediaType.textHtml());
    }

    private static String body(String fmt, Set<Long> ids) {
      final Iterator<Long> itr = ids.iterator();
      String oneLink = oneLink(itr, 1);
      StringBuilder links = new StringBuilder();
      for (int i = 1; oneLink != null; i++, oneLink = oneLink(itr, i)) {
        links.append(oneLink);
      }
      return String.format(fmt, links.toString());
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

  }

}
