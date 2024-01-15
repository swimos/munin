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

package filethesebirds.munin.connect.reddit;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

final class RedditApi {

  private RedditApi() {
  }

  private static final String DOMAIN = "https://oauth.reddit.com";
  private static final String SUBREDDIT = "/r/whatsthisbird";

  private static final String BEFORE_FMT = "&before=%s";
  private static final String AFTER_FMT = "&after=%s";
  private static final String ONE_LIMIT = "?limit=1";
  private static final String MAX_LIMIT = "?limit=100";

  private static final String GET_UNDOCUMENTED_COMMENTS = SUBREDDIT + "/comments";
  private static final String GET_ONE_UNDOCUMENTED_COMMENT = DOMAIN
      + GET_UNDOCUMENTED_COMMENTS + ONE_LIMIT;
  private static final String GET_MAX_UNDOCUMENTED_COMMENTS = DOMAIN
      + GET_UNDOCUMENTED_COMMENTS + MAX_LIMIT;
  private static final URI GET_ONE_UNDOCUMENTED_COMMENT_URI = URI.create(GET_ONE_UNDOCUMENTED_COMMENT);
  private static final URI GET_MAX_UNDOCUMENTED_COMMENTS_URI = URI.create(GET_MAX_UNDOCUMENTED_COMMENTS);
  private static final String GET_UNDOCUMENTED_COMMENTS_BEFORE_FMT = DOMAIN
      + GET_UNDOCUMENTED_COMMENTS + MAX_LIMIT + BEFORE_FMT;
  private static final String GET_UNDOCUMENTED_COMMENTS_AFTER_FMT = DOMAIN
      + GET_UNDOCUMENTED_COMMENTS + MAX_LIMIT + AFTER_FMT;

  private static final String GET_UNDOCUMENTED_POSTS = SUBREDDIT + "/new";
  private static final String GET_ONE_UNDOCUMENTED_POST = DOMAIN
      + GET_UNDOCUMENTED_POSTS + ONE_LIMIT;
  private static final String GET_MAX_UNDOCUMENTED_POSTS = DOMAIN
      + GET_UNDOCUMENTED_POSTS + MAX_LIMIT;
  private static final URI GET_ONE_UNDOCUMENTED_POST_URI = URI.create(GET_ONE_UNDOCUMENTED_POST);
  private static final URI GET_MAX_UNDOCUMENTED_POSTS_URI = URI.create(GET_MAX_UNDOCUMENTED_POSTS);
  private static final String GET_UNDOCUMENTED_POSTS_BEFORE_FMT = DOMAIN
      + GET_UNDOCUMENTED_POSTS + MAX_LIMIT + BEFORE_FMT;
  private static final String GET_UNDOCUMENTED_POSTS_AFTER_FMT = DOMAIN
      + GET_UNDOCUMENTED_POSTS + MAX_LIMIT + AFTER_FMT;

  private static final URI GET_IDENTITY_ME_URI = URI.create(DOMAIN + "/api/v1/me");

  private static final String GET_READ_COMMENTS_ARTICLE_FMT = DOMAIN + SUBREDDIT + "/comments/%s?threaded=false&sort=old";
  private static final String GET_READ_BY_ID_FMT = DOMAIN + "/by_id/%s" + MAX_LIMIT;

  private static final URI POST_ANY_COMMENT_URI = URI.create(DOMAIN + "/api/comment");

  private static final URI POST_EDIT_EDITUSERTEXT_URI = URI.create(DOMAIN + "/api/editusertext");
  private static final URI POST_EDIT_DEL_URI = URI.create(DOMAIN + "/api/del");

  private static HttpRequest.Builder baseRequestBuilder(URI uri, String token, String userAgent) {
    return HttpRequest.newBuilder(uri)
        .header("Authorization", "bearer " + token)
        .header("User-Agent", userAgent)
        .header("Accept-Encoding", "gzip");
  }

  private static HttpRequest get(URI uri, String token, String userAgent) {
    return baseRequestBuilder(uri, token, userAgent)
        .timeout(Duration.ofMillis(7000L))
        .GET().build();
  }

  private static HttpRequest post(URI uri, HttpRequest.BodyPublisher publisher,
                                  String token, String userAgent) {
    return baseRequestBuilder(uri, token, userAgent)
        .timeout(Duration.ofMillis(10500L))
        .POST(publisher).build();
  }

  // ACCOUNT scope

  public static HttpRequest getIdentityMe(String token, String userAgent) {
    return get(GET_IDENTITY_ME_URI, token, userAgent);
  }

  // UNDOCUMENTED scope

  public static HttpRequest getOneUndocumentedComment(String token, String userAgent) {
    return get(GET_ONE_UNDOCUMENTED_COMMENT_URI, token, userAgent);
  }

  public static HttpRequest getMaxUndocumentedComments(String token, String userAgent) {
    return get(GET_MAX_UNDOCUMENTED_COMMENTS_URI, token, userAgent);
  }

  public static HttpRequest getUndocumentedCommentsBefore(String before, String token, String userAgent) {
    return get(URI.create(String.format(GET_UNDOCUMENTED_COMMENTS_BEFORE_FMT, before)),
        token, userAgent);
  }

  public static HttpRequest getUndocumentedCommentsAfter(String after, String token, String userAgent) {
    return get(URI.create(String.format(GET_UNDOCUMENTED_COMMENTS_AFTER_FMT, after)),
        token, userAgent);
  }

  public static HttpRequest getOneUndocumentedPost(String token, String userAgent) {
    return get(GET_ONE_UNDOCUMENTED_POST_URI, token, userAgent);
  }

  public static HttpRequest getMaxUndocumentedPosts(String token, String userAgent) {
    return get(GET_MAX_UNDOCUMENTED_POSTS_URI, token, userAgent);
  }

  public static HttpRequest getUndocumentedPostsBefore(String before, String token, String userAgent) {
    return get(URI.create(String.format(GET_UNDOCUMENTED_POSTS_BEFORE_FMT, before)),
        token, userAgent);
  }

  public static HttpRequest getUndocumentedPostsAfter(String after, String token, String userAgent) {
    return get(URI.create(String.format(GET_UNDOCUMENTED_POSTS_AFTER_FMT, after)),
        token, userAgent);
  }

  // READ scope

  public static HttpRequest getReadCommentsArticle(String article, String token, String userAgent) {
    return get(URI.create(String.format(GET_READ_COMMENTS_ARTICLE_FMT, article)), token, userAgent);
  }

  public static HttpRequest getReadById(String joinedIds, String token, String userAgent) {
    return get(URI.create(String.format(GET_READ_BY_ID_FMT, joinedIds)), token, userAgent);
  }

  // ANY scope

  public static HttpRequest postAnyComment(String parent, String body, String token, String userAgent) {
    final String payload = String.format("api_type=json&thing_id=%s&text=%s",
        parent, body);
    return post(POST_ANY_COMMENT_URI, HttpRequest.BodyPublishers.ofString(payload), token, userAgent);
  }

  // EDIT scope

  public static HttpRequest postEditEditusertext(String id, String body, String token, String userAgent) {
    final String payload = String.format("api_type=json&thing_id=%s&text=%s",
        id, body);
    return post(POST_EDIT_EDITUSERTEXT_URI, HttpRequest.BodyPublishers.ofString(payload), token, userAgent);
  }

  public static HttpRequest postEditDel(String fullname, String token, String userAgent) {
    final String payload = String.format("id=%s", fullname);
    return post(POST_EDIT_DEL_URI, HttpRequest.BodyPublishers.ofString(payload), token, userAgent);
  }

}
