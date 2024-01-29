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

package swim.munin.filethesebirds.connect.ebird;

import swim.munin.Utils;
import java.io.InputStream;
import java.util.Properties;

public class EBirdCredentials {

  private final String userAgent;

  public EBirdCredentials(String userAgent) {
    this.userAgent = userAgent;
  }

  public static EBirdCredentials fromProperties(Properties props) {
    return new EBirdCredentials(props.getProperty("userAgent", ""));
  }

  public static EBirdCredentials fromStream(InputStream stream) {
    return Utils.credentialsFromStream(stream, EBirdCredentials::fromProperties);
  }

  public String userAgent() {
    return this.userAgent;
  }

}
