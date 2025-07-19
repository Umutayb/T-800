package arnold.fucaptcha.omniparser.models;

import lombok.Data;

@Data
public class Action {
    String targetElementContent;
    String targetElementId;
    ContentType contentType;

    public Action contentMatch(ElementClassificationResponse.Element element){
        this.targetElementContent = element.getContent();
        return this;
    }

    public enum ContentType {
        text_field,
        button,
        input_field;
    }
}
