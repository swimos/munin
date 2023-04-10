package filethesebirds.munin.connect.vault;

import filethesebirds.munin.digest.Submission;
import org.testng.annotations.Test;

public class VaultClientSpec {

  @Test
  public void testDrySubmissionsUpsert() {
    final Submission[] submissions = new Submission[]{
        new Submission("ybm8bf", "Flycatcher Friday(on a Sunday): Are these two different species? NY",
            Submission.Location.NORTH_AMERICA, 	"https://b.thumbs.redditmedia.com/PhNOPlhgGPeLEARuV-R5pV6d8b6OdCRTGbUh70F3SBo.jpg",
            1666542881L, 6, 5),
        new Submission("ybq5qc", "This is from one of my illustrated Harry Potter books. Are all these owls based on real species? I recognize a couple of them. What do you guys thinks?",
            Submission.Location.UNKNOWN, 	"https://b.thumbs.redditmedia.com/2dY9xT2P1dyi61uZw4OKBwgVlsQZCX6kF11gQElZzdc.jpg",
            1666552177, 237, 15),
      };
    System.out.println(VaultApi.upsertSubmissionsQuery(submissions));
  }

}
