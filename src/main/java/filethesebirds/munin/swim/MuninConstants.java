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

package filethesebirds.munin.swim;

public final class MuninConstants {

  private MuninConstants() {
  }

  public static long lookbackHours() {
    return 36L; // FIXME: env
  }

  public static long lookbackSeconds() {
    return 60L * 60L * lookbackHours();
  }

  public static long lookbackMillis() {
    return 1000L * lookbackSeconds();
  }

}
