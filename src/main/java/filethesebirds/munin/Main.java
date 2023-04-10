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

package filethesebirds.munin;

import filethesebirds.munin.swim.Coalesce;
import filethesebirds.munin.swim.MuninPolicy;
import filethesebirds.munin.swim.Shared;
import swim.api.plane.PlaneContext;
import swim.kernel.Kernel;
import swim.server.ServerLoader;

public class Main {

  public static void main(String[] args) throws Exception {
    // Initialize external clients
    Shared.loadVaultClient();
    Shared.loadEBirdClient();
    Shared.loadRedditClient();

    // Initialize Swim kernel
    final Kernel kernel = ServerLoader.loadServer();
    final PlaneContext plane = (PlaneContext) kernel.getSpace("munin");
    kernel.start();
    plane.setPolicy(new MuninPolicy(plane));
    System.out.println("Running munin...");
    kernel.run();

    // Trigger coalescence
    Coalesce.coalesce(plane);
  }

}
