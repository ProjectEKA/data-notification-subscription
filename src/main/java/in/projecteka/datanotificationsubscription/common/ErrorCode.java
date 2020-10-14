package in.projecteka.datanotificationsubscription.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ErrorCode {
    BAD_REQUEST_FROM_GATEWAY(1510),
    DB_OPERATION_FAILED(1502),
    INVALID_TOKEN(1401),
    USER_NOT_FOUND(1414),
    SUBSCRIPTION_REQUEST_NOT_FOUND(1415),
    INVALID_DATE_RANGE(1418),
    INVALID_HITYPE(1419),
    SUBSCRIPTION_REQUEST_EXPIRED(1435),
    INVALID_REQUEST(1513),
    NETWORK_SERVICE_ERROR(1511),
    UNKNOWN_ERROR_OCCURRED(1500);/*please resume codes from the line above, we will put the codes in order
    later
    and in ranges*/


    private final int value;

    ErrorCode(int val) {
        value = val;
    }

    // Adding @JsonValue annotation that tells the 'value' to be of integer type while de-serializing.
    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static ErrorCode getNameByValue(int value) {
        return Arrays.stream(ErrorCode.values())
                .filter(errorCode -> errorCode.value == value)
                .findAny()
                .orElse(ErrorCode.UNKNOWN_ERROR_OCCURRED);
    }
}