// Copyright 2015-2023 Swim.inc
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package filethesebirds.munin.digest.answer;

import filethesebirds.munin.digest.Answer;
import filethesebirds.munin.digest.Taxonomy;
import filethesebirds.munin.digest.Users;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Link;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import swim.uri.Uri;

public final class Publication {

  private Publication() {
  }

  private static String publishableTaxa(Set<String> taxa) {
    if (taxa == null || taxa.isEmpty()) {
      return null;
    }
    final List<String> entries = new ArrayList<>(taxa.size());
    for (String taxon : taxa) {
      if (Taxonomy.containsCode(taxon)) {
        if ("nonavian".equals(taxon)) {
          entries.add(Taxonomy.commonName(taxon));
        } else {
          entries.add(String.format("[%s](https://ebird.org/species/%s)",
              Taxonomy.commonName(taxon), taxon));
        }
      }
    }
    return String.join(", ", entries);
  }

  private static String publishableReviewers(Set<String> reviewers) {
    if (reviewers == null || reviewers.isEmpty()) {
      return null;
    }
    final List<String> entries = new ArrayList<>(reviewers.size());
    for (String reviewer : reviewers) {
      if (Users.userIsReviewer(reviewer)) {
        entries.add(reviewer);
      }
    }
    return String.join(", ", entries);
  }

  public static String publicationFromAnswer(Answer ans) {
    if (ans == null) {
      return null;
    }
    final String taxa = publishableTaxa(ans.taxa());
    if (taxa == null) {
      return null;
    }
    final String reviewers = publishableReviewers(ans.reviewers());
    return "Taxa recorded: " + taxa
        + ((reviewers == null) ? "" : ("\n\nReviewed by: " + reviewers))
        + "\n\n^(I catalog submissions to this subreddit.) "
        + "[^(Recent uncatalogued submissions)](https://munin.swim.services/submissions?lane=api/unanswered)^( | )"
        + "[^(Learn to use me)](https://gist.github.com/brohitbrose/be99a16ddc7a6a1bd9c1eef28d622564)";
  }

  public static Answer answerFromPublication(String pub) {
    final PublicationVisitor visitor = new PublicationVisitor();
    PARSER.parse(pub).accept(visitor);
    final MutableAnswer answer = (MutableAnswer) Answers.mutable();
    answer.addAllTaxa(visitor.taxa());
    if (pub.contains(Taxonomy.commonName("nonavian"))) {
      answer.addAllTaxa(Set.of("nonavian"));
    }
    answer.addAllReviewers(visitor.reviewers());
    return answer;
  }

  private static class PublicationVisitor extends AbstractVisitor {

    private final Set<String> taxa;
    private final Set<String> reviewers;

    private PublicationVisitor() {
      this.taxa = new HashSet<>();
      this.reviewers = new HashSet<>();
    }

    private Set<String> taxa() {
      return this.taxa;
    }

    private Set<String> reviewers() {
      return this.reviewers;
    }

    @Override
    public void visit(Link link) {
      final String uriStr = link.getDestination();
      final Uri uri;
      try {
        uri = Uri.parse(uriStr);
      } catch (Exception e) {
        super.visit(link);
        return;
      }
      if (uri.hostName().contains("ebird.org")) {
        taxa().add(uriStr.substring(uriStr.lastIndexOf("/") + 1));
      }
    }

    @Override
    public void visit(Text text) {
      final String reviewerPrefix = "Reviewed by: ";
      if (text.getLiteral().startsWith(reviewerPrefix)) {
        final String reviewerDump = text.getLiteral().substring(reviewerPrefix.length());
        final String[] reviewers = reviewerDump.split(", ");
        for (String reviewer : reviewers) {
          reviewers().add(reviewer);
        }
      }
      super.visit(text); // shouldn't be needed for Text, but why not
    }
  }

  private static final Parser PARSER = Parser.builder().build();

}
