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

package filethesebirds.munin.connect.ebird;

import java.io.IOException;
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

  public static EBirdCredentials fromResource(Class<?> resourceClass, String resourcePath)
      throws RuntimeException {
    final Properties props = new Properties();
    try (InputStream is = resourceClass.getResourceAsStream(resourcePath)) {
      props.load(is);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load " + resourcePath, e);
    }
    return fromProperties(props);
  }

  public String userAgent() {
    return this.userAgent;
  }

}
