package filethesebirds.munin.swim;

import swim.api.auth.Identity;
import swim.api.policy.AbstractPolicy;
import swim.api.policy.PolicyDirective;
import swim.warp.CommandMessage;
import swim.warp.Envelope;

public class MuninPolicy extends AbstractPolicy {

  @Override
  protected <T> PolicyDirective<T> authorize(Envelope envelope, Identity identity) {
    // no commanding allowed
    if (envelope instanceof CommandMessage) {
      return forbid();
    }
    // no creating new dynamic agents
    if (envelope.nodeUri().toString().contains("submission/")) {
      return forbid();
    }
    return super.authorize(envelope, identity);
  }

}
