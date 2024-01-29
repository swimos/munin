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

import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.http.HttpLane;
import swim.api.lane.CommandLane;
import swim.api.lane.JoinValueLane;
import swim.api.lane.MapLane;
import swim.concurrent.TimerRef;
import swim.http.HttpRequest;
import swim.http.HttpResponse;
import swim.munin.swim.LiveSubmissions;
import swim.structure.Form;
import swim.structure.Value;

/**
 * A singleton Web Agent whose lanes stream granular insights into all active
 * r/WhatsThisBird submissions.
 *
 * <p>The {@code SubmissionsAgent} is liable for the following {@link
 * LiveSubmissions} action:
 * <ul>
 * <li>Expiring every submission whose time since creation exceeds a threshold
 * </ul>
 * but does not interact with vault in any way.
 */
public class SubmissionsAgent extends AbstractAgent {

  protected TimerRef expiryTimer;

  @SwimLane("statuses")
  protected JoinValueLane<Long, Value> statuses = joinValueLane()
      .keyForm(Form.forLong())
      .valueForm(Form.forValue())
      .didUpdate(this::statusesDidUpdate);

  @SwimLane("subscribe")
  protected CommandLane<Long> subscribe = this.<Long>commandLane()
      .onCommand(this::subscribeOnCommand);

  @SwimLane("expireSubmission")
  protected CommandLane<Long> expireSubmission = this.<Long>commandLane()
      .onCommand(this::expireSubmissionOnCommand);

  @SwimLane("shelveSubmission")
  protected CommandLane<Long> shelveSubmission = this.<Long>commandLane()
      .onCommand(this::shelveSubmissionOnCommand);

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

  protected void statusesDidUpdate(long k, Value n, Value o) {
    SubmissionsAgentLogic.statusesDidUpdate(this, k, n, o);
  }

  protected void subscribeOnCommand(long v) {
    SubmissionsAgentLogic.subscribeOnCommand(this, v);
  }

  protected void expireSubmissionOnCommand(long v) {
    SubmissionsAgentLogic.removeSubmission(this, "expireSubmission", v);
  }

  protected void shelveSubmissionOnCommand(long v) {
    SubmissionsAgentLogic.removeSubmission(this, "shelveSubmission", v);
  }

  HttpResponse<?> unansweredApiDoRespond(HttpRequest<Value> request) {
    return SubmissionsAgentLogic.unansweredApiDoRespond(this, request);
  }

  HttpResponse<?> unreviewedApiDoRespond(HttpRequest<Value> request) {
    return SubmissionsAgentLogic.unreviewedApiDoRespond(this, request);
  }

  HttpResponse<?> answeredApiDoRespond(HttpRequest<Value> request) {
    return SubmissionsAgentLogic.answeredApiDoRespond(this, request);
  }

  HttpResponse<?> reviewedApiDoRespond(HttpRequest<Value> request) {
    return SubmissionsAgentLogic.reviewedApiDoRespond(this, request);
  }

  @Override
  public void didStart() {
    SubmissionsAgentLogic.didStart(this);
  }

  @Override
  public void willClose() {
    SubmissionsAgentLogic.willClose(this);
  }

}
