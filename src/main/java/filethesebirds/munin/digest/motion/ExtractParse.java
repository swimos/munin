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

package filethesebirds.munin.digest.motion;

import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.Taxonomy;
import filethesebirds.munin.digest.Users;
import filethesebirds.munin.digest.motion.commonmark.HintExtension;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.parser.Parser;

public class ExtractParse {

  private static final Parser PARSER = Parser.builder()
      .extensions(List.of(AutolinkExtension.create(), new HintExtension()))
      .build();

  private static int commandLength(String body, int n) {
    for (int i = "!addTaxa ".length(); i < n; i++) {
      final char c = body.charAt(i);
      final boolean legal = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || (c == 'T')
          || (c == ',') || (c != '\n' && Character.isWhitespace(c));
      if (!legal) {
        return i;
      }
    }
    return n;
  }

  private static int fastPathTaxa(String body, int maxCommandLen, Set<String> taxa) {
    final int commandLength = commandLength(body, Math.min(body.length(), maxCommandLen + 1));
    // Suspect user error for excessively long commands
    if (commandLength > maxCommandLen) {
      return 0;
    }
    final String[] split = body.substring(0, commandLength).split("(\\s|,)+");
    int added = 0;
    for (int i = 1; i < split.length; i++) {
      if (Taxonomy.containsCode(split[i])) {
        taxa.add(split[i]);
        added++;
      }
    }
    return added;
  }

  private static int seekToCommand(String body, int from) {
    if (body.startsWith("!addTaxa", from) || body.startsWith("!addtaxa", from)) {
      return from + 1;
    } else if (body.startsWith("!overrideTaxa", from) || body.startsWith("!overridetaxa", from)) {
      return -from - 1;
    }
    final int seek = body.indexOf("\n!", from);
    return seek < 0 ? 0 : seekToCommand(body, seek + 1);
  }

  private static Extract parse(String body, int maxCommandLen,
                               BiFunction<Set<String>, Integer, Extract> plusGenerator,
                               BiFunction<Set<String>, Integer, Extract> overrideGenerator,
                               Extract empty, Function<ExtractingVisitor, Extract> slowGenerator) {
    final int seek = seekToCommand(body, 0);
    if (seek > 0) {
      final Set<String> delta = new HashSet<>();
      final int taxa = fastPathTaxa(body.substring(seek - 1), maxCommandLen, delta);
      return plusGenerator.apply(delta, taxa);
    } else if (seek < 0) {
      final Set<String> delta = new HashSet<>();
      final int taxa = fastPathTaxa(body.substring(-(seek + 1)), maxCommandLen, delta);
      return overrideGenerator.apply(delta, taxa);
    }
    // fast-path: no URLs present, and not enough + signs to generate hints
    if (!body.contains("http")) {
      final int firstPlus = body.indexOf('+');
      if (firstPlus < 0 || body.lastIndexOf('+') == firstPlus) {
        return empty;
      }
    }
    // slow path
    final ExtractingVisitor visitor = new ExtractingVisitor();
    PARSER.parse(body).accept(visitor);
    return slowGenerator.apply(visitor);
  }

  public static Extract parseSuggestionBased(String body) {
    return parse(body, 256,
        (d, i) -> ImmutableExtract.create(ImmutableSuggestion.plus(d), null, null),
        (d, i) -> ImmutableExtract.create(ImmutableSuggestion.override(d), null, null),
        ImmutableExtract.create(ImmutableSuggestion.empty(), null, null),
        v -> ImmutableExtract.create(ImmutableSuggestion.plus(v.plusTaxa()), v.plusHints(), v.plusVagueHints()));
  }

  public static Extract parseReviewBased(String reviewer, String body) {
    return parse(body, 512,
        (d, i) -> i > 0
            ? ImmutableExtract.create(ImmutableReview.plus(reviewer, d), null, null)
            // Reviewer !addTaxa with errors is an empty suggestion, not an empty review
            : ImmutableExtract.create(ImmutableSuggestion.empty(), null, null),
        (d, i) -> i > 0
            ? ImmutableExtract.create(ImmutableReview.override(reviewer, d), null, null)
            // Reviewer !overrideTaxa with errors is an empty suggestion, not an empty review
            : ImmutableExtract.create(ImmutableSuggestion.empty(), null, null),
        ImmutableExtract.create(ImmutableReview.empty(reviewer), null, null),
        v -> ImmutableExtract.create(ImmutableReview.plus(reviewer, v.plusTaxa()), v.plusHints(), v.plusVagueHints()));
  }

  public static Extract parseComment(Comment comment) {
    if (Users.userIsNonparticipant(comment.author())
        || comment.body().contains("!np")) {
      return ImmutableExtract.empty();
    }
    if (Users.userIsReviewer(comment.author())
        && !comment.body().contains("!nr")) {
      return parseReviewBased(comment.author(), unescapedBody(comment));
    }
    return parseSuggestionBased(unescapedBody(comment));
  }

  private static String unescapedBody(Comment comment) {
    return comment.body().replaceAll("\\A\\\\+", "+")
        .replaceAll("\n\\\\+", "\n+");
  }

}
