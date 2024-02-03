package swim.munin.swim;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class ReadOnlyPolicySpec {

  @Test
  public void testExtractSubmissionId10() {
    assertEquals(ReadOnlyPolicy.extractSubmissionId10("/submission"), -1L);
    assertEquals(ReadOnlyPolicy.extractSubmissionId10("/submission/18o4jix"), 2701034457L);
    assertEquals(ReadOnlyPolicy.extractSubmissionId10("/submission/18o4jix/foo/23"), -1L);
    assertEquals(ReadOnlyPolicy.extractSubmissionId10("/submissions/18o4jix"), -1L);
  }

}
