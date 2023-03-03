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

  private static String singularize(String s) {
    if (s.endsWith("ies")) {
      return s.substring(0, s.length() - 3);
    } else if (s.endsWith("es")) {
      return s.substring(0, s.length() - 2);
    } else if (s.endsWith("s")) {
      return s.substring(0, s.length() - 1);
    } else if (s.endsWith("mice")) {
      return s.replace("mice", "mouse");
    } else if (s.endsWith("eese")) {
      return s.replace("eese", "oose");
    }
    return s;
  }

  private static String cleanHint(String s) {
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
        final String filtered = cleanHint(singularize(((Text) customNode.getFirstChild()).getLiteral()));
        plusHints().add(filtered);
      }
    } else if (customNode instanceof VagueHint) {
      if (customNode.getFirstChild() instanceof Text) {
        final String filtered = cleanHint(singularize(((Text) customNode.getFirstChild()).getLiteral()));
        plusVagueHints().add(filtered);
      }
    }
    super.visit(customNode);
  }

}
