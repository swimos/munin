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

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import swim.adapter.http.HttpIngest;
import swim.http.MediaType;
import swim.structure.Value;

public class StructuredRedditResponse extends AbstractRedditResponse<Value> {

  public StructuredRedditResponse(HttpResponse<InputStream> hr) {
    super(hr);
  }

  @Override
  protected Value decodeEssence(HttpResponse<InputStream> hr) throws IOException {
    // Ensure successful status code
    if (hr.statusCode() / 100 != 2) {
      return Value.absent();
    }
    // Extract content encoding
    final String encoding = hr.headers().firstValue("content-encoding")
        .orElse(null);
    // Extract content type
    final MediaType mediaType = hr.headers().firstValue("content-type")
        .map(MediaType::parse)
        .orElse(MediaType.applicationJson());
    return HttpIngest.responseBodyStructure(HttpIngest.responseBodyStream(hr, encoding),
        mediaType);
  }

}
