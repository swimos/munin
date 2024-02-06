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

package swim.munin.connect.reddit;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.function.Function;
import swim.munin.connect.reddit.response.NominalRedditResponse;
import swim.recon.Recon;
import swim.structure.Form;
import swim.structure.Item;
import swim.structure.Kind;
import swim.structure.Tag;
import swim.structure.Value;

@Tag("comment")
public class Comment {

  private final String id;
  private final long createdUtc;
  private final String submissionId;
  private final String author;
  private final String body;
  private final String submissionAuthor;
  private final int numComments;

  public Comment(String id, long createdUtc, String submissionId,
                 String author, String body, String submissionAuthor,
                 int numComments) {
    this.id = id;
    this.createdUtc = createdUtc;
    this.submissionId = submissionId;
    this.author = author;
    this.body = body;
    this.submissionAuthor = submissionAuthor;
    this.numComments = numComments;
  }

  private Comment() {
    this("", -1L, "", "", "", "", -1);
  }

  public String id() {
    return this.id;
  }

  public long createdUtc() {
    return this.createdUtc;
  }

  public String submissionId() {
    return this.submissionId;
  }

  public String author() {
    return this.author;
  }

  public String body() {
    return this.body;
  }

  public String submissionAuthor() {
    return this.submissionAuthor;
  }

  public int numComments() {
    return this.numComments;
  }

  @Override
  public String toString() {
    return Recon.toString(form().mold(this));
  }

  @Kind
  private static Form<Comment> form;

  public static Form<Comment> form() {
    if (form == null) {
      form = Form.forClass(Comment.class);
    }
    return form;
  }

  /**
   * Extracts only the crucial aspects of a response from any {@code
   * RedditClient\.fetch.*UndocumentedComment.*} endpoint.
   *
   * @param hr  an HttpResponse to be transformed
   * @return  a RedditResponse with minimal {@code essence()}
   */
  public static RedditResponse<Comment[]> commentsFetchCrux(HttpResponse<InputStream> hr) {
    return new Comment.CommentsRedditResponse(hr);
  }

  /**
   * Extracts only the crucial aspects of a response from any {@code
   * RedditClient\.publish.*} endpoint.
   *
   * @param hr  an HttpResponse to be transformed
   * @return  a RedditResponse with minimal {@code essence()}
   */
  public static RedditResponse<Comment> commentPublishCrux(HttpResponse<InputStream> hr) {
    return new Comment.CommentRedditResponse(hr);
  }

  private static <V> V extractField(Value data, Value essence, String key,
                                    Function<Value, V> extractor) {
    try {
      return extractor.apply(data.get(key));
    } catch (Exception e) {
      throw new RuntimeException("Failed to extract " + key + ". Essence dump: "
          + essence, e);
    }
  }

  private static Comment fromPayloadChild(Item child, Value essence) {
    final Value data = child.get("data");
    final String id = extractField(data, essence, "id", Value::stringValue);
    final long createdUtc = extractField(data, essence, "created_utc", Value::longValue);
    final String submissionId = extractField(data, essence, "link_id", Value::stringValue).substring(3),
        author = extractField(data, essence, "author", Value::stringValue).toLowerCase(Locale.ROOT),
        body = extractField(data, essence, "body", Value::stringValue),
        submissionAuthor = extractField(data, essence, "link_author", v -> v.stringValue("").toLowerCase(Locale.ROOT));
    final int numComments = extractField(data, essence, "num_comments", v -> v.intValue(-1));
    return new Comment(id, createdUtc, submissionId, author, body, submissionAuthor, numComments);
  }

  private static class CommentsRedditResponse
      extends NominalRedditResponse<Comment[]> {

    @Override
    protected Comment[] cast(Value essence) {
      final Value children = essence.get("data").get("children");
      final Comment[] res = new Comment[children.length()];
      int i = 0;
      for (Item child : children) {
        res[i] = fromPayloadChild(child, essence);
        i++;
      }
      return res;
    }

    private CommentsRedditResponse(HttpResponse<InputStream> hr) {
      super(hr);
    }

  }

  private static class CommentRedditResponse
      extends NominalRedditResponse<Comment> {

    @Override
    protected Comment cast(Value essence) {
      return fromPayloadChild(essence.get("json").get("data").get("things")
          .getItem(0), essence);
    }

    private CommentRedditResponse(HttpResponse<InputStream> hr) {
      super(hr);
    }

  }

}
