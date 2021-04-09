package in.projecteka.datanotificationsubscription.clients.model;

import in.projecteka.datanotificationsubscription.common.model.AuthMode;
import in.projecteka.datanotificationsubscription.common.model.AuthPurposeCode;
import in.projecteka.datanotificationsubscription.common.model.RequesterType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import javax.validation.Valid;
import java.time.LocalDateTime;

@AllArgsConstructor
@Builder
@Data
public class AuthRequestRepresentation {
    private String requestId;
    private String patientId;
    private String status;
    private Purpose purpose;
    private AuthMode authMode;
    private Requester requester;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;

    @Builder
    @AllArgsConstructor
    @Data
    public static class Requester {
        RequesterType type;
        @NonNull
        @Valid
        String id;
        String name;
    }

    @Builder
    @AllArgsConstructor
    @Data
    public static class Purpose {
        AuthPurposeCode code;
        String text;
    }
}

