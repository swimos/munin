package filethesebirds.munin.connect.http;

import swim.http.HttpStatus;

/**
 * Signals a probable application-layer issue stemming from an unhealthy
 * status code received within an HTTP response.
 */
public class StatusCodeException extends Exception {

  private final HttpStatus status;
  private final String serializedHeaders;

  public StatusCodeException(int code, String serializedHeaders) {
    this.status = HttpStatus.from(code);
    this.serializedHeaders = serializedHeaders;
  }

  public HttpStatus status() {
    return this.status;
  }

  public String headers() {
    return this.serializedHeaders;
  }

}
