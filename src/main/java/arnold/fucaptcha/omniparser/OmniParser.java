package arnold.fucaptcha.omniparser;

import arnold.fucaptcha.omniparser.models.ElementClassificationResponse;
import arnold.fucaptcha.omniparser.models.ErrorModel;
import arnold.fucaptcha.omniparser.models.ParseRequest;
import retrofit2.Call;
import utils.FileUtilities;
import wasapi.WasapiClient;
import wasapi.WasapiUtilities;

public class OmniParser extends WasapiUtilities {

    OmniParserServices omniParserServer;

    public OmniParser(String baseUrl){
        omniParserServer = new WasapiClient.Builder()
                .baseUrl(baseUrl)
                .readTimeout(1200)
                .logRequestBody(false)
                .build(OmniParserServices.class);
    }

    public ElementClassificationResponse upload(String filePath){
        log.info("Uploading " + filePath + ".");
        ParseRequest container = new ParseRequest(FileUtilities.getEncodedString(filePath), 0.15, 650);
        Call<ElementClassificationResponse> messageCall = omniParserServer.parse(container);
        return perform(messageCall, true, false, ErrorModel.class);
    }
}
