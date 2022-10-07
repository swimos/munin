package filethesebirds.munin.connect.xenocanto;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

class XenoCantoApi {

  private static final String DOMAIN = "https://xeno-canto.org";
  private static final String ENDPOINT = "/api/2/recordings?query=nr:%s";
  private static final String FMT = DOMAIN + ENDPOINT;

  private XenoCantoApi() {
  }

  private static HttpRequest get(URI uri) {
    return HttpRequest.newBuilder(uri)
        .GET()
        .timeout(Duration.ofMillis(5000L))
        .build();
  }

  private static URI endpoint(String id) {
    return URI.create(String.format(FMT, id));
  }

  public static HttpRequest byId(String id) {
    return get(endpoint(id));
  }

}
