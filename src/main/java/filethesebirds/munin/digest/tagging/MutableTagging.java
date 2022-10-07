package filethesebirds.munin.digest.tagging;

import filethesebirds.munin.digest.Tagging;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

class MutableTagging implements Tagging {

  private final Set<String> reviewers;
  private final Set<Tag> tags;
  private final Set<String> readOnlyReviewers;
  private final Set<Tag> readOnlyTags;

  MutableTagging() {
    this.reviewers = new HashSet<>();
    this.tags = EnumSet.noneOf(Tag.class);
    this.readOnlyReviewers = Collections.unmodifiableSet(this.reviewers);
    this.readOnlyTags = Collections.unmodifiableSet(this.tags);
  }

  @Override
  public Set<String> reviewers() {
    return this.readOnlyReviewers;
  }

  @Override
  public Set<Tag> tags() {
    return this.readOnlyTags;
  }

  @Override
  public Tagging overrideTags(String reviewer, Set<Tag> overrideTags) {
    if (reviewers().contains(reviewer)) {
      return this;
    }
    this.tags.clear();
    if (this.tags.addAll(overrideTags)) {
      this.reviewers.add(reviewer);
    }
    return this;
  }

}
