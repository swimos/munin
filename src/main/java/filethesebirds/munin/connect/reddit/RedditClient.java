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

import filethesebirds.munin.connect.http.HttpUtils;
import filethesebirds.munin.connect.http.StatusCodeException;
import filethesebirds.munin.connect.reddit.response.EmptyRedditResponse;
import filethesebirds.munin.digest.Comment;
import filethesebirds.munin.digest.Submission;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Supplier;
import swim.http.HttpStatus;
import swim.json.Json;
import swim.structure.Value;
import static java.net.http.HttpResponse.BodyHandler;
import static java.net.http.HttpResponse.BodyHandlers;

public class RedditClient {

  private final HttpClient executor;
  private final RedditPasswordGrantProvider grant;

  private RedditClient(HttpClient executor, RedditPasswordGrantProvider grant)
      throws StatusCodeException {
    this.executor = executor;
    this.grant = grant;
    refreshToken(this.grant.currentExpiry());
  }

  public static RedditClient fromStream(HttpClient executor, InputStream stream)
      throws StatusCodeException {
    final RedditCredentials credentials = RedditCredentials.fromStream(stream);
    return new RedditClient(executor, new RedditPasswordGrantProvider(credentials));
  }

  // Issue a refresh token, or block-wait until issued.
  // Concurrent calls to this method will also block.
  // Plays well with "scheduleWithFixedDelay"-type recurring tasks
  private void refreshToken(long expectedExpiry) throws StatusCodeException {
    if (expectedExpiry == this.grant.currentExpiry()) {
      synchronized (this.grant) {
        System.out.println("[INFO] RedditClient entered token refresh synchronize block");
        if (expectedExpiry == this.grant.currentExpiry()) { // may have already been swapped
          this.grant.fetchNewToken(this.executor);
        }
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
        }
      }
    }
    refreshToken(expectedExpiry);
    return makeAuthorizedRequest(requestSupplier.get(), handler);
  }

  public Value fetchIdentityMe() throws StatusCodeException {
    return Json.parse(makeApiCall(() -> RedditApi.getIdentityMe(this.grant.currentToken(),
        this.grant.userAgent()), BodyHandlers.ofString()).body());
  }

  public RedditResponse<Submission[]> fetchOneUndocumentedPost() throws StatusCodeException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            RedditApi.getOneUndocumentedPost(this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Submission[]> fetchMaxUndocumentedPosts() throws StatusCodeException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            RedditApi.getMaxUndocumentedPosts(this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Submission[]> fetchUndocumentedPostsBefore(String fullname) throws StatusCodeException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            RedditApi.getUndocumentedPostsBefore(fullname, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Submission[]> fetchUndocumentedPostsAfter(String fullname) throws StatusCodeException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            RedditApi.getUndocumentedPostsAfter(fullname, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchOneUndocumentedComment() throws StatusCodeException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            RedditApi.getOneUndocumentedComment(this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchMaxUndocumentedComments() throws StatusCodeException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            RedditApi.getMaxUndocumentedComments(this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchUndocumentedCommentsBefore(String before) throws StatusCodeException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            RedditApi.getUndocumentedCommentsBefore(before, this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchUndocumentedCommentsAfter(String after) throws StatusCodeException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            RedditApi.getUndocumentedCommentsAfter(after, this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment[]> fetchReadCommentsArticle(String article) throws StatusCodeException {
    return Comment.commentsFetchCrux(makeApiCall(() ->
            RedditApi.getReadCommentsArticle(article, this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Submission[]> fetchReadById(String joinedIds) throws StatusCodeException {
    return Submission.submissionsFetchCrux(makeApiCall(() ->
            RedditApi.getReadById(joinedIds, this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment> publishAnyComment(String parent, String body) throws StatusCodeException {
    return Comment.commentPublishCrux(makeApiCall(() ->
            RedditApi.postAnyComment(parent, body, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Comment> publishEditEditusertext(String id, String body) throws StatusCodeException {
    return Comment.commentPublishCrux(makeApiCall(() ->
            RedditApi.postEditEditusertext(id, body, this.grant.currentToken(),
                this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  public RedditResponse<Void> removeEditDel(String fullname) throws StatusCodeException {
    return new EmptyRedditResponse(makeApiCall(() -> RedditApi.postEditDel(fullname, this.grant.currentToken(), this.grant.userAgent()),
        BodyHandlers.ofInputStream()));
  }

  @FunctionalInterface
  public interface Callable<V> {

    RedditResponse<V> call(RedditClient client) throws StatusCodeException;

  }

}
