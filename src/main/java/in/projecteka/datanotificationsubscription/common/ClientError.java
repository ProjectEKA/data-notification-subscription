package in.projecteka.datanotificationsubscription.common;

import lombok.Getter;
import lombok.ToString;
import org.springframework.http.HttpStatus;

import static in.projecteka.datanotificationsubscription.common.ErrorCode.INVALID_TOKEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Getter
@ToString
public class ClientError extends Throwable {
    private final HttpStatus httpStatus;
    private final ErrorRepresentation error;

    public ClientError(HttpStatus httpStatus, ErrorRepresentation errorRepresentation) {
        this.httpStatus = httpStatus;
        error = errorRepresentation;
    }

    public static ClientError unAuthorized() {
        return new ClientError(UNAUTHORIZED,
                new ErrorRepresentation(new Error(INVALID_TOKEN, "Token verification failed")));
    }

    public ErrorCode getErrorCode() {
        return this.error.getError().getCode();
    }
}
