package in.projecteka.datanotificationsubscription.common.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TokenValidationRequest {
    String authToken;
}
