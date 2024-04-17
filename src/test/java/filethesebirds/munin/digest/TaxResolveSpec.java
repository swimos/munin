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

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class TaxResolveSpec {

  private static final TaxResolve TR = new TaxResolve();

  @Test
  public void testUsSpecies() {
    assertEquals(TR.us.resolveSpecies("Bläck cApp-ChickAdEe").code, "bkcchi");
    assertEquals(TR.us.resolveSpecies("european collared dove").code, "eucdov");
  }

  @Test
  public void testUkSpecies() {
    assertEquals(TR.uk.resolveSpecies("Common Starling").code, "eursta");
  }

  @Test
  public void testAmbiguous() {
    assertEquals(TR.resolve("Common        \t\t Mägpie"), "eurmag1");
    assertEquals(TR.resolve("Magpie"), "eurmag1");
    assertEquals(TR.resolve("Common Myna"), "commyn");
    assertEquals(TR.resolve("Hill Myna"), "hilmyn");
    assertEquals(TR.resolve("yellowlegs"), "greyel");
  }

  @Test
  public void testResolveHasHybrid() {
    assertEquals(TR.resolveHasHybrid("Brewster's Warbler"), "brewar");
    assertEquals(TR.resolveHasHybrid("Lawrence's Warbler"), "lawwar");
    assertEquals(TR.resolveHasHybrid("Blue Golden Warbler"), "x00669");
  }

  @Test
  public void testResolvePlain() {
    assertEquals(TR.resolvePlain("Teal"), "egwtea1");
    assertEquals(TR.resolvePlain("scaup"), "gresca");
    assertEquals(TR.resolvePlain("Crested Caracara"), "y00678");
    assertEquals(TR.resolvePlain("pheasant"), "rinphe1");
  }

  @Test
  public void testRawResolve() {
    assertEquals(TR.resolve("domestic chicken"), "redjun1");
    assertEquals(TR.resolve("Blue-winged Golden-winged hybrid"), "x00669");
    assertEquals(TR.resolve("domestic  feral   pigeon"), "rocpig1");
    assertEquals(TR.resolve("domestic  muscovy"), "musduc3");
    assertEquals(TR.resolve("integrade northern flicker"), "rxyfli");
    assertEquals(TR.resolve("long-billed/short-billed dowitcher"), "dowitc");
    assertEquals(TR.resolve("larus sp."), "larus1");
    assertEquals(TR.resolve("Golden x Blue winged"), "x00669");
    assertEquals(TR.resolve("myrtle  x audubon's"), "yerwar3");
    assertEquals(TR.resolve("Yellow-rumped warbler (myrtle)"), "myrwar");
    assertEquals(TR.resolve("Slate-colored Junco"), "slcjun");
    assertEquals(TR.resolve("audubon's warbler"), "audwar");
    assertEquals(TR.resolve("pied wagtail"), "whiwag3");
    assertEquals(TR.resolve("brent"), "brant");
    assertEquals(TR.resolve("Non-avian"), "nonavian");
    assertEquals(TR.resolve("nonavian"), "nonavian");

    assertEquals(TR.resolve("turdus migratorius"), "amerob");
    assertEquals(TR.resolve("setophaga coronata x auduboni"), "yerwar3");
    assertEquals(TR.resolve("anser anser"), "gragoo");
    assertEquals(TR.resolve("anser anser (anser)"), "gragoo2");
  }

}