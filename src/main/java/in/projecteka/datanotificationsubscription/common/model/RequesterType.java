package in.projecteka.datanotificationsubscription.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = RequesterTypeDeserializer.class)
public enum RequesterType {
    HIP,
    INVALID_REQUESTER_TYPE,
    HIU,
    HEALTH_LOCKER,
    HIP_AND_HIU;

    public static RequesterType fromText(String requesterType) {
        try {
            return RequesterType.valueOf(requesterType);
        } catch (IllegalArgumentException e) {
            return RequesterType.INVALID_REQUESTER_TYPE;
        }
    }
}
