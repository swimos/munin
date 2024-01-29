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

package swim.munin.filethesebirds.swim;

import swim.kernel.KernelProxy;
import swim.structure.Value;

// Example usage in the top of server.recon:
// @kernel(class: 'MuninLogKernel', level: 'info')
public class MuninLogKernel extends KernelProxy {

  private static final double KERNEL_PRIORITY = 100.0;

  private static final int TRACE_LEVEL = 300;
  private static final int DEBUG_LEVEL = 400;
  private static final int INFO_LEVEL = 800;
  private static final int WARN_LEVEL = 900;
  private static final int ERROR_LEVEL = 1000;

  private final int logLevel;

  public MuninLogKernel(int logLevel) {
    this.logLevel = logLevel;
  }

  @Override
  public double kernelPriority() {
    return KERNEL_PRIORITY;
  }

  @Override
  public void trace(Object message) {
    if (this.logLevel <= TRACE_LEVEL) {
      super.trace(message);
    }
  }

  @Override
  public void debug(Object message) {
    if (this.logLevel <= DEBUG_LEVEL) {
      super.debug(message);
    }
  }

  @Override
  public void info(Object message) {
    if (this.logLevel <= INFO_LEVEL) {
      super.info(message);
    }
  }

  @Override
  public void warn(Object message) {
    if (this.logLevel <= WARN_LEVEL) {
      super.warn(message);
    }
  }

  @Override
  public void error(Object message) {
    if (this.logLevel <= ERROR_LEVEL) {
      super.error(message);
    }
  }

  public static MuninLogKernel fromValue(Value moduleConfig) {
    final Value args = moduleConfig.getAttr("kernel");
    final String kernelClassName = args.get("class").stringValue(null);
    if (kernelClassName == null || MuninLogKernel.class.getName().equals(kernelClassName)) {
      final String level = args.get("level").stringValue("info").trim().toLowerCase();
      System.out.println("[INFO] MuninLogKernel: Using log level " + level);
      final int logLevel;
      switch (level) {
        case "debug":
          logLevel = DEBUG_LEVEL;
          break;
        case "info":
          logLevel = INFO_LEVEL;
          break;
        case "warn":
          logLevel = WARN_LEVEL;
          break;
        case "error":
          logLevel = ERROR_LEVEL;
          break;
        case "fail":
          logLevel = Integer.MAX_VALUE;
          break;
        default:
          logLevel = TRACE_LEVEL;
          break;
      }
      return new MuninLogKernel(logLevel);
    }
    return null;
  }

}
