package arnold.fucaptcha.omniparser;

import arnold.fucaptcha.omniparser.models.ElementClassificationResponse;
import arnold.fucaptcha.omniparser.models.ParseRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface OmniParserServices {
    String BASE_URL = "";

    @POST("parse/")
    Call<ElementClassificationResponse> parse(@Body ParseRequest imgContainer);

}
