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

package filethesebirds.munin.connect.reddit;

import filethesebirds.munin.util.ConfigUtils;
import java.io.InputStream;
import java.util.Properties;

public class RedditCredentials {

  private final String clientId;
  private final String clientSecret;
  private final String user;
  private final String pass;
  private final String userAgent;

  public RedditCredentials(String clientId, String clientSecret,
                           String user, String pass, String userAgent) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.user = user;
    this.pass = pass;
    this.userAgent = userAgent;
  }

  public static RedditCredentials fromProperties(Properties props) {
    return new RedditCredentials(props.getProperty("clientId", ""),
        props.getProperty("clientSecret", ""),
        props.getProperty("redditUser", ""),
        props.getProperty("redditPass", ""),
        props.getProperty("userAgent", ""));
  }

  public static RedditCredentials fromStream(InputStream stream) {
    return ConfigUtils.credentialsFromStream(stream, RedditCredentials::fromProperties);
  }

  public String clientId() {
    return this.clientId;
  }

  public String clientSecret() {
    return this.clientSecret;
  }

  public String user() {
    return this.user;
  }

  public String pass() {
    return this.pass;
  }

  public String userAgent() {
    return this.userAgent;
  }

}
