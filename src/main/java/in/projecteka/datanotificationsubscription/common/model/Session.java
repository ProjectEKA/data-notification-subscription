package in.projecteka.datanotificationsubscription.common.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Value;

@Value
public class Session {
    @JsonAlias({"access_token"})
    String accessToken;
}
