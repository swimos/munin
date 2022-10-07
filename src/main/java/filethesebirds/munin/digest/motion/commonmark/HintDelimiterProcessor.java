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

package filethesebirds.munin.digest.motion.commonmark;

import org.commonmark.node.Node;
import org.commonmark.node.Nodes;
import org.commonmark.node.SourceSpans;
import org.commonmark.node.Text;
import org.commonmark.parser.delimiter.DelimiterProcessor;
import org.commonmark.parser.delimiter.DelimiterRun;

class HintDelimiterProcessor implements DelimiterProcessor {

  @Override
  public char getOpeningCharacter() {
    return '+';
  }

  @Override
  public char getClosingCharacter() {
    return '+';
  }

  @Override
  public int getMinLength() {
    return 1;
  }

  @Override
  public int process(DelimiterRun openingRun, DelimiterRun closingRun) {
    if ((openingRun.canClose() || closingRun.canOpen()) &&
        closingRun.originalLength() % 3 != 0 &&
        (openingRun.originalLength() + closingRun.originalLength()) % 3 == 0) {
      return 0;
    }

    int usedDelimiters;
    Node hint;
    // calculate actual number of delimiters used from this closer
    if (openingRun.length() >= 2 && closingRun.length() >= 2) {
      usedDelimiters = 2;
      hint = new TaxonHint();
    } else {
      usedDelimiters = 1;
      hint = new SpeciesHint();
    }

    SourceSpans sourceSpans = SourceSpans.empty();
    sourceSpans.addAllFrom(openingRun.getOpeners(usedDelimiters));

    Text opener = openingRun.getOpener();
    for (Node node : Nodes.between(opener, closingRun.getCloser())) {
      hint.appendChild(node);
      sourceSpans.addAll(node.getSourceSpans());
    }

    sourceSpans.addAllFrom(closingRun.getClosers(usedDelimiters));

    hint.setSourceSpans(sourceSpans.getSourceSpans());
    opener.insertAfter(hint);

    return usedDelimiters;
  }

}
