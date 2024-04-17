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

import filethesebirds.munin.digest.TaxResolve;
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

  private final TaxResolve taxonomy;
  private final Set<String> plusTaxa;
  private final Set<String> plusHints;
  private final Set<String> plusVagueHints;

  ExtractingVisitor(TaxResolve taxonomy) {
    this.taxonomy = taxonomy;
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
    CommonUrlExtract.extractFromUri(this.taxonomy, uri, plusTaxa(), plusHints());
    super.visit(link);
  }

  @Override
  public void visit(CustomNode customNode) {
    if (customNode instanceof Hint) {
      if (customNode.getFirstChild() instanceof Text) {
        plusHints().add(((Text) customNode.getFirstChild()).getLiteral());
      }
    } else if (customNode instanceof VagueHint) {
      if (customNode.getFirstChild() instanceof Text) {
        plusVagueHints().add(((Text) customNode.getFirstChild()).getLiteral());
      }
    }
    super.visit(customNode);
  }

}
