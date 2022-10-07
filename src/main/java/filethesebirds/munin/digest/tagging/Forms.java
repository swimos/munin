package filethesebirds.munin.digest.tagging;

import filethesebirds.munin.digest.Tagging;
import java.util.Set;
import swim.structure.Form;
import swim.structure.Item;
import swim.structure.Record;
import swim.structure.Text;
import swim.structure.Value;

public class Forms {

  private static final Form<Set<String>> SET_STRING_FORM = Form.forSet(Form.forString());

  private static final Form<Tagging.Tag> TAGGING_TAG_FORM = new Form<>() {

    @Override
    public String tag() {
      return "taggingTag";
    }

    @Override
    public Class<?> type() {
      return Tagging.Tag.class;
    }

    @Override
    public Item mold(Tagging.Tag object) {
      if (object == null) {
        return Value.extant();
      }
      return Text.from(object.text());
    }

    @Override
    public Tagging.Tag cast(Item item) {
      if (item == null || !item.isDistinct()) {
        return null;
      }
      return Tagging.Tag.fromText(item.stringValue());
    }

  };

  private static final Form<Tagging> TAGGING_FORM = new Form<>() {

    @Override
    public String tag() {
      return "tagging";
    }

    @Override
    public Class<?> type() {
      return Tagging.class;
    }

    @Override
    public Item mold(Tagging object) {
      if (object == null || object.isEmpty()) {
        return Value.extant();
      }
      Record result = Record.create(3).attr(tag());
      if (!object.reviewers().isEmpty()) {
        result = result.slot("reviewers", forSetString().mold(object.reviewers()).toValue());
      }
      if (!object.tags().isEmpty()) {
        result = result.slot("tags", forSetTaggingTag().mold(object.tags()).toValue());
      }
      return result;
    }

    @Override
    public Tagging cast(Item item) {
      if (item == null || !item.isDistinct()) {
        // return Taggin
        return null;
      }
      // return Taggitem.stringValue().
      return null;
    }

  };

  public static Form<Set<String>> forSetString() {
    return SET_STRING_FORM;
  }

  public static Form<Tagging.Tag> forTaggingTag() {
    return TAGGING_TAG_FORM;
  }

  public static Form<Set<Tagging.Tag>> forSetTaggingTag() {
    return Form.forSet(forTaggingTag());
  }

  public static Form<Tagging> forTagging() {
    return TAGGING_FORM;
  }

}
