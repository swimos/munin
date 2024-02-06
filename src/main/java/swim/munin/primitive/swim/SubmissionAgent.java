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

package swim.munin.primitive.swim;

import swim.munin.MuninEnvironment;
import swim.munin.Utils;
import swim.munin.connect.reddit.Comment;
import swim.munin.connect.reddit.RedditClient;
import swim.munin.swim.AbstractSubmissionAgent;
import swim.munin.swim.LiveSubmissions;
import swim.structure.Value;

public class SubmissionAgent extends AbstractSubmissionAgent {

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

  @Override
  protected boolean onNewComment(String caller, Comment c) {
    final boolean result = super.onNewComment(caller, c);
    if (result && commentIsLatest(this.status.get(), c)) {
      this.status.set(Comment.form().mold(c).toValue());
    }
    return result;
  }

  private static boolean commentIsLatest(Value prevStatus, Comment c) {
    if (!prevStatus.isDistinct()) {
      return true;
    }
    return Utils.id36To10(c.id()) > Utils.id36To10(prevStatus.get("id").stringValue("0"));
  }

  @Override
  protected void clearLanes() {
    throw new UnsupportedOperationException("Implementation skips close logic for simplicity");
  }

}
