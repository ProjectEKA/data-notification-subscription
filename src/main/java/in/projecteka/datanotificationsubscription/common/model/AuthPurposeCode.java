package in.projecteka.datanotificationsubscription.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import in.projecteka.datanotificationsubscription.common.AuthPurposeDeserializer;

@JsonDeserialize(using = AuthPurposeDeserializer.class)
public enum AuthPurposeCode {
    LINK,
    KYC,
    KYC_AND_LINK,
    INVALID_PURPOSE;

    public static AuthPurposeCode fromText(String purpose) {
        if (purpose.equals("LINK")
                || purpose.equals("KYC")
                || purpose.equals("KYC_AND_LINK")) {
            return AuthPurposeCode.valueOf(purpose);
        } else {
            return AuthPurposeCode.INVALID_PURPOSE;
        }
    }

}
