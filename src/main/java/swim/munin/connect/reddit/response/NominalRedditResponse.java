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

package swim.munin.connect.reddit.response;

import swim.munin.connect.reddit.RedditResponse;
import java.io.InputStream;
import java.net.http.HttpResponse;
import swim.structure.Value;

public abstract class NominalRedditResponse<V> implements RedditResponse<V> {

  private final StructuredRedditResponse delegate;

  public NominalRedditResponse(HttpResponse<InputStream> hr) {
    this.delegate = new StructuredRedditResponse(hr);
  }

  protected abstract V cast(Value value);

  @Override
  public HttpResponse<InputStream> httpResponse() {
    return this.delegate.httpResponse();
  }

  @Override
  public V essence() {
    return cast(this.delegate.essence());
  }

  @Override
  public int requestsRemaining() {
    return this.delegate.requestsRemaining();
  }

  @Override
  public long millisToReset() {
    return this.delegate.millisToReset();
  }

}
