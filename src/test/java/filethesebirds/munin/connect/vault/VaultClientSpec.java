package filethesebirds.munin.connect.vault;

import filethesebirds.munin.digest.Forms;
import filethesebirds.munin.digest.Submission;
import filethesebirds.munin.util.ConfigUtils;
import org.testng.annotations.Test;
import swim.recon.Recon;
import swim.structure.Value;

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

  @Test
  public void testDryObservationsAssign() {
    final String submissionId36 = "111o8dj";
    System.out.println(VaultApi.deleteObservationsQuery(submissionId36));
    final Value answer = Recon.parse("@answer{taxa:{saffin,yebcar,javspa,houspa,commyn},reviewers:{kiwikiu}}");
    System.out.println(VaultApi.insertObservationsQuery(submissionId36, Forms.forAnswer().cast(answer)));
  }

  @Test
  public void testPooled() {
    final VaultClient client = VaultClient.fromStream(ConfigUtils.openConfigFile(System.getProperty("vault.conf"), "/bombe-config.properties"));
    final Submission[] submissions = new Submission[]{
        new Submission("ybm8bf", "Flycatcher Friday(on a Sunday): Are these two different species? NY",
            Submission.Location.NORTH_AMERICA, 	"https://b.thumbs.redditmedia.com/PhNOPlhgGPeLEARuV-R5pV6d8b6OdCRTGbUh70F3SBo.jpg",
            1666542881L, 6, 5),
        new Submission("ybq5qc", "This is from one of my illustrated Harry Potter books. Are all these owls based on real species? I recognize a couple of them. What do you guys thinks?",
            Submission.Location.UNKNOWN, 	"https://b.thumbs.redditmedia.com/2dY9xT2P1dyi61uZw4OKBwgVlsQZCX6kF11gQElZzdc.jpg",
            1666552177, 237, 15),
        new Submission("12j7czi", "Minneapolis, MN - A fowl circus",
            Submission.Location.NORTH_AMERICA, "https://b.thumbs.redditmedia.com/pclI6AJ60h1t-nsVhd2Ly6M-dHvYWbS7ehbGscthiKw.jpg",
            1681267777, 8, 10)
    };
    client.upsertSubmissions(submissions);
    client.upsertSubmissions(submissions); // idempotent

    client.assignObservations("ybm8bf", Forms.forAnswer().cast(Recon.parse("@answer{taxa:{y00324},reviewers:{kiwikiu}}")));
    client.assignObservations("ybm8bf", Forms.forAnswer().cast(Recon.parse("@answer{taxa:{flycat1},reviewers:{kiwikiu,brohitbrose}}")));

    client.assignObservations("12j7czi", Forms.forAnswer().cast(Recon.parse("@answer{taxa:{redhea,lessca,rinduc,comgol,rebmer},reviewers:{brohitbrose}}")));
  }

}
