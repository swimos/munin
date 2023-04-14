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

import filethesebirds.munin.digest.Forms;
import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.http.HttpLane;
import swim.api.lane.CommandLane;
import swim.api.lane.JoinValueLane;
import swim.api.lane.MapLane;
import swim.http.HttpResponse;
import swim.http.HttpStatus;
import swim.http.MediaType;
import swim.json.Json;
import swim.structure.Form;
import swim.structure.Value;

public class SubmissionsAgent extends AbstractAgent {

  @SwimLane("statuses")
  private JoinValueLane<String, Value> statuses = joinValueLane()
      .keyForm(Form.forString())
      .valueForm(Form.forValue())
      .didUpdate((k, n, o) -> {
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
      });

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
          .body(Json.toString(Forms.forSetString().mold(this.unanswered.keySet()).toValue()),
              MediaType.applicationJson()));

  @SwimLane("unreviewed")
  MapLane<String, Value> unreviewed = mapLane();

  @SwimLane("answered")
  MapLane<String, Value> answered = mapLane();

  @SwimLane("reviewed")
  MapLane<String, Value> reviewed = mapLane();

  @Override
  public void didStart() {
    System.out.println(nodeUri() + ": didStart");
  }

}
