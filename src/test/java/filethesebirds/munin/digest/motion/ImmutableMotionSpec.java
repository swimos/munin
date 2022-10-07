package filethesebirds.munin.digest.motion;

import java.util.Set;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class ImmutableMotionSpec {

  // Expose ordinarily-unavailable constructors to facilitate other tests

  public static Suggestion newImmutableSuggestion(Set<String> pluses) {
    return ImmutableSuggestion.create(pluses);
  }

  public static Suggestion emptySuggestion() {
    return ImmutableSuggestion.empty();
  }

  public static Review newPlusImmutableReview(String reviewer, Set<String> pluses) {
    return ImmutableReview.plus(reviewer, pluses);
  }

  public static Review newOverrideImmutableReview(String reviewer, Set<String> overrides) {
    return ImmutableReview.override(reviewer, overrides);
  }

  public static Review newEmptyImmutableReview(String reviewer) {
    return ImmutableReview.empty(reviewer);
  }

  @Test
  public void testAppend() {
    // Taxonomy input validation intentionally not enforced at this step
    assertEquals(emptySuggestion().additionalTaxa(Set.of("reshaw", "badddd", "barowl13")).plusTaxa().size(), 3);
    // Retroactively appending taxa does nothing for override-type Reviews...
    assertEquals(newOverrideImmutableReview("brohitbrose", Set.of("lockedout"))
        .additionalTaxa(Set.of("reshaw", "badddd", "barowl13")).plusTaxa().size(), 0);
    // ...but works fine for plus-type ones
    assertEquals(newPlusImmutableReview("brohitbrose", Set.of("gooddd"))
        .additionalTaxa(Set.of("reshaw", "gooddd", "barowl13")).plusTaxa().size(), 3);
  }

}
