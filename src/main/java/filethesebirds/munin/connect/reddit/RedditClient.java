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

package filethesebirds.munin.connect.reddit;

import filethesebirds.munin.connect.http.HttpUtils;
import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.Submission;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Supplier;
import swim.json.Json;
import swim.structure.Value;
import static java.net.http.HttpResponse.BodyHandler;
import static java.net.http.HttpResponse.BodyHandlers;

public class RedditClient {

  private final HttpClient executor;
  private final RedditPasswordGrantProvider grant;

  private RedditClient(HttpClient executor, RedditPasswordGrantProvider grant)
      throws RedditApiException {
    this.executor = executor;
    this.grant = grant;
    refreshToken(this.grant.currentExpiry());
  }

  public static RedditClient fromStream(HttpClient executor, InputStream stream)
      throws RedditApiException {
    final RedditCredentials credentials = RedditCredentials.fromStream(stream);
    return new RedditClient(executor, new RedditPasswordGrantProvider(credentials));
  }

  private static <V> boolean responseIsSuccessful(HttpResponse<V> response) {
    return response.statusCode() / 100 == 2;
  }

  private static <V> boolean responseIsUnauthorized(HttpResponse<V> response) {
    return response.statusCode() == 403;
  }

  private void refreshToken(long oldExpiry) throws RedditApiException {
    if (oldExpiry == this.grant.currentExpiry()) {
      synchronized (this.grant) {
        if (oldExpiry == this.grant.currentExpiry()) {
          this.grant.fetchNewToken(this.executor);
        }
      }
    }
  }

  private <T> HttpResponse<T> makeAuthorizedRequest(HttpRequest request, BodyHandler<T> handler)
      throws RedditApiException {
    final HttpResponse<T> response = HttpUtils.fireRequest(this.executor,
        request, handler, 5);
    if (responseIsSuccessful(response)) {
      return response;
    }
    if (!responseIsUnauthorized(response)) {
      throw new RedditApiException("Problematic API response with code " + response.statusCode()
          + ". Headers: " + response.headers());
    }
    return null;
  }

  private <T> HttpResponse<T> makeApiCall(Supplier<HttpRequest> requestSupplier,
                            HttpResponse.BodyHandler<T> handler)
      throws RedditApiException {
    final long now = System.currentTimeMillis();
    final long oldExpiry = this.grant.currentExpiry();
    if (this.grant.currentToken() == null || now < this.grant.currentExpiry()) {
      final HttpResponse<T> res = makeAuthorizedRequest(requestSupplier.get(), handler);
      if (res != null) {
        return res;
      }
    }
    // Expired token, refresh it in a thread-safe manner and try again
    refreshToken(oldExpiry);
    return makeAuthorizedRequest(requestSupplier.get(), handler);
  }

  public Value fetchIdentityMe() throws RedditApiException {
    return Json.parse(makeApiCall(() -> RedditApi.getIdentityMe(this.grant.currentToken(),
        this.grant.userAgent()), BodyHandlers.ofString()).body());
  }

  public RedditResponse<Submission[]> fetchOneUndocumentedPost() throws RedditApiException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            RedditApi.getOneUndocumentedPost(this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Submission[]> fetchMaxUndocumentedPosts() throws RedditApiException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            RedditApi.getMaxUndocumentedPosts(this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Submission[]> fetchUndocumentedPostsBefore(String before)
      throws RedditApiException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            RedditApi.getUndocumentedPostsBefore(before, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Submission[]> fetchUndocumentedPostsAfter(String after)
      throws RedditApiException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            RedditApi.getUndocumentedPostsAfter(after, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchOneUndocumentedComment() throws RedditApiException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            RedditApi.getOneUndocumentedComment(this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchMaxUndocumentedComments() throws RedditApiException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            RedditApi.getMaxUndocumentedComments(this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchUndocumentedCommentsBefore(String before)
      throws RedditApiException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            RedditApi.getUndocumentedCommentsBefore(before, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchUndocumentedCommentsAfter(String after)
      throws RedditApiException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            RedditApi.getUndocumentedCommentsAfter(after, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchReadCommentsArticle(String article)
      throws RedditApiException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            RedditApi.getReadCommentsArticle(article, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment> publishAnyComment(String parent, String body) throws RedditApiException {
    return Comment.commentPublishCrux(makeApiCall(() ->
            RedditApi.postAnyComment(parent, body, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment> publishEditEditusertext(String id, String body) throws RedditApiException {
    return Comment.commentPublishCrux(makeApiCall(() ->
            RedditApi.postEditEditusertext(id, body, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

}
