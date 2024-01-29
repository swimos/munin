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

import java.net.URI;
import java.time.Duration;
import swim.munin.connect.http.HttpUtils;
import swim.munin.connect.http.StatusCodeException;
import swim.munin.connect.reddit.response.EmptyRedditResponse;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import swim.http.HttpStatus;
import swim.json.Json;
import swim.structure.Value;
import static java.net.http.HttpResponse.BodyHandler;
import static java.net.http.HttpResponse.BodyHandlers;

public class RedditClient {

  private final HttpClient executor;
  private final RedditPasswordGrantProvider grant;
  private final Api api;
  private final AtomicBoolean isRefreshing = new AtomicBoolean(false);

  private RedditClient(HttpClient executor, RedditPasswordGrantProvider grant, Api api)
      throws StatusCodeException {
    this.executor = executor;
    this.grant = grant;
    this.api = api;
    refreshToken(this.grant.currentExpiry());
  }

  public static RedditClient fromStream(String subreddit, HttpClient executor, InputStream stream)
      throws StatusCodeException {
    final RedditCredentials credentials = RedditCredentials.fromStream(stream);
    return new RedditClient(executor, new RedditPasswordGrantProvider(credentials), new Api(subreddit));
  }

  private void refreshToken(long expectedExpiry) throws StatusCodeException {
    if (expectedExpiry == this.grant.currentExpiry()) {
      if (isRefreshing.compareAndSet(false, true)) {
        try {
          System.out.println("[INFO] RedditClient fetching new token, concurrent client calls will be denied");
          this.grant.fetchNewToken(this.executor);
        } finally {
          this.isRefreshing.set(false);
          System.out.println("[INFO] RedditClient released concurrent usage denial restriction");
        }
      } else {
        throw new ConcurrentTokenRefreshException();
      }
    }
  }

  private <T> HttpResponse<T> makeAuthorizedRequest(HttpRequest request, BodyHandler<T> handler)
      throws StatusCodeException {
    final HttpResponse<T> response = HttpUtils.fireRequest(this.executor, request, handler, 3);
    if (response.statusCode() / 100 == 2) {
      return response;
    } else {
      throw new StatusCodeException(response.statusCode(), response.headers().toString());
    }
  }

  private <T> HttpResponse<T> makeApiCall(Supplier<HttpRequest> requestSupplier,
                            HttpResponse.BodyHandler<T> handler)
      throws StatusCodeException {
    final long expectedExpiry = this.grant.currentExpiry();
    if (this.grant.currentToken() == null || System.currentTimeMillis() < this.grant.currentExpiry()) {
      try {
        return makeAuthorizedRequest(requestSupplier.get(), handler);
      } catch (StatusCodeException e) {
        if (e.status().code() != HttpStatus.UNAUTHORIZED.code()
            && e.status().code() != HttpStatus.FORBIDDEN.code()) {
          throw e;
        } else {
          System.out.println("[WARN] Received unauthorized/forbidden response, will attempt token refresh");
        }
      }
    }
    refreshToken(expectedExpiry);
    return makeAuthorizedRequest(requestSupplier.get(), handler);
  }

  public Value fetchIdentityMe() throws StatusCodeException {
    return Json.parse(makeApiCall(() -> this.api.getIdentityMe(this.grant.currentToken(),
        this.grant.userAgent()), BodyHandlers.ofString()).body());
  }

  public RedditResponse<Submission[]> fetchOneUndocumentedPost() throws StatusCodeException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            this.api.getOneUndocumentedPost(this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Submission[]> fetchMaxUndocumentedPosts() throws StatusCodeException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            this.api.getMaxUndocumentedPosts(this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Submission[]> fetchUndocumentedPostsBefore(String fullname) throws StatusCodeException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            this.api.getUndocumentedPostsBefore(fullname, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Submission[]> fetchUndocumentedPostsAfter(String fullname) throws StatusCodeException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            this.api.getUndocumentedPostsAfter(fullname, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchOneUndocumentedComment() throws StatusCodeException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            this.api.getOneUndocumentedComment(this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchMaxUndocumentedComments() throws StatusCodeException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            this.api.getMaxUndocumentedComments(this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchUndocumentedCommentsBefore(String before) throws StatusCodeException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            this.api.getUndocumentedCommentsBefore(before, this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchUndocumentedCommentsAfter(String after) throws StatusCodeException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            this.api.getUndocumentedCommentsAfter(after, this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  // public RedditResponse<Comment[]> fetchReadCommentsArticle(String article) throws StatusCodeException {
  //   return Comment.commentsFetchCrux(makeApiCall(() ->
  //           this.api.getReadCommentsArticle(article, this.grant.currentToken(), this.grant.userAgent()),
  //       BodyHandlers.ofInputStream()));
  // }

  public RedditResponse<Submission[]> fetchReadById(String joinedIds) throws StatusCodeException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            this.api.getReadById(joinedIds, this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment> publishAnyComment(String parent, String body) throws StatusCodeException {
    return Comment.commentPublishCrux(makeApiCall(() ->
            this.api.postAnyComment(parent, body, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment> publishEditEditusertext(String id, String body) throws StatusCodeException {
    return Comment.commentPublishCrux(makeApiCall(() ->
            this.api.postEditEditusertext(id, body, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Void> removeEditDel(String fullname) throws StatusCodeException {
    return new EmptyRedditResponse(makeApiCall(() -> this.api.postEditDel(fullname, this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  @FunctionalInterface
  public interface Callable<V> {

    RedditResponse<V> call(RedditClient client) throws StatusCodeException;

  }

  private static class Api {

    // Subreddit-agnostic constants
    private static final String DOMAIN = "https://oauth.reddit.com";
    private static final URI GET_IDENTITY_ME = URI.create(DOMAIN + "/api/v1/me");
    private static final String BEFORE_FMT = "&before=%s";
    private static final String AFTER_FMT = "&after=%s";
    private static final String ONE_LIMIT = "?limit=1";
    private static final String MAX_LIMIT = "?limit=100";
    private static final String GET_READ_BY_ID_FMT = DOMAIN + "/by_id/%s" + MAX_LIMIT;
    private static final URI POST_ANY_COMMENT = URI.create(DOMAIN + "/api/comment");
    private static final URI POST_EDIT_EDITUSERTEXT_URI = URI.create(DOMAIN + "/api/editusertext");
    private static final URI POST_EDIT_DEL_URI = URI.create(DOMAIN + "/api/del");
    // private final String GET_READ_COMMENTS_ARTICLE_FMT = DOMAIN + this.subreddit + "/comments/%s?threaded=false&sort=old";

    private final String subreddit;

    private final URI getOneUndocumentedComment;
    private final URI getMaxUndocumentedComments;
    private final String getUndocumentedCommentsBeforeFmt;
    private final String getUndocumentedCommentsAfterFmt;

    private final URI getOneUndocumentedPost;
    private final URI getMaxUndocumentedPosts;
    private final String getUndocumentedPostsBeforeFmt;
    private final String getUndocumentedPostsAfterFmt;

    public Api(String subreddit) {
      this.subreddit = "/r/" + subreddit; // FIXME: sanitize
      // comments fetch
      final String getUndocumentedComments = this.subreddit + "/comments";
      this.getOneUndocumentedComment = URI.create(DOMAIN + getUndocumentedComments + ONE_LIMIT);
      this.getMaxUndocumentedComments = URI.create(DOMAIN + getUndocumentedComments + MAX_LIMIT);
      this.getUndocumentedCommentsBeforeFmt = DOMAIN + getUndocumentedComments + MAX_LIMIT + BEFORE_FMT;
      this.getUndocumentedCommentsAfterFmt = DOMAIN + getUndocumentedComments + MAX_LIMIT + AFTER_FMT;
      // submissions fetch
      final String getUndocumentedPosts = this.subreddit + "/new";
      this.getOneUndocumentedPost = URI.create(DOMAIN + getUndocumentedPosts + ONE_LIMIT);
      this.getMaxUndocumentedPosts = URI.create(DOMAIN + getUndocumentedPosts + MAX_LIMIT);
      this.getUndocumentedPostsBeforeFmt = DOMAIN + getUndocumentedPosts + MAX_LIMIT + BEFORE_FMT;
      this.getUndocumentedPostsAfterFmt = DOMAIN + getUndocumentedPosts + MAX_LIMIT + AFTER_FMT;
    }

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

    public HttpRequest getIdentityMe(String token, String userAgent) {
      return get(GET_IDENTITY_ME, token, userAgent);
    }

    // UNDOCUMENTED scope

    public HttpRequest getOneUndocumentedComment(String token, String userAgent) {
      return get(this.getOneUndocumentedComment, token, userAgent);
    }

    public HttpRequest getMaxUndocumentedComments(String token, String userAgent) {
      return get(this.getMaxUndocumentedComments, token, userAgent);
    }

    public HttpRequest getUndocumentedCommentsBefore(String before, String token, String userAgent) {
      return get(URI.create(String.format(this.getUndocumentedCommentsBeforeFmt, before)),
          token, userAgent);
    }

    public HttpRequest getUndocumentedCommentsAfter(String after, String token, String userAgent) {
      return get(URI.create(String.format(this.getUndocumentedCommentsAfterFmt, after)),
          token, userAgent);
    }

    public HttpRequest getOneUndocumentedPost(String token, String userAgent) {
      return get(this.getOneUndocumentedPost, token, userAgent);
    }

    public HttpRequest getMaxUndocumentedPosts(String token, String userAgent) {
      return get(this.getMaxUndocumentedPosts, token, userAgent);
    }

    public HttpRequest getUndocumentedPostsBefore(String before, String token, String userAgent) {
      return get(URI.create(String.format(this.getUndocumentedPostsBeforeFmt, before)),
          token, userAgent);
    }

    public HttpRequest getUndocumentedPostsAfter(String after, String token, String userAgent) {
      return get(URI.create(String.format(this.getUndocumentedPostsAfterFmt, after)),
          token, userAgent);
    }

    // READ scope

    // public HttpRequest getReadCommentsArticle(String article, String token, String userAgent) {
    //   return get(URI.create(String.format(GET_READ_COMMENTS_ARTICLE_FMT, article)), token, userAgent);
    // }

    public HttpRequest getReadById(String joinedIds, String token, String userAgent) {
      return get(URI.create(String.format(GET_READ_BY_ID_FMT, joinedIds)), token, userAgent);
    }

    // ANY scope

    public HttpRequest postAnyComment(String parent, String body, String token, String userAgent) {
      final String payload = String.format("api_type=json&thing_id=%s&text=%s",
          parent, body);
      return post(POST_ANY_COMMENT, HttpRequest.BodyPublishers.ofString(payload), token, userAgent);
    }

    // EDIT scope

    public HttpRequest postEditEditusertext(String id, String body, String token, String userAgent) {
      final String payload = String.format("api_type=json&thing_id=%s&text=%s",
          id, body);
      return post(POST_EDIT_EDITUSERTEXT_URI, HttpRequest.BodyPublishers.ofString(payload), token, userAgent);
    }

    public HttpRequest postEditDel(String fullname, String token, String userAgent) {
      final String payload = String.format("id=%s", fullname);
      return post(POST_EDIT_DEL_URI, HttpRequest.BodyPublishers.ofString(payload), token, userAgent);
    }

  }

}
