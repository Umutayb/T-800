package arnold.fucaptcha.omniparser.models;

public class ParseRequest extends Base64Container {
    double box_threshold;
    int box_overlay_divident;

    public ParseRequest(String base64_image, double box_threshold, int box_overlay_divident) {
        super(base64_image);
        this.box_overlay_divident = box_overlay_divident;
        this.box_threshold = box_threshold;
    }
}
