package swim.munin.filethesebirds.digest.answer;

import swim.munin.filethesebirds.digest.Answer;
import swim.munin.filethesebirds.digest.motion.ImmutableMotionSpec;
import swim.munin.filethesebirds.digest.motion.Review;
import swim.munin.filethesebirds.digest.motion.Suggestion;
import java.util.Set;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class MutableAnswerSpec {

  @Test
  public void testAnswerLifecycle() {
    final Answer answer = Answers.mutable();
    final Suggestion sug1 = ImmutableMotionSpec.emptySuggestion();
    final Suggestion sug2 = ImmutableMotionSpec.newPlusImmutableSuggestion(Set.of("foo", "bar"));
    final Suggestion sug3 = ImmutableMotionSpec.newPlusImmutableSuggestion(Set.of("foo", "baz"));
    answer.apply(sug1).apply(sug2).apply(sug3);
    // No-frills set union
    assertEquals(answer.taxa(), Set.of("foo", "bar", "baz"));
    final Suggestion sug4 = ImmutableMotionSpec.newOverrideImmutableSuggestion(Set.of("foo", "bar"));
    answer.apply(sug4);
    // Override has removal ability
    assertEquals(answer.taxa(), Set.of("foo", "bar"));
    final Review rev1 = ImmutableMotionSpec.newEmptyImmutableReview("uncheckedReviewer");
    final Suggestion sug5 = ImmutableMotionSpec.newPlusImmutableSuggestion(Set.of("troll", "ololol"));
    answer.apply(rev1).apply(sug5);
    // Review locks out further suggestions
    assertEquals(answer.taxa(), Set.of("foo", "bar"));
    // A new reviewer may still contribute, but suggesters may not
    final Review rev3 = ImmutableMotionSpec.newOverrideImmutableReview("difReviewer", Set.of("foo"));
    answer.apply(rev3).apply(sug4);
    assertEquals(answer.taxa(), Set.of("foo"));
  }

}
