package filethesebirds.munin.digest.tagging;

import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.Tagging;
import filethesebirds.munin.digest.Users;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class TaggingParse {

  private TaggingParse() {
  }

  private static int commandLength(String body) {
    for (int i = 0; i < body.length(); i++) {
      final char c = body.charAt(i);
      if (!(Character.isLetter(c) || Character.isWhitespace(c) || c == ',')) {
        return i;
      }
    }
    return body.length();
  }

  private static Set<Tagging.Tag> parseBody(String body) {
    final int idx = body.indexOf("!overrideTags ");
    if (idx < 0) {
      return Collections.emptySet();
    }
    final String beheadedBody = body.substring(idx);
    final int firstNewlineIdx = beheadedBody.indexOf('\n');
    final String command = beheadedBody.substring(idx,
        firstNewlineIdx < 0 ? beheadedBody.length() : firstNewlineIdx);
    final int commandLen = commandLength(command);
    final String[] split = command.substring(0, commandLen).split("(\\s|,)+");
    final Set<Tagging.Tag> result = EnumSet.noneOf(Tagging.Tag.class);
    for (int i = 1; i < split.length; i++) {
      try {
        result.add(Tagging.Tag.fromText(split[i]));
      } catch (Exception e) {
        // swallow
      }
    }
    return result;
  }

  public static Set<Tagging.Tag> parseComment(Comment comment) {
    if (Users.userIsNonparticipant(comment.author())) {
      return Collections.emptySet();
    }
    if (Users.userIsReviewer(comment.author())
        && !comment.body().contains("!nonreview")) {
      return parseBody(comment.body());
    }
    return Collections.emptySet();
  }

}
