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

package filethesebirds.munin.digest;

import filethesebirds.munin.Utils;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class TaxResolve {

  final Map<String, String[]> taxonomy;
  final Localed us;
  final Localed uk;
  final Localed sci;

  public TaxResolve() {
    this.taxonomy = loadTaxonomy();
    this.us = new Localed("taxonomy/2023-us.csv");
    this.uk = new Localed("taxonomy/2023-uk.csv");
    this.sci = new Localed("taxonomy/2023-sci.csv");
  }

  private static Map<String, String[]> loadTaxonomy() {
    final Map<String, String[]> result = new HashMap<>(32000);
    try (InputStream is = Utils.openConfigFile(null, "taxonomy/2023-com.csv");
         InputStreamReader isr = new InputStreamReader(is);
         BufferedReader br = new BufferedReader(isr)) {
      String line;
      while ((line = br.readLine()) != null) {
        final String[] split = line.split(",");
        if (split.length >= 3) {
          final String code = split[0];
          if (codeIsValid(code)) {
            result.put(code, new String[]{split[1], split[2]});
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to load taxonomy", e);
    }
    return result;
  }

  private static boolean codeIsValid(String code) {
    if (2 > code.length() || code.length() > 8) { // (ou, banowl13)
      return false;
    }
    for (int i = 0; i < code.length(); i++) {
      final char c = code.charAt(i);
      if (!(('a' <= c && c <= 'z') || ('0' <= c && c <= '9'))) {
        return false;
      }
    }
    return true;
  }

  public boolean containsCode(String code) {
    return code != null && this.taxonomy.containsKey(code);
  }

  public String commonName(String code) {
    if (containsCode(code)) {
      return this.taxonomy.get(code)[0];
    }
    return null;
  }

  public int ordinal(String code) {
    if (containsCode(code)) {
      return Integer.parseInt(this.taxonomy.get(code)[1]);
    }
    return -1;
  }

  public String resolve(String query) {
    if (query.contains("chicken") && !query.contains("prairie")) {
      return "redjun1";
    } else if (query.contains("nonavian")) {
      return "nonavian";
    }
    String sanitized = sanitizeQuery(query);
    String simplified = simplifySanitizedQuery(sanitized);
    if (sanitized.contains("hybrid")) {
      return resolveHasHybrid(simplified);
    } else if (sanitized.contains("domestic") || query.contains("feral")) {
      return resolveHasDomestic(simplified);
    } else if (sanitized.contains("intergrade") || sanitized.contains("integrade")) {
      return resolveHasIntergrade(simplified);
    } else if (sanitized.contains("/")) {
      return resolveHasSlash(simplified);
    } else if (sanitized.startsWith("sp ") || sanitized.endsWith(" sp")  || sanitized.contains(" sp.")) {
      return resolveHasSp(simplified);
    } else if (sanitized.contains(" x ")) {
      return resolveHasX(simplified);
    } else if (sanitized.contains("(") || sanitized.contains("subsp") || sanitized.contains("ssp")) {
      return resolveHasSubsp(simplified);
    } else {
      return resolvePlain(simplified);
    }
  }

  static String sanitizeQuery(String query) {
    query = truncateCommonPrefixes(replaceDiacritics(query.trim()));
    final StringBuilder sb = new StringBuilder(query.length());
    for (int i = 0; i < query.length(); i++) {
      final char c = query.charAt(i);
      if (('a' <= c && c <= 'z') || ('0' <= c && c <= '9') || ('/' == c) || ('(' == c) || (')' == c) || ('.' == c)) {
        sb.append(c);
      } else if ('A' <= c && c <= 'Z') {
        sb.append((char) (c + ('a' - 'A')));
      } else if ((c == '-' || Character.isWhitespace(c))
          && i > 0 && !Character.isWhitespace(query.charAt(i - 1))) {
        sb.append(" ");
      }
    }
    return sb.toString();
  }

  static String simplifySanitizedQuery(String sanitized) {
    // TODO: ed, ing
    return sanitized.replaceAll("conure", "parakeet")
        .replaceAll("feral", "")
        .replace(sanitized.contains("goose") ? "" : "domestic", "")
        .replaceAll("intergrade", "")
        .replaceAll("integrade", "")
        .replaceAll("\\bsubsp", "") // FIXME: this doesn't look right
        .replace("ssp", "")
        .replaceAll("\\bsp\\b", "")
        .replace(".", "")
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

  String resolveHasHybrid(String simplified) {
    // TODO: intergrade?
    final Match common = resolve(
        () -> this.us.resolveHybrid(simplified),
        () -> this.uk.resolveHybrid(simplified));
    return common.score > 0 ? common.code
        : resolve(() -> this.sci.resolveHybrid(simplified)).code;
  }
//
  String resolveHasDomestic(String simplified) {
    final Match common = resolve(
        () -> this.us.resolveDomestic(simplified),
        () -> this.uk.resolveDomestic(simplified));
    return common.score > 0 ? common.code
        : resolve(() -> this.sci.resolveDomestic(simplified)).code;
  }

  String resolveHasIntergrade(String simplified) {
    final Match common = resolve(
        () -> this.us.resolveIntergrade(simplified),
        () -> this.uk.resolveIntergrade(simplified));
    return common.score > 0 ? common.code
        : resolve(() -> this.sci.resolveIntergrade(simplified)).code;
  }

  String resolveHasSlash(String simplified) {
    // TODO: species, hybrid, intergrade?
    final Match common = resolve(
        () -> this.us.resolveSlash(simplified),
        () -> this.uk.resolveSlash(simplified));
    return common.score > 0 ? common.code
        : resolve(() -> this.sci.resolveSlash(simplified)).code;
  }

  String resolveHasSp(String simplified) {
    final Match common = resolve(
        () -> this.us.resolveSpuh(simplified),
        () -> this.uk.resolveSpuh(simplified));
    return common.score > 0 ? common.code
        : resolve(() -> this.sci.resolveSpuh(simplified)).code;
  }

  String resolveHasX(String simplified) {
    final Match common = resolve(
        () -> this.us.resolveHybrid(simplified),
        () -> this.uk.resolveHybrid(simplified),
        () -> this.us.resolveIntergrade(simplified),
        () -> this.uk.resolveIntergrade(simplified));
    return common.score > 0 ? common.code
        : resolve(
            () -> this.sci.resolveHybrid(simplified),
            () -> this.sci.resolveIntergrade(simplified))
        .code;
  }

  String resolveHasSubsp(String simplified) {
    final Match common = resolve(
        () -> this.us.resolveSubspecies(simplified),
        () -> this.uk.resolveSubspecies(simplified));
    return common.score > 0 ? common.code
        : resolve(() -> this.sci.resolveSubspecies(simplified)).code;
  }

  String resolvePlain(String simplified) {
    final Match common = resolve(
        () -> this.us.resolveSpecies(simplified),
        () -> this.uk.resolveSpecies(simplified),
        () -> this.us.resolveSubspecies(simplified),
        () -> this.uk.resolveSubspecies(simplified),
        () -> this.us.resolveHybrid(simplified),
        () -> this.uk.resolveHybrid(simplified));
    return common.score > 0 ? common.code
        : resolve(
            () -> this.sci.resolveSpecies(simplified),
            () -> this.sci.resolveSubspecies(simplified),
            () -> this.sci.resolveHybrid(simplified))
        .code;
  }

  @SafeVarargs
  private static Match resolve(Supplier<Match>... fns) {
    Match soFar = Match.FAIL;
    for (Supplier<Match> fn : fns) {
      final Match candidate = fn.get();
      if (candidate.score == 100) {
        return candidate;
      } else if (candidate.compare(soFar) > 0) {
        soFar = candidate;
      }
    }
    return soFar;
  }

  private static String replaceDiacritics(String s) {
    return s.replace('ä', 'a')
        .replace('ñ', 'n')
        .replace('ö', 'o')
        .replace('ü', 'u');
  }

  private static String truncateCommonPrefixes(String s) {
    return s.replaceAll("(\\beur[a-z]+\\b)", "eur");
  }

  static class Localed {

    final Map<String, List<String>> speciesCom;
    final Map<String, List<String>> domesticCom;
    final Map<String, List<String>> hybridCom;
    final Map<String, List<String>> subspeciesCom;
    final Map<String, List<String>> intergradeCom;
    final Map<String, List<String>> slashCom;
    final Map<String, List<String>> spuhCom;

    Localed(String path) {
      this.speciesCom = new HashMap<>();
      this.domesticCom = new HashMap<>();
      this.hybridCom = new HashMap<>();
      this.subspeciesCom = new HashMap<>();
      this.intergradeCom = new HashMap<>();
      this.slashCom = new HashMap<>();
      this.spuhCom = new HashMap<>();
      loadResource(path);
    }

    void loadResource(String path) {
      try (InputStream is = Utils.openConfigFile(null, path);
           InputStreamReader isr = new InputStreamReader(is);
           BufferedReader br = new BufferedReader(isr)) {
        String line;
        while ((line = br.readLine()) != null) {
          final String[] split = line.split(",");
          if (split.length >= 3) {
            final String cat = split[3];
            final Map<String, List<String>> target;
            switch (cat) {
              case "species":
                target = this.speciesCom;
                break;
              case "domestic":
                target = this.domesticCom;
                break;
              case "hybrid":
                target = this.hybridCom;
                break;
              case "issf":
              case "form":
                target = this.subspeciesCom;
//                target.put(split[0], List.copyOf(new LinkedHashSet<>(tokenizeCom(split[1]))));
//                continue;
                break;
              case "intergrade":
                target = this.intergradeCom;
                break;
              case "slash":
                target = this.slashCom;
                break;
              case "spuh":
                target = this.spuhCom;
                target.put(split[0], tokenizeCom(split[1].replaceAll("sp\\.", "")));
                continue;
              default:
                continue;
            }
            final String code = split[0];
//            final List<String> value = tokenizeCom(split[1]);
            final List<String> value = List.copyOf(new LinkedHashSet<>(tokenizeCom(split[1])));
            target.put(code, value);
          }
        }
      } catch (Exception e) {
        throw new RuntimeException("Failed to load taxonomy", e);
      }
    }

    private static List<String> tokenizeCom(String com) {
      com = com.toLowerCase(Locale.ROOT);
      com = replaceDiacritics(com);
      com = truncateCommonPrefixes(com);
      return List.of(replaceDiacritics(com)
          .replaceAll("(form)|(group)\\)", "")
          .replaceAll("type intergrade", "")
          .replaceAll("['\"()]", "")
          .replaceAll("domestic type", "")
          .replaceAll(" x ", " ")
          .replaceAll("hybrid", "")
          .split("[^a-zA-Z.]+"));
    }

    Match resolveSpecies(String query) {
      return resolve(this.speciesCom, query);
    }

    Match resolveDomestic(String query) {
      return resolve(this.domesticCom, query);
    }
//
    Match resolveHybrid(String query) {
      return resolve(this.hybridCom, query);
    }
//
    Match resolveSubspecies(String query) {
      return resolve(this.subspeciesCom, query);
    }

    Match resolveIntergrade(String query) {
      return resolve(this.intergradeCom, query);
    }

    Match resolveSlash(String query) {
      return resolve(this.slashCom, query);
    }

    Match resolveSpuh(String query) {
      return resolve(this.spuhCom, query);
    }

    static Match resolve(Map<String, List<String>> map, String query) {
      return resolve(map, tokenizeCom(query));
    }

    static Match resolve(Map<String, List<String>> map, List<String> input) {
      Match soFar = Match.FAIL;
      for (Map.Entry<String, List<String>> entry : map.entrySet()) {
        final Match match = Match.compute(entry.getKey(), input, entry.getValue());
        if (match.score == 100) {
          return match;
        }
        if (match.compare(soFar) > 0) {
          soFar = match;
        }
      }
      return soFar;
    }

  }

  /**
   * The result of a function like {@code compare(List<String> inputTokens,
   * List<String> candidateTokens)}.
   */
  static class Match {

    final String code;
    // 0: at least one inputToken is not a prefix of any candidateToken
    // 100: every candidateToken is prefixed by an element of inputTokens
    // (0 < 10a + b < 99): a candidateTokens are prefixed by an element of
    //  inputTokens, plus b=:
    //  - 9: candidateTokens contains "common"
    //  - 8: candidateTokens contains "eur"
    //  - 7: candidateTokens contains "northern"
    //  - 6: candidateTokens contains "great" or "greater"
    //  - 5: candidateTokens contains "american"
    //  - 4: candidateTokens contains "eastern"
    //  - 3: candidateTokens contains "western"
    //  - 2: candidateTokens contains "southern"
    final int score;
    final List<String> tokens;

    private Match(String code, int score, List<String> tokens) {
      this.code = code;
      this.score = score;
      this.tokens = tokens;
    }

    static Match compute(String code, List<String> inputTokens, List<String> candidateTokens) {
      final boolean[] seen = new boolean[candidateTokens.size()];
      int score = 0;
      int seenCount = 0;
      outer:
      for (String p : inputTokens) {
        for (int i = 0; i < candidateTokens.size(); i++) {
          final String s = candidateTokens.get(i);
          if (s.startsWith(p)) {
            if (!seen[i]) {
              score += 10;
              seen[i] = true;
              seenCount++;
            }
            continue outer;
          }
        }
        return Match.FAIL;
      }
      return seenCount == candidateTokens.size() ? new Match(code, 100, candidateTokens)
          : score == 0 ? Match.FAIL
          : new Match(
              code,
              Math.min(commonTokenIncrement(candidateTokens, score), 99),
              candidateTokens);
    }

    private static int commonTokenIncrement(List<String> tokens, int score) {
      int delta = 0;
      for (String s : tokens) {
        switch (s) {
          case "common":
            delta = 9;
            break;
          case "eur":
            delta = Math.max(delta, 8);
            break;
          case "northern":
            delta = Math.max(delta, 7);
            break;
          case "great":
          case "greater":
            delta = Math.max(delta, 6);
            break;
          case "american":
            delta = Math.max(delta, 5);
            break;
          case "eastern":
            delta = Math.max(delta, 4);
            break;
          case "western":
            delta = Math.max(delta, 3);
            break;
          case "southern":
            delta = Math.max(delta, 2);
            break;
          default:
            break;
        }
      }
      return score + delta;
    }

    @Override
    public String toString() {
      return "Match{" +
          "code='" + code + '\'' +
          ", score=" + score +
          ", tokens=" + tokens +
          '}';
    }

    int compare(Match dst) {
      final int diff = this.score - dst.score;
      return diff == 0 ? dst.tokens.size() - this.tokens.size() : diff;
    }

    static final Match FAIL = new Match("", 0, Collections.emptyList());

  }

}
