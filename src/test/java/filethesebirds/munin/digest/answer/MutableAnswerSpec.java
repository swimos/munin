package filethesebirds.munin.digest.answer;

import filethesebirds.munin.digest.Answer;
import filethesebirds.munin.digest.motion.ImmutableMotionSpec;
import filethesebirds.munin.digest.motion.Review;
import filethesebirds.munin.digest.motion.Suggestion;
import java.util.Set;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class MutableAnswerSpec {

  @Test
  public void testAnswerLifecycle() {
    final Answer answer = Answers.mutable();
    final Suggestion sug1 = ImmutableMotionSpec.emptySuggestion();
    final Suggestion sug2 = ImmutableMotionSpec.newImmutableSuggestion(Set.of("foo", "bar"));
    final Suggestion sug3 = ImmutableMotionSpec.newImmutableSuggestion(Set.of("foo", "baz"));
    answer.apply(sug1).apply(sug2).apply(sug3);
    // No-frills set union
    assertEquals(answer.taxa(), Set.of("foo", "bar", "baz"));
    final Review rev1 = ImmutableMotionSpec.newEmptyImmutableReview("uncheckedReviewer");
    final Suggestion sug4 = ImmutableMotionSpec.newImmutableSuggestion(Set.of("troll", "ololol"));
    answer.apply(rev1).apply(sug4);
    // Review locks out further suggestions
    assertEquals(answer.taxa(), Set.of("foo", "bar", "baz"));
    // A new reviewer may still contribute, but suggesters may not
    final Review rev3 = ImmutableMotionSpec.newOverrideImmutableReview("difReviewer", Set.of("foo"));
    answer.apply(rev3).apply(sug4);
    assertEquals(answer.taxa(), Set.of("foo"));
  }

}
