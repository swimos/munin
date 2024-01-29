package swim.munin.filethesebirds.connect.vault;

import swim.munin.filethesebirds.digest.Forms;
import swim.munin.connect.reddit.Submission;
import swim.munin.Utils;
import java.util.List;
import org.testng.annotations.Test;
import swim.recon.Recon;
import swim.structure.Value;

public class VaultClientSpec {

  @Test
  public void testDrySubmissionsUpsert() {
    final List<Submission> submissions = List.of(
        new Submission("ybm8bf", "Flycatcher Friday(on a Sunday): Are these two different species? NY", "author",
            "North America", 	"https://b.thumbs.redditmedia.com/PhNOPlhgGPeLEARuV-R5pV6d8b6OdCRTGbUh70F3SBo.jpg",
            1666542881L, 6, 5),
        new Submission("ybq5qc", "This is from one of my illustrated Harry Potter books. Are all these owls based on real species? I recognize a couple of them. What do you guys thinks?",
            "author",null, 	"https://b.thumbs.redditmedia.com/2dY9xT2P1dyi61uZw4OKBwgVlsQZCX6kF11gQElZzdc.jpg",
            1666552177, 237, 15));
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
    final VaultClient client = VaultClient.fromStream(Utils.openConfigFile(System.getProperty("vault.conf"), "/bombe-config.properties"));
    final List<Submission> submissions = List.of(
        new Submission("ybm8bf", "Flycatcher Friday(on a Sunday): Are these two different species? NY", "author",
            "North America", 	"https://b.thumbs.redditmedia.com/PhNOPlhgGPeLEARuV-R5pV6d8b6OdCRTGbUh70F3SBo.jpg",
            1666542881L, 6, 5),
        new Submission("ybq5qc", "This is from one of my illustrated Harry Potter books. Are all these owls based on real species? I recognize a couple of them. What do you guys thinks?",
            "author",null, 	"https://b.thumbs.redditmedia.com/2dY9xT2P1dyi61uZw4OKBwgVlsQZCX6kF11gQElZzdc.jpg",
            1666552177, 237, 15));
    client.upsertSubmissions(submissions);
    client.upsertSubmissions(submissions); // idempotent

    client.assignObservations("ybm8bf", Forms.forAnswer().cast(Recon.parse("@answer{taxa:{y00324},reviewers:{kiwikiu}}")));
    client.assignObservations("ybm8bf", Forms.forAnswer().cast(Recon.parse("@answer{taxa:{flycat1},reviewers:{kiwikiu,brohitbrose}}")));

    client.assignObservations("12j7czi", Forms.forAnswer().cast(Recon.parse("@answer{taxa:{redhea,lessca,rinduc,comgol,rebmer},reviewers:{brohitbrose}}")));
    final List<Submission> lateSubmission = List.of(
        new Submission("12j7czi", "Minneapolis, MN - A fowl circus", "author",
            "North America", "https://b.thumbs.redditmedia.com/pclI6AJ60h1t-nsVhd2Ly6M-dHvYWbS7ehbGscthiKw.jpg",
            1681267777, 8, 10));
    client.upsertSubmissions(lateSubmission);
  }

//  @Test
//  public void gross() {
//    try (InputStream is = ConfigUtils.openConfigFile("/gross.txt", "/gross.txt");
//        InputStreamReader isr = new InputStreamReader(is);
//        BufferedReader br = new BufferedReader(isr)) {
//      br.lines().forEach(l -> {
//        final int anchor = l.indexOf(",");
//        if (anchor < 0) return;
//        final String id36 = l.substring(0, anchor);
//        final Answer ans = Forms.forAnswer().cast(Recon.parse(l.substring(anchor + 1)));
//        System.out.println(VaultApi.deleteObservationsQuery(id36));
//        System.out.println(VaultApi.insertObservationsQuery(id36, ans));
//      });
//    } catch (Exception e) {
//    }
//  }

}
