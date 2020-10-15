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
        if (requesterType.equalsIgnoreCase("HIP")) {
            return RequesterType.HIP;
        } else if (requesterType.equalsIgnoreCase("HIU")) {
            return RequesterType.HIU;
        } else if (requesterType.equalsIgnoreCase("HEALTH_LOCKER")) {
            return RequesterType.HEALTH_LOCKER;
        } else if (requesterType.equalsIgnoreCase("HIP_AND_HIU")) {
            return RequesterType.HIP_AND_HIU;
        } else {
            return RequesterType.INVALID_REQUESTER_TYPE;
        }
    }
}
