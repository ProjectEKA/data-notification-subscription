package in.projecteka.datanotificationsubscription.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import in.projecteka.datanotificationsubscription.common.AuthModeDeserializer;

@JsonDeserialize(using = AuthModeDeserializer.class)
public enum AuthMode {
    MOBILE_OTP,
    AADHAAR_OTP,
    INVALID_AUTHMODE,
    DEMOGRAPHICS,
    DIRECT;

    public static AuthMode fromText(String authMode) {
        if (authMode.equals("MOBILE_OTP")
                || authMode.equals("AADHAAR_OTP")
                || authMode.equals("DIRECT")
                || authMode.equals("DEMOGRAPHICS")) {
            return AuthMode.valueOf(authMode);
        } else {
            return AuthMode.INVALID_AUTHMODE;
        }
    }
}
