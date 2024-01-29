package swim.munin.swim;

import java.util.List;
import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.ValueLane;
import swim.munin.connect.reddit.Comment;
import swim.munin.connect.reddit.Submission;
import swim.structure.Form;

/**
 * A dynamically instantiable Web Agent that serves as the intelligent, informed
 * digital twin of an r/WhatsThisBird submission.
 *
 * <p>Each {@code SubmissionAgent} is liable for the following {@link
 * LiveSubmissions} action:
 * <ul>
 * <li>Shelving the submission upon encountering a properly issued {@code !rm}
 * comment or a comment whose submission author is {@code [deleted]}
 * </ul>
 * and the following vault actions:
 * <ul>
 * <li>Assigning observations based on updates to {@link #answer}
 * <li>Deleting the corresponded submission (cascaded to its observations) upon
 * encountering an aforementioned shelve-capable comment
 * </ul>
 */
public abstract class AbstractSubmissionAgent extends AbstractAgent {

  @SwimLane("info")
  protected final ValueLane<Submission> info = valueLane()
      .valueForm(Submission.form())
      .didSet(this::infoDidSet);

  @SwimLane("addNewComment")
  protected final CommandLane<Comment> addNewComment = commandLane()
      .valueForm(Comment.form())
      .onCommand(this::addNewCommentOnCommand);

  @SwimLane("addManyComments")
  CommandLane<List<Comment>> addManyComments = commandLane()
      .valueForm(Form.forList(Comment.form()))
      .onCommand(comments -> {
        if (comments != null && !comments.isEmpty()) {
          for (int i = comments.size() - 1; i >= 0; i--) {
            onNewComment(comments.get(i), "addManyComments");
          }
        }
      });

  protected void infoDidSet(Submission n, Submission o) {
    // stub
  }

  protected void addNewCommentOnCommand(Comment c) {
    // stub
  }

}
