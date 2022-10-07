package filethesebirds.munin.digest;

import swim.structure.Record;

public final class DigestTestUtils {

  private DigestTestUtils() {
  }

  public static Comment timestampedComment(String author, String body, String createdUtc) {
    return Comment.form().cast(Record.create(4).attr("comment")
        .slot("author", author).slot("body", body).slot("createdUtc", createdUtc));
  }

  public static Comment bareComment(String author, String body) {
    return Comment.form().cast(Record.create(3).attr("comment")
        .slot("author", author).slot("body", body));
  }

}
