

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
