package swim.munin.filethesebirds.digest.answer;

import java.util.Set;
import org.testng.annotations.Test;
import swim.munin.filethesebirds.digest.Answer;
import static org.testng.Assert.assertEquals;

public class PublicationSpec {

  @Test
  public void testAnswerFromPublication() {
    final String body = "Added taxa: [Red-tailed Hawk](https://ebird.org/species/rethaw), [Cooper's Hawk](https://ebird.org/species/coohaw)\n\nReviewed by: brohitbrose, tinylongwing, another-thing\n\n^(I'm a bot cataloging r/WhatsThisBird.) [^(Learn how to use me)](https://gist.github.com/brohitbrose/be99a16ddc7a6a1bd9c1eef28d622564)^(.)";
    final Answer result = Publication.answerFromPublication(body);
    assertEquals(result.taxa(), Set.of("rethaw", "coohaw"));
    assertEquals(result.reviewers(), Set.of("tinylongwing", "brohitbrose", "another-thing"));
  }

  @Test
  public void testPublicationFromAnswer() {
    final MutableAnswer answer = (MutableAnswer) Answers.mutable();
    answer.addAllTaxa(Set.of("rethaw", "coohaw"));
    answer.addAllReviewers(Set.of("tinylongwing", "brohitbrose", "another-thing"));
    final MutableAnswer derived = (MutableAnswer) Publication.answerFromPublication(Publication.publicationFromAnswer(answer));
    assertEquals(derived.taxa().size(), 2);
    assertEquals(derived.taxa(), answer.taxa());
    assertEquals(derived.reviewers().size(), 3);
    assertEquals(derived.reviewers(), answer.reviewers());
  }

}
