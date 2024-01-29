package swim.munin.filethesebirds.digest.motion;

import java.util.HashSet;
import java.util.Set;
import org.testng.annotations.Test;
import swim.uri.Uri;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CommonUrlExtractSpec {

  private static boolean extract(String url, Set<String> taxa, Set<String> hints) {
    return CommonUrlExtract.extractFromUri(Uri.parse(url), taxa, hints);
  }

  @Test
  public void testExtractionFromCommonUrls() {
    final Set<String> taxa = new HashSet<>(),
        hints = new HashSet<>();
    // AllAboutBirds
    assertTrue(extract("https://www.allaboutbirds.org/guide/Short-billed_Dowitcher/species-compare/", taxa, hints));
    // eBird (regular)
    assertTrue(extract("https://ebird.org/species/rxyfli", taxa, hints));
    // search.macaulaylibrary / media.ebird
    assertTrue(extract("https://media.ebird.org/catalog?taxonCode=brwhaw&sort=rating_rank_desc&mediaType=photo&beginMonth=8&endMonth=9&age=juvenile,immature",
        taxa, hints));
    assertEquals(taxa, Set.of("rxyfli", "brwhaw"));
    assertEquals(hints, Set.of("short-billed%20dowitcher"));
  }

}
