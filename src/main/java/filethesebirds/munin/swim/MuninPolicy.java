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

package filethesebirds.munin.swim;

import swim.api.auth.Identity;
import swim.api.downlink.MapDownlink;
import swim.api.plane.PlaneContext;
import swim.api.policy.AbstractPolicy;
import swim.api.policy.PolicyDirective;
import swim.structure.Form;
import swim.structure.Value;
import swim.warp.CommandMessage;
import swim.warp.Envelope;

public class MuninPolicy extends AbstractPolicy {

  private MapDownlink<String, Value> downlink;

  public MuninPolicy(PlaneContext plane) {
    this.downlink = plane.downlinkMap()
        .keyForm(Form.forString())
        .nodeUri("/submissions").laneUri("statuses")
        .keepSynced(true)
        .open();
  }

  @Override
  protected <T> PolicyDirective<T> authorize(Envelope envelope, Identity identity) {
    // no commanding allowed
    if (envelope instanceof CommandMessage) {
      return forbid();
    }
    // no creating new dynamic agents
    final String envelopeNodeUri = envelope.nodeUri().toString();
    if (envelopeNodeUri.contains("submission/")) {
      if (!this.downlink.containsKey(envelopeNodeUri)) {
        return forbid();
      }
    }
    return super.authorize(envelope, identity);
  }

}
