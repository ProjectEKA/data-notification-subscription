package in.projecteka.datanotificationsubscription.subscription.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum  Requester {
    HIU("HIU"),
    HIP("HIP"),
    HEALTH_LOCKER("HEALTH_LOCKER");

    private final String resourceType;

    Requester(String value) {
        resourceType = value;
    }

    @JsonValue
    public String getValue() {
        return resourceType;
    }
}
