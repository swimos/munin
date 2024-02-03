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

package swim.munin.swim;

import swim.api.auth.Identity;
import swim.api.policy.AbstractPolicy;
import swim.api.policy.PolicyDirective;
import swim.munin.Utils;
import swim.munin.filethesebirds.swim.Shared;
import swim.uri.Uri;
import swim.warp.CommandMessage;
import swim.warp.Envelope;

public class ReadOnlyPolicy extends AbstractPolicy {

  public ReadOnlyPolicy() {
  }

  @Override
  protected <T> PolicyDirective<T> authorize(Envelope envelope, Identity identity) {
    // no commanding allowed
    if (envelope instanceof CommandMessage) {
      return forbid();
    }
    final Uri nodeUri = envelope.nodeUri();
    if (nodeUri == null) {
      return super.authorize(envelope, identity);
    }
    // no creating new dynamic agents
    final String nodeStr = nodeUri.toString();
    if (nodeStr != null && (nodeStr.startsWith("/submission/") || nodeStr.startsWith("submission/"))) {
      final long id10 = extractSubmissionId10(envelope.nodeUri().toString());
      if (id10 < 0L || Shared.liveSubmissions().getActive(id10) == null) {
        return forbid();
      }
    }
    return super.authorize(envelope, identity);
  }

  static long extractSubmissionId10(String nodeUri) {
    if (nodeUri == null || nodeUri.isEmpty()) {
      return -1L;
    }
    for (int i = 0; i < nodeUri.length(); i++) {
      final char c = nodeUri.charAt(i);
      if (!(('/' == c) || ('a' <= c && c <= 'z') || ('0' <= c && c <= '9'))) {
        return -1L;
      }
    }
    final String indicator = "submission/";
    final int i = nodeUri.indexOf(indicator) + indicator.length();
    if (i < indicator.length()) {
      return -1L;
    } else {
      try {
        return Utils.id36To10(nodeUri.substring(i));
      } catch (Exception e) {
        return -1L;
      }
    }
  }

}
