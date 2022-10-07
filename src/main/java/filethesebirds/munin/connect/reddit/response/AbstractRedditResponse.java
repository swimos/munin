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

package filethesebirds.munin.connect.reddit.response;

import filethesebirds.munin.connect.reddit.RedditResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;

public abstract class AbstractRedditResponse<V> implements RedditResponse<V> {

  final HttpResponse<InputStream> hr;
  private V decodedEssence = null;
  private int requestsRemaining = -1;
  private long millisToReset = -1L;

  public AbstractRedditResponse(HttpResponse<InputStream> hr) {
    this.hr = hr;
  }

  @Override
  public HttpResponse<InputStream> httpResponse() {
    return this.hr;
  }

  protected abstract V decodeEssence(HttpResponse<InputStream> hr) throws IOException;

  @Override
  public final V essence() {
    if (this.decodedEssence != null) {
      return this.decodedEssence;
    }
    try {
      return (this.decodedEssence = decodeEssence(this.hr));
    } catch (IOException e) {
      throw new RuntimeException("Failed to extract essence", e);
    }
  }

  @Override
  public int requestsRemaining() {
    return this.requestsRemaining >= 0 ? this.requestsRemaining
        : this.hr.headers().firstValue("x-ratelimit-remaining")
            .map(s -> (this.requestsRemaining = (int) Double.parseDouble(s)))
            .orElse(-1);
  }

  @Override
  public long millisToReset() {
    return this.millisToReset >= 0L ? this.millisToReset
        : this.hr.headers().firstValue("x-ratelimit-reset")
            .map(s -> (this.millisToReset = (long) Double.parseDouble(s)))
            .orElse(-1L);
  }

}
