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

import java.util.Iterator;
import java.util.Set;
import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.http.HttpLane;
import swim.api.lane.CommandLane;
import swim.api.lane.JoinValueLane;
import swim.api.lane.MapLane;
import swim.http.HttpResponse;
import swim.http.HttpStatus;
import swim.http.MediaType;
import swim.structure.Form;
import swim.structure.Value;

public class SubmissionsAgent extends AbstractAgent {

  @SwimLane("statuses")
  private JoinValueLane<String, Value> statuses = joinValueLane()
      .keyForm(Form.forString())
      .valueForm(Form.forValue())
      .didUpdate(this::statusesDidUpdate);

  protected void statusesDidUpdate(String k, Value n, Value o) {
    final String id = n.get("id").stringValue(null);
    if (id == null) {
      return;
    }
    final Value taxa = n.get("taxa");
    final Value reviewers = n.get("reviewers");
    if (taxa.isDistinct()) {
      if (reviewers.isDistinct()) {
        this.unreviewed.remove(id);
        this.reviewed.put(id, n);
      }
      this.unanswered.remove(id);
      this.answered.put(id, n);
    } else {
      this.reviewed.remove(id);
      this.answered.remove(id);
      this.unreviewed.put(id, n);
      this.unanswered.put(id, n);
    }
  }

  @SwimLane("subscribe")
  private CommandLane<String> subscribe = this.<String>commandLane()
      .onCommand(uri -> {
        this.statuses.downlink(uri)
            .nodeUri(uri).laneUri("status")
            .open();
      });

  @SwimLane("unsubscribe")
  private CommandLane<String> unsubscribe = this.<String>commandLane()
      .onCommand(uri -> {
        this.statuses.remove(uri);
        final String k = uri.substring("/submission/".length());
        this.reviewed.remove(k);
        this.answered.remove(k);
        this.unanswered.remove(k);
        this.unreviewed.remove(k);
      });

  @SwimLane("unanswered")
  MapLane<String, Value> unanswered = mapLane();

  @SwimLane("api/unanswered")
  HttpLane<Value> unansweredApi = this.<Value>httpLane()
      .doRespond(request -> HttpResponse.create(HttpStatus.OK)
          .body(body(this.unanswered.keySet()), MediaType.textHtml()));

  private static String body(Set<String> ids) {
    final Iterator<String> itr = ids.iterator();
    String oneLink = oneLink(itr, 1);
    StringBuilder links = new StringBuilder();
    for (int i = 1; oneLink != null; i++, oneLink = oneLink(itr, i)) {
      links.append(oneLink);
    }
    return String.format("<!doctypehtml><title>munin/unanswered</title><h2>Recent Uncatalogued Submissions</h2><div><h3>Links</h3><ul>%s</ul></div><div><h3>Notes</h3><ul><li>The links utilize Reddit's <i>by_id</i> API to provide uncataloged submissions from the last 36 hours ordered chronologically, 20 submissions per link.<li>The links <i>probably will not</i> load in Reddit apps (first-party or otherwise) but should work in any standard browser.<li>If a <strong>removed or deleted</strong> post shows up in the list, you may leave a comment on the post to accelerate its removal.<li>Expect frequent changes to this work-in-progress page.</ul></div>",
        links.toString());
  }

  private static String oneLink(Iterator<String> ids, int linkId) {
    if (ids == null || !ids.hasNext()) {
      return null;
    }
    StringBuilder url = new StringBuilder("https://www.reddit.com/by_id/");
    for (int i = 0; i < 20 && ids.hasNext(); i++) {
      url.append("t3_").append(ids.next()).append(',');
    }
    if (url.charAt(url.length() - 1) == ',') {
      url.deleteCharAt(url.length() - 1);
    }
    final String toString = url.toString();
    return String.format("<li><a href=%s>link %d</a>", toString, linkId);
  }

  @SwimLane("unreviewed")
  MapLane<String, Value> unreviewed = mapLane();

  @SwimLane("answered")
  MapLane<String, Value> answered = mapLane();

  @SwimLane("reviewed")
  MapLane<String, Value> reviewed = mapLane();

  @Override
  public void didStart() {
    info(nodeUri() + ": didStart");
  }

}
