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

package swim.munin;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class Utils {

  private Utils() {
  }

  // ===========================================================================
  // File handling
  // ===========================================================================

  public static InputStream openConfigFile(String diskPath, String resourcePath) {
    if (diskPath == null || diskPath.isEmpty()) {
      System.out.println("[INFO] Will load resource at " + resourcePath);
      return Utils.class.getResourceAsStream(resourcePath);
    }
    try {
      System.out.println("[INFO] Will load file at " + diskPath);
      return new FileInputStream(diskPath);
    } catch (IOException e) {
      System.out.println("[INFO] Will load resource at " + resourcePath);
      return Utils.class.getResourceAsStream(resourcePath);
    }
  }

  public static <T> T credentialsFromStream(InputStream stream, Function<Properties, T> generator) {
    final Properties props = new Properties();
    try {
      props.load(stream);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load from stream", e);
    }
    return generator.apply(props);
  }

  // ===========================================================================
  // Reddit utilities
  // ===========================================================================

  public static String id10To36(long id10) {
    return Long.toString(id10, 36);
  }

  public static long id36To10(String id36) {
    return Long.parseLong(id36, 36);
  }

  public static String sanitizeSubreddit(String s) {
    if (isMulti(s)) {
      throw new UnsupportedOperationException("Multi-reddit logic not yet implemented");
    } else if (validateSubreddit(s)) {
      return s.toLowerCase(Locale.ROOT);
    } else {
      throw new IllegalArgumentException("Bad subreddit name (too long, or invalid characters) " + s);
    }
  }

  private static boolean isMulti(String s) {
    return s != null && s.contains("+");
  }

  private static final Pattern SUBREDDIT_REGEX_PATTERN = Pattern.compile("\\A[A-Za-z0-9][A-Za-z0-9_]{1,20}\\z");

  private static boolean validateSubreddit(String s) {
    if (s == null || s.length() < 2) {
      throw new IllegalArgumentException("Bad subreddit name (too short) " + s);
    }
    return SUBREDDIT_REGEX_PATTERN.matcher(s).matches();
  }

}
