package filethesebirds.munin.digest;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A possibly in-progress view of reviewer-identified tags for an
 * r/WhatsThisBird submission.
 *
 * <p>{@code Taggings} begin in an empty state that lacks any tags or reviewers.
 * Invocations of {@link #overrideTags(String, Set)}} update {@code Taggings}
 * toward correctness and thoroughness.
 *
 * @see Answer  the equivalent for eBird taxa in place of tags
 */
public interface Tagging {

  Set<String> reviewers();

  Set<Tag> tags();

  Tagging overrideTags(String reviewer, Set<Tag> overrideTags);

  default boolean isEmpty() {
    return false;
//    return (reviewers() == null || reviewers().isEmpty())
//        &&
  }

  enum Tag {

    ART("art"),
    AUDIO("audio"),
    DESCRIPTION("description"),
    IMAGE("image"),
    VIDEO("video"),

    IMMATURE("immature"),

    EGG("egg"),
    FEATHER("feather"),
    NEST("nest"),
    NONAVIAN("nonavian"),
    REMAINS("remains"),
    WASTE("waste"),

    ABERRANT("aberrant"),
    CHALLENGE("challenge"),
    UNSOLVED("unsolved");

    private static final Map<String, Tag> LOOKUP = Stream.of(Tag.values())
        .collect(Collectors.toUnmodifiableMap(Tag::text, Function.identity()));

    private final String text;

    Tag(String text) {
      this.text = text;
    }

    public String text() {
      return this.text;
    }

    public static Tag fromText(String text) {
      final Tag result = LOOKUP.get(text.toLowerCase(Locale.ROOT));
      if (result == null) {
        throw new IllegalArgumentException("No Tag corresponding to text " + text);
      }
      return result;
    }

  }

}
