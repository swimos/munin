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

import filethesebirds.munin.digest.motion.commonmark.Hint;
import filethesebirds.munin.digest.motion.commonmark.VagueHint;
import java.util.HashSet;
import java.util.Set;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Link;
import org.commonmark.node.Text;
import swim.uri.Uri;

class ExtractingVisitor extends AbstractVisitor {

  private final Set<String> plusTaxa;
  private final Set<String> plusHints;
  private final Set<String> plusVagueHints;

  ExtractingVisitor() {
    this.plusTaxa = new HashSet<>();
    this.plusHints = new HashSet<>();
    this.plusVagueHints = new HashSet<>();
  }

  Set<String> plusTaxa() {
    return this.plusTaxa;
  }

  Set<String> plusHints() {
    return this.plusHints;
  }

  Set<String> plusVagueHints() {
    return this.plusVagueHints;
  }

  private static String cleanHint(String s) {
    s = s.trim();
    final StringBuilder sb = new StringBuilder(s.length() + 8);
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (('a' <= c && c <= 'z') || ('-' == c) || ('/' == c) || ('(' == c) || (')' == c) || ('.' == c)) {
        sb.append(c);
      } else if ('A' <= c && c <= 'Z') {
        sb.append((char) (c + ('a' - 'A')));
      } else if (Character.isWhitespace(c)
          && i > 0 && !Character.isWhitespace(s.charAt(i - 1))) {
        sb.append("%20");
      }
    }
    return sb.toString();
  }

  private static String normalize(String s) {
    // TODO: ed, ing
    return s.replaceAll("european", "eur")
        .replaceAll("eurasian", "eur")
        .replaceAll("conure", "parakeet")
        .replaceAll("greater", "great")
        .replaceAll("vermillion", "vermilion")
        .replaceAll("species", "sp.")
        .replaceAll("mice\\b", "mouse")
        .replaceAll("eeses\\b", "oose")
        .replaceAll("eese\\b", "oose")
        .replaceAll("sss\\b", "ss")
        .replaceAll("ies\\b", "")
        .replaceAll("es\\b", "")
        .replaceAll("s\\b", "");
  }

  @Override
  public void visit(Link link) {
    if ("".equals(link.getTitle())) {
      super.visit(link);
      return;
    }
    final String uriStr = link.getDestination();
    final Uri uri;
    try {
      uri = Uri.parse(uriStr);
    } catch (Exception e) {
      super.visit(link);
      return;
    }
    CommonUrlExtract.extractFromUri(uri, plusTaxa(), plusHints());
    super.visit(link);
  }

  @Override
  public void visit(CustomNode customNode) {
    if (customNode instanceof Hint) {
      if (customNode.getFirstChild() instanceof Text) {
        final String filtered = normalize(cleanHint(((Text) customNode.getFirstChild()).getLiteral()));
        plusHints().add(filtered);
      }
    } else if (customNode instanceof VagueHint) {
      if (customNode.getFirstChild() instanceof Text) {
        final String filtered = normalize(cleanHint(((Text) customNode.getFirstChild()).getLiteral()));
        plusVagueHints().add(filtered);
      }
    }
    super.visit(customNode);
  }

}
