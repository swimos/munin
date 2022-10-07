package filethesebirds.munin.digest.tagging;

import filethesebirds.munin.digest.DigestTestUtils;
import filethesebirds.munin.digest.Tagging;
import java.util.EnumSet;
import java.util.Set;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class TaggingParseSpec {

  @Test
  public void testParse() {
    final String body = "!addTaxa barowl13\n\n!overrideTags , art,audio, video fake nonavian faker ~ immature";
    final Set<Tagging.Tag> tags = TaggingParse.parseComment(DigestTestUtils.bareComment("brohitbrose", body));
    assertEquals(tags, EnumSet.of(Tagging.Tag.NONAVIAN, Tagging.Tag.AUDIO, Tagging.Tag.VIDEO));
  }

}