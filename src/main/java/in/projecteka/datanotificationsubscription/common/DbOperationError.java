package in.projecteka.datanotificationsubscription.common;

import lombok.Getter;
import lombok.ToString;
import org.springframework.http.HttpStatus;

import static in.projecteka.datanotificationsubscription.common.ErrorCode.DB_OPERATION_FAILED;

@Getter
@ToString
public class DbOperationError extends Throwable {
    private final HttpStatus httpStatus;
    private final ErrorRepresentation error;
    private final String errorMessage = "Failed to persist in database";

    public DbOperationError() {
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        this.error = new ErrorRepresentation(new Error(DB_OPERATION_FAILED, errorMessage));
    }

    public DbOperationError(String errorMessage) {
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        this.error = new ErrorRepresentation(new Error(DB_OPERATION_FAILED, errorMessage));
    }
}