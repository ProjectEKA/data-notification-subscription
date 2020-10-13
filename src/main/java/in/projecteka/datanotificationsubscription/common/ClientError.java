package in.projecteka.datanotificationsubscription.common;

import lombok.Getter;
import lombok.ToString;
import org.springframework.http.HttpStatus;

import static in.projecteka.datanotificationsubscription.common.ErrorCode.SUBSCRIPTION_REQUEST_EXPIRED;
import static in.projecteka.datanotificationsubscription.common.ErrorCode.SUBSCRIPTION_REQUEST_NOT_FOUND;
import static in.projecteka.datanotificationsubscription.common.ErrorCode.INVALID_DATE_RANGE;
import static in.projecteka.datanotificationsubscription.common.ErrorCode.INVALID_REQUEST;
import static in.projecteka.datanotificationsubscription.common.ErrorCode.INVALID_TOKEN;
import static in.projecteka.datanotificationsubscription.common.ErrorCode.NETWORK_SERVICE_ERROR;
import static in.projecteka.datanotificationsubscription.common.ErrorCode.UNKNOWN_ERROR_OCCURRED;
import static in.projecteka.datanotificationsubscription.common.ErrorCode.USER_NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Getter
@ToString
public class ClientError extends Throwable {
    private final HttpStatus httpStatus;
    private final ErrorRepresentation error;

    private static final String CANNOT_PROCESS_REQUEST_TRY_LATER = "Cannot process the request at the moment," +
            "please try later.";

    public ClientError(HttpStatus httpStatus, ErrorRepresentation errorRepresentation) {
        this.httpStatus = httpStatus;
        error = errorRepresentation;
    }

    public static ClientError unAuthorized() {
        return new ClientError(UNAUTHORIZED,
                new ErrorRepresentation(new Error(INVALID_TOKEN, "Token verification failed")));
    }

    public static ClientError tooManyRequests() {
        return new ClientError(TOO_MANY_REQUESTS, new ErrorRepresentation(
                new Error(INVALID_REQUEST, "Too many requests from gateway")));
    }

    public static ClientError unknownErrorOccurred() {
        return internalServerError("Unknown error occurred");
    }

    private static ClientError internalServerError(String message) {
        return new ClientError(INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(UNKNOWN_ERROR_OCCURRED, message)));
    }

    public static ClientError userNotFound() {
        return new ClientError(NOT_FOUND,
                new ErrorRepresentation(new Error(USER_NOT_FOUND, "Cannot find the user")));
    }

    public static ClientError invalidDateRange() {
        return new ClientError(BAD_REQUEST,
                new ErrorRepresentation(new Error(INVALID_DATE_RANGE, "Date Range given is invalid")));
    }

    public static ClientError subscriptionRequestNotFound() {
        return new ClientError(NOT_FOUND,
                new ErrorRepresentation(new Error(SUBSCRIPTION_REQUEST_NOT_FOUND, "Cannot find the subscription request")));
    }

    public static ClientError subscriptionRequestExpired() {
        return new ClientError(HttpStatus.GONE,
                new ErrorRepresentation(new Error(SUBSCRIPTION_REQUEST_EXPIRED, "subscription Request expired")));
    }

    public static ClientError networkServiceCallFailed() {
        return new ClientError(INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(NETWORK_SERVICE_ERROR, CANNOT_PROCESS_REQUEST_TRY_LATER)));
    }

    public static ClientError unknownUnauthorizedError(String message) {
        return new ClientError(UNAUTHORIZED, new ErrorRepresentation(new Error(UNKNOWN_ERROR_OCCURRED, message)));
    }

    public ErrorCode getErrorCode() {
        return this.error.getError().getCode();
    }
}
