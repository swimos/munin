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

package swim.munin.swim;

import java.util.List;
import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.ValueLane;
import swim.munin.Utils;
import swim.munin.connect.reddit.Comment;
import swim.munin.connect.reddit.Submission;
import swim.structure.Form;
import swim.structure.Num;
import swim.structure.Value;

/**
 * A dynamically instantiable Web Agent that serves as the intelligent, informed
 * digital twin of a Reddit submission.
 *
 * <p>By default, each instance of this {@code SubmissionAgent} modifies a
 * {@code LiveSubmissions} instance by shelving the submission upon encountering
 * a comment whose submission author is {@code [deleted]}. Override
 */
public abstract class AbstractSubmissionAgent extends AbstractAgent
    implements MuninAgent {

  @SwimLane("info")
  protected final ValueLane<Submission> info = valueLane()
      .valueForm(Submission.form())
      .didSet(this::infoDidSet);

  @SwimLane("status")
  protected final ValueLane<Value> status = this.<Value>valueLane()
      .didSet(this::statusDidSet);

  @SwimLane("addNewComment")
  protected final CommandLane<Comment> addNewComment = commandLane()
      .valueForm(Comment.form())
      .onCommand(this::addNewCommentOnCommand);

  @SwimLane("addManyComments")
  protected final CommandLane<List<Comment>> addManyComments = commandLane()
      .valueForm(Form.forList(Comment.form()))
      .onCommand(this::addManyCommentsOnCommand);

  /**
   * A command-type endpoint that triggers closing this {@code SubmissionAgent}
   * and clearing its lanes.
   */
  @SwimLane("expire")
  protected final CommandLane<Value> expire = this.<Value>commandLane()
      .onCommand(this::expireOnCommand);

  /**
   * A command-type endpoint that triggers closing this {@code SubmissionAgent}
   * clearing its lanes, and removing all traces of its underlying submission
   * from external systems (if they have been configured).
   */
  @SwimLane("shelve")
  protected final CommandLane<Value> shelve = this.<Value>commandLane()
      .onCommand(this::shelveOnCommand);

  protected void infoDidSet(Submission n, Submission o) {
    Logic.trace(this, "info", "Begin didSet(" + n + ", " + o + ")");
    Logic.trace(this, "info", "End didSet()");
  }

  protected void statusDidSet(Value n, Value o) {
    Logic.trace(this, "status", "Begin didSet(" + n + ", " + o + ")");
    Logic.trace(this, "status", "End didSet()");
  }

  protected void addNewCommentOnCommand(Comment c) {
    Logic.trace(this, "addNewComment", "Begin onCommand(" + c + ")");
    onNewComment("addNewComment", c);
    Logic.trace(this, "addNewComment", "End onCommand()");
  }

  protected void addManyCommentsOnCommand(List<Comment> comments) {
    Logic.trace(this, "addManyComments", "Begin onCommand(" + comments + ")");
    if (comments != null && !comments.isEmpty()) {
      for (int i = comments.size() - 1; i >= 0; i--) {
        if (!onNewComment("addManyComments", comments.get(i))) {
          return;
        }
      }
    }
    Logic.trace(this, "addManyComments", "End onCommand()");
  }

  /**
   * Processes the provided comment and returns whether this {@code
   * SubmissionAgent} should continue processing subsequent comments (usually
   * true).
   */
  protected boolean onNewComment(String caller, Comment c) {
    Logic.info(this, caller, "Received comment " + c);
    if (commentShelvesSubmission(c)) {
      Logic.info(this, caller, "Will shelve submission");
      if (liveSubmissions().shelve(this, caller, c.submissionId())) {
        command("/submissions", "shelveSubmission", Num.from(Utils.id36To10(c.submissionId())));
      }
      return false;
    }
    return true;
  }

  protected void expireOnCommand(Value v) {
    Logic.trace(this, "expire", "Begin onCommand(" + v + ")");
    if (v.isDistinct()) {
      notifyAndClose("expire");
    } else {
      Logic.warn(this, "expire", "Skipped expire due to non-distinct command payload");
    }
    Logic.trace(this, "expire", "End onCommand()");
  }

  protected void shelveOnCommand(Value v) {
    Logic.trace(this, "shelve", "Begin onCommand(" + v + ")");
    if (v.isDistinct()) {
      notifyPurgeAndClose("shelve");
    } else {
      Logic.warn(this, "shelve", "Skipped shelve due to non-distinct command payload");
    }
    Logic.trace(this, "shelve", "End onCommand()");
  }

  protected final void notifyAndClose(String caller) {
    try {
      final long id10 = Utils.id36To10(getProp("id").stringValue());
      Logic.debug(this, caller, "Notifying /submissions of submission " + caller);
      this.command("/submissions", caller + "Submission", Num.from(id10));
    } catch (Exception e) {
      Logic.warn(this, caller, "Failed to " + caller + ", agent will still close");
      didFail(e);
    } finally {
      clearLanes();
      Logic.trace(this, caller, "End onCommand()");
      close();
    }
  }

  protected final void notifyPurgeAndClose(String caller) {
    try {
      final long id10 = Utils.id36To10(getProp("id").stringValue());
      Logic.debug(this, caller, "Notifying /submissions of submission " + caller);
      this.command("/submissions", caller + "Submission", Num.from(id10));
      purge(caller, id10);
    } catch (Exception e) {
      Logic.warn(this, caller, "Failed to " + caller + ", agent will still close");
      didFail(e);
    } finally {
      clearLanes();
      Logic.trace(this, caller, "End onCommand()");
      close();
    }
  }

  protected void purge(String caller, long submissionId10) {
    // stub
  }

  protected boolean commentShelvesSubmission(Comment comment) {
    return "[deleted]".equals(comment.submissionAuthor());
  }

  protected abstract void clearLanes();

  @Override
  public void didStart() {
    Logic.info(this, "didStart()", "");
    try {
      final Num id10 =  Num.from(Utils.id36To10(getProp("id").stringValue(null)));
      command("/submissions", "subscribe", id10);
    } catch (Exception e) {
      didFail(e);
      close();
    }
  }

  @Override
  public void willClose() {
    Logic.info(this, "willClose()", "");
  }

}
