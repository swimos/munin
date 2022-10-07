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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class Users {

  private static final Set<String> REVIEWERS = new HashSet<>(63);
  static {
    loadSetFromResource(REVIEWERS, "reviewers");
  }

  private static final String PUBLISHER = "filethesebirdsbot";

  private static final Set<String> NONPARTICIPANTS = new HashSet<>();
  static {
    loadSetFromResource(NONPARTICIPANTS, "nonparticipants");
  }

  private static void loadSetFromResource(Set<String> set, String name) {
    try (BufferedReader br = new BufferedReader(
        new InputStreamReader(Objects.requireNonNull(
            Main.class.getResourceAsStream("/" + name + ".txt"))))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (isValidUsername(line)) {
          set.add(line);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to load " + name, e);
    }
  }

  public static boolean userIsReviewer(String user) {
    return user != null && REVIEWERS.contains(user);
  }

  public static boolean userIsPublisher(String user) {
    return PUBLISHER.equals(user);
  }

  public static boolean userIsNonparticipant(String user) {
    return user != null && NONPARTICIPANTS.contains(user);
  }

  private static boolean isValidUsernameChar(char c) {
    return ('a' <= c && c <= 'z') || ('0' <= c && c <= '9')
        || (c == '-') || (c == '_');
  }

  public static boolean isValidUsername(String s) {
    if (s == null || s.length() < 2) {
      return false;
    }
    for (int i = 0, n = s.length(); i < n; i++) {
      if (!isValidUsernameChar(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

}
