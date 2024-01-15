package filethesebirds.munin.swim;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class MuninPolicySpec {

  @Test
  public void testExtractSubmissionId10() {
    assertEquals(MuninPolicy.extractSubmissionId10("/submission"), -1L);
    assertEquals(MuninPolicy.extractSubmissionId10("/submission/18o4jix"), 2701034457L);
    assertEquals(MuninPolicy.extractSubmissionId10("/submission/18o4jix/foo/23"), -1L);
    assertEquals(MuninPolicy.extractSubmissionId10("/submissions/18o4jix"), -1L);
  }

}
