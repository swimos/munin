package filethesebirds.munin.connect.xenocanto;

import filethesebirds.munin.connect.http.HttpUtils;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Supplier;
import static java.net.http.HttpResponse.BodyHandlers;

public class XenoCantoClient {

  private final HttpClient executor;

  public XenoCantoClient(HttpClient executor) {
    this.executor = executor;
  }

  private static <V> boolean responseIsSuccessful(HttpResponse<V> response) {
    return response.statusCode() == 200;
  }

  private String makeApiCall(Supplier<HttpRequest> requestSupplier)
      throws XenoCantoApiException {
    final HttpResponse<String> resp = HttpUtils.fireRequest(this.executor, requestSupplier.get(),
        BodyHandlers.ofString(), 1);
    if (responseIsSuccessful(resp)) {
      return resp.body();
    } else {
      throw new XenoCantoApiException("Problematic API response with code " + resp.statusCode()
          + ". Headers: " + resp.headers());
    }
  }

}
