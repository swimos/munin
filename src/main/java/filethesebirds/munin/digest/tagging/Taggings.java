package filethesebirds.munin.digest.tagging;

import filethesebirds.munin.digest.Tagging;

public class Taggings {

  public static Tagging mutable() {
    return new MutableTagging();
  }

}
