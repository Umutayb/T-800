package arnold.fucaptcha.omniparser.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import utils.arrays.lambda.Collectors;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ElementClassificationResponse {
    String som_image_base64;
    List<Element> parsed_content_list;
    double latency;

    public ElementClassificationResponse(List<Element> parsed_content_list) {
        this.parsed_content_list = parsed_content_list;
    }

    public List<BaseElement> getReducedContentList(){
        return parsed_content_list.stream()
                .map(Element::reduced).toList();
    }

    public ElementClassificationResponse idEnrichment(){
        this.parsed_content_list = parsed_content_list.stream().map(Element::idEnrichment).toList();
        return this;
    }

    public Element getElementByID(String id){
        return this.parsed_content_list.stream()
                .filter(element -> element.getTargetElementId().equals(id))
                .collect(Collectors.toSingleton());
    }

    public String reducedClassificationString(){
        StringBuilder elementListString = new StringBuilder();
        List<BaseElement> contentList = this.getReducedContentList();
        contentList.forEach(
                element -> elementListString
                        .append(contentList.indexOf(element) == 0 ? "[" : ", ")
                        .append("{elementContent : ")
                        .append(element.getContent())
                        .append(", targetElementId : ")
                        .append(element.getTargetElementId())
                        .append(", elementType : ")
                        .append(element.getType())
                        .append("}")
                        .append(contentList.indexOf(element) == contentList.size() - 1 ? "]" : "")
        );

        return elementListString.toString();
    }

    @Data
    public static class BaseElement {
        String content;
        String targetElementId;
        boolean interactivity;
        String type;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class Element extends BaseElement {
        List<Double> bbox;
        String source;

        public Element idEnrichment(){
            this.targetElementId = UUID.randomUUID().toString().substring(0, 8);
            return this;
        }

        public BaseElement reduced(){
            BaseElement reducedElement = new BaseElement();
            reducedElement.setType(this.type);
            reducedElement.setContent(this.content);
            reducedElement.setTargetElementId(this.targetElementId);
            reducedElement.setInteractivity(true); //Gotta do this
            return reducedElement;
        }
    }
}
