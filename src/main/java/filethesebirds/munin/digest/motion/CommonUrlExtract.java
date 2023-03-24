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

import filethesebirds.munin.digest.Taxonomy;
import java.util.Locale;
import java.util.Set;
import swim.uri.Uri;
import swim.uri.UriPath;
import swim.uri.UriQuery;

class CommonUrlExtract {

  private static String sanitizeAllAboutBirdsHint(String raw) {
    // TODO: ed, ing
    return raw.replace("_", "%20")
        .replaceAll("sss\\b", "ss")
        .replaceAll("s\\b", "");
  }

  private static boolean forAllAboutBirds(UriPath path, Set<String> hints) {
    final String prefix = "/guide/";
    final String pathStr = path.toString().toLowerCase(Locale.ROOT);
    if (pathStr.startsWith(prefix)) {
      final String tail = pathStr.substring(prefix.length());
      int slashIndex = tail.indexOf('/');
      if (slashIndex < 0) {
        slashIndex = tail.length();
      }
      final String rawHint = tail.substring(0, slashIndex);
      if (rawHint.startsWith("asset")) {
        return false;
      }
      hints.add(sanitizeAllAboutBirdsHint(rawHint));
      return true;
    }
    return false;
  }

  private static boolean forEbird(UriPath path, Set<String> taxa) {
    final String prefix = "/species/";
    final String pathStr = path.toString().toLowerCase(Locale.ROOT);
    if (pathStr.startsWith(prefix)) {
      final String tail = pathStr.substring(prefix.length());
      // trim to alphanum only
      int tailLength;
      for (tailLength = 0; tailLength < tail.length(); tailLength++) {
        if (!Character.isLetterOrDigit(tail.charAt(tailLength))) {
          break;
        }
      }
      final String trimmedTail = tail.substring(0, tailLength);
      if (Taxonomy.containsCode(trimmedTail)) {
        taxa.add(trimmedTail);
        return true;
      }
    }
    return false;
  }

  private static boolean forFeatherBase(UriPath path) {
    return false;
  }

  private static boolean forMediaEbird(UriPath path, UriQuery query, Set<String> taxa) {
    final String pathStr = path.toString().toLowerCase(Locale.ROOT);
    if (pathStr.startsWith("/catalog")) {
      final String code = query.get("taxonCode");
      if (Taxonomy.containsCode(code)) {
        taxa.add(code);
        return true;
      }
    }
    return false;
  }

  private static boolean forWikipedia(UriPath path, Set<String> hints) {
    return false;
  }

  static boolean extractFromUri(Uri uri, Set<String> taxa, Set<String> hints) {
    final String extractedHostName = uri.hostName();
    if (extractedHostName == null) {
      return false;
    }
    final String hostName = extractedHostName.toLowerCase(Locale.ROOT);
    if (hostName.endsWith("allaboutbirds.org")) {
      return forAllAboutBirds(uri.path(), hints);
    } else if (hostName.endsWith("media.ebird.org") || hostName.endsWith("search.macaulaylibrary.org")) {
      return forMediaEbird(uri.path(), uri.query(), taxa);
    } else if (hostName.endsWith("ebird.org")) { // don't reorder
      return forEbird(uri.path(), taxa);
    }
    return false;
  }

}
