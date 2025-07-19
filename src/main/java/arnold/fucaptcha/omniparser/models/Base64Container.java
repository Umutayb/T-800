package arnold.fucaptcha.omniparser.models;

import lombok.Data;

@Data
public class Base64Container {
    String base64_image;

    public Base64Container(String base64_image) {
        this.base64_image = base64_image;
    }
}
