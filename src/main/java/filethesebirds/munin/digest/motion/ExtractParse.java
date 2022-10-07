// Copyright 2015-2022 Swim.inc
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

package filethesebirds.munin.digest.motion;

import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.Taxonomy;
import filethesebirds.munin.digest.Users;
import filethesebirds.munin.digest.motion.commonmark.HintExtension;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.parser.Parser;

public class ExtractParse {

  private static final Parser PARSER = Parser.builder()
      .extensions(List.of(AutolinkExtension.create(), new HintExtension()))
      .build();

  private static int commandLength(String body) {
    for (int i = 1; i < body.length(); i++) {
      final char c = body.charAt(i);
      if (!(Character.isLetterOrDigit(c) || Character.isWhitespace(c)
            || c == ',')) {
        return i;
      }
    }
    return body.length();
  }

  private static int fastPathTaxa(String body, int maxCommandLen, Set<String> taxa) {
    final int firstNewlineIdx = body.indexOf('\n');
    final String command = body.substring(0, firstNewlineIdx < 0 ? body.length() : firstNewlineIdx);
    final int commandLength = commandLength(command);
    // Suspect user error for excessively long commands
    if (command.length() > maxCommandLen) {
      return 0;
    }
    final String[] split = command.substring(0, commandLength).split("(\\s|,)+");
    int added = 0;
    for (int i = 1; i < split.length; i++) {
      if (Taxonomy.containsCode(split[i])) {
        taxa.add(split[i]);
        added++;
      }
    }
    return added;
  }

  // FIXME: this can surely be cleaned up
  public static Extract parseSuggestionBased(String body) {
    // fast-path
    if (body.startsWith("!addTaxa ") || body.startsWith("!addtaxa ")) {
      final Set<String> delta = new HashSet<>();
      fastPathTaxa(body, 128, delta);
      return ImmutableExtract.create(ImmutableSuggestion.create(delta), null, null);
    }
    int lower = body.indexOf("\n!addTaxa ");
    lower = lower < 0 ? Integer.MAX_VALUE : lower;
    int candidate = body.indexOf("\n!addtaxa ");
    candidate = candidate < 0 ? Integer.MAX_VALUE : candidate;
    lower = Math.min(lower, candidate);
    if (lower < Integer.MAX_VALUE) {
      final Set<String> delta = new HashSet<>();
      fastPathTaxa(body.substring(lower + 1), 128, delta);
      return ImmutableExtract.create(ImmutableSuggestion.create(delta), null, null);
    }
    // slow-path
    final ExtractingVisitor visitor = new ExtractingVisitor();
    PARSER.parse(body).accept(visitor);
    return ImmutableExtract.create(ImmutableSuggestion.create(visitor.plusTaxa()),
        visitor.plusSpeciesHints(), visitor.plusTaxonHints());
  }

  public static Extract parseReviewBased(String reviewer, String body) {
    // fast-path
    if (body.startsWith("!overrideTaxa ") || body.startsWith("!overridetaxa ")) {
      final Set<String> delta = new HashSet<>();
      if (fastPathTaxa(body, 256, delta) > 0) {
        return ImmutableExtract.create(ImmutableReview.override(reviewer, delta), null, null);
      }
      // override with mistake is an empty suggestion, not an empty review
      return ImmutableExtract.create(ImmutableSuggestion.empty(), null, null);
    }
    if (body.startsWith("!addTaxa") || body.startsWith("!addtaxa")) {
      final Set<String> delta = new HashSet<>();
      if (fastPathTaxa(body, 256, delta) > 0) {
        return ImmutableExtract.create(ImmutableReview.plus(reviewer, delta), null, null);
      }
      // addTaxa with mistakes is an empty suggestion, not an empty review
      return ImmutableExtract.create(ImmutableSuggestion.empty(), null, null);
    }
    int lower = body.indexOf("\n!addTaxa ");
    lower = lower < 0 ? Integer.MAX_VALUE : lower;
    int candidate = body.indexOf("\n!addtaxa ");
    candidate = candidate < 0 ? Integer.MAX_VALUE : candidate;
    lower = Math.min(lower, candidate);
    if (lower < Integer.MAX_VALUE) {
      final Set<String> delta = new HashSet<>();
      if (fastPathTaxa(body.substring(lower + 1), 256, delta) > 0) {
        return ImmutableExtract.create(ImmutableReview.plus(reviewer, delta), null, null);
      }
      // addTaxa with mistakes is an empty suggestion
      return ImmutableExtract.create(ImmutableSuggestion.empty(), null, null);
    }
    // slow-path
    final ExtractingVisitor visitor = new ExtractingVisitor();
    PARSER.parse(body).accept(visitor);
    return ImmutableExtract.create(ImmutableReview.plus(reviewer, visitor.plusTaxa()),
        visitor.plusSpeciesHints(), visitor.plusTaxonHints());
  }

  public static Extract parseComment(Comment comment) {
    if (Users.userIsNonparticipant(comment.author())
        || comment.body().contains("!np")) {
      return ImmutableExtract.empty();
    }
    if (Users.userIsReviewer(comment.author())
        && !comment.body().contains("!nr")) {
      return parseReviewBased(comment.author(), comment.body());
    }
    return parseSuggestionBased(comment.body());
  }

}
