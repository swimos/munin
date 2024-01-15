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

import filethesebirds.munin.connect.reddit.RedditResponse;
import filethesebirds.munin.connect.reddit.response.NominalRedditResponse;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.function.Function;
import swim.recon.Recon;
import swim.structure.Attr;
import swim.structure.Form;
import swim.structure.Item;
import swim.structure.Kind;
import swim.structure.Record;
import swim.structure.Value;

public class Submission {

  private final String id;
  private final String title;
  private final String author;
  private final String flair;
  final String thumbnail;
  private final long createdUtc;
  private final int karma;
  private final int commentCount;

  public Submission(String id, String title, String author, String flair,
                    String thumbnail, long createdUtc, int karma, int commentCount) {
    this.id = id;
    this.title = title;
    this.author = author;
    this.flair = flair;
    this.thumbnail = thumbnail;
    this.createdUtc = createdUtc;
    this.karma = karma;
    this.commentCount = commentCount;
  }

  public String id() {
    return this.id;
  }

  public String title() {
    return this.title;
  }

  public String author() {
    return this.author;
  }

  public String flair() {
    return this.flair;
  }

  public String thumbnail() {
    return this.thumbnail;
  }

  public long createdUtc() {
    return this.createdUtc;
  }

  public int karma() {
    return this.karma;
  }

  public int commentCount() {
    return this.commentCount;
  }

  /**
   * Extracts only the crucial aspects of a response from any {@code
   * RedditClient.fetch.*UndocumentedPost.*} endpoint.
   *
   * @param hr  an HttpResponse to be transformed
   * @return  a RedditResponse with minimal {@code essence()}
   */
  public static RedditResponse<Submission[]> submissionsFetchCrux(HttpResponse<InputStream> hr) {
    return new SubmissionsRedditResponse(hr);
  }

  private static Form<Submission> form;

  @Kind
  public static Form<Submission> form() {
    if (Submission.form == null) {
      Submission.form = new SubmissionForm();
    }
    return Submission.form;
  }

  @Override
  public String toString() {
    return Recon.toString(form().mold(this));
  }

  private static class SubmissionsRedditResponse
      extends NominalRedditResponse<Submission[]> {

    private static <V> V extractField(Value data, Value essence, String key, Function<Value, V> extractor) {
      try {
        return extractor.apply(data.get(key));
      } catch (Exception e) {
        throw new RuntimeException("Failed to extract " + key + ". Essence dump: "
            + essence, e);
      }
    }

    @Override
    protected Submission[] cast(Value essence) {
      final Value children = essence.get("data").get("children");
      final Submission[] res = new Submission[children.length()];
      int i = 0;
      for (Item child : children) {
        final Value data = child.get("data");
        final String id = extractField(data, essence, "id", Value::stringValue);
        final String title = extractField(data, essence, "title", Value::stringValue);
        final String author = extractField(data, essence, "author", Value::stringValue)
            .toLowerCase(Locale.ROOT);
        final String flair = extractField(data, essence, "link_flair_text",
            v -> {
              final String lower = v.stringValue(null);
              return lower != null ? lower.toLowerCase(Locale.ROOT) : null;
            });
        final String thumbnail = extractField(data, essence, "thumbnail", Value::stringValue);
        final long createdUtc = extractField(data, essence, "created_utc", Value::longValue);
        final int karma = extractField(data, essence, "score", Value::intValue);
        final int commentCount = extractField(data, essence, "num_comments", Value::intValue);
        res[i] = new Submission(id, title, author, flair, thumbnail, createdUtc, karma, commentCount);
        i++;
      }
      return res;
    }

    private SubmissionsRedditResponse(HttpResponse<InputStream> hr) {
      super(hr);
    }

  }

  private static class SubmissionForm extends Form<Submission> {

    @Override
    public String tag() {
      return "submission";
    }

    @Override
    public Class<?> type() {
      return Submission.class;
    }

    @Override
    public Item mold(Submission s) {
      if (s == null) {
        return Value.extant();
      }
      return Record.create(9).attr(tag())
          .slot("id", s.id())
          .slot("title", s.title())
          .slot("author", s.author())
          .slot("flair", s.flair())
          .slot("thumbnail", s.thumbnail())
          .slot("createdUtc", s.createdUtc())
          .slot("karma", s.karma())
          .slot("commentCount", s.commentCount());
    }

    @Override
    public Submission cast(Item item) {
      try {
        final Attr attr = (Attr) item.head();
        if (!tag().equals(attr.getKey().stringValue())) {
          return null;
        }
        return new Submission(item.get("id").stringValue(),
            item.get("title").stringValue(),
            item.get("author").stringValue(),
            item.get("flair").stringValue(null),
            item.get("thumbnail").stringValue(),
            item.get("createdUtc").longValue(),
            item.get("karma").intValue(1),
            item.get("commentCount").intValue(0));
      } catch (Exception e) {
        return null;
      }
    }

  }

}
