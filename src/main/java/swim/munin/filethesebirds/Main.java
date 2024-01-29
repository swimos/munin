package swim.munin.filethesebirds;

import swim.api.plane.PlaneContext;
import swim.api.ref.WarpRef;
import swim.kernel.Kernel;
import swim.munin.filethesebirds.swim.Coalescence;
import swim.munin.filethesebirds.swim.MuninPolicy;
import swim.munin.filethesebirds.swim.Shared;
import swim.server.ServerLoader;

public final class Main {

  public static void main(String[] args) {
    startExternalClients();
    final WarpRef swim = startSwimServer();
    coalesce(swim);
    // Park main thread while Swim server runs asynchronously
  }

  private static void startExternalClients() {
    Shared.loadVaultClient();
    Shared.loadEBirdClient();
    Shared.loadRedditClient();
  }

  private static PlaneContext startSwimServer() {
    final Kernel kernel = ServerLoader.loadServer();
    final PlaneContext plane = (PlaneContext) kernel.getSpace("munin");
    kernel.start();
    plane.setPolicy(new MuninPolicy());
    System.out.println("[INFO] Running munin...");
    kernel.run();
    return plane;
  }

  private static void coalesce(WarpRef swim) {
    final Coalescence coalesce = Coalescence.coalesce(swim);
    Shared.loadLiveSubmissions(coalesce);
    coalesce.startSubmissionAgents();
    coalesce.startFetchTasks();
  }

}
