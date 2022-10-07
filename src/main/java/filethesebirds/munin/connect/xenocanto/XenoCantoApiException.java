package filethesebirds.munin.connect.xenocanto;

public class XenoCantoApiException extends Exception {

  public XenoCantoApiException(String msg) {
    super(msg);
  }

  public XenoCantoApiException(String msg, Throwable e) {
    super(msg, e);
  }

}
