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

package filethesebirds.munin.digest;

import filethesebirds.munin.Main;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Taxonomy {

  private static final Map<String, String> TAXONOMY = new HashMap<>(32000);
  static {
    try (BufferedReader br = new BufferedReader(
        new InputStreamReader(Objects.requireNonNull(
            Main.class.getResourceAsStream("/ebird-taxa.csv"))))) {
      String line;
      while ((line = br.readLine()) != null) {
        final String[] split = line.split(",");
        if (split.length == 2) {
          final String code = split[0];
          if (codeIsValid(code)) {
            TAXONOMY.put(code, split[1]);
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to load taxonomy", e);
    }
  }

  private static boolean codeIsValid(String code) {
    if (2 > code.length() || code.length() > 8) { // (ou, banowl13)
      return false;
    }
    for (int i = 0; i < code.length(); i++) {
      final char c = code.charAt(i);
      if (!(('a' <= c && c <= 'z') || ('0' <= c && c <= '9'))) {
        return false;
      }
    }
    return true;
  }

  public static boolean containsCode(String code) {
    return code != null && TAXONOMY.containsKey(code);
  }

  public static String commonName(String code) {
    if (containsCode(code)) {
      return TAXONOMY.get(code);
    }
    return null;
  }

  private Taxonomy() {
  }

}
