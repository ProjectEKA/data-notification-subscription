package in.projecteka.datanotificationsubscription.subscription.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@AllArgsConstructor
@Builder
public class HIUSubscriptionNotifyResponse {
    UUID requestId;
    LocalDateTime timestamp;
    Acknowledgement acknowledgement;
    Error error;
    GatewayResponse resp;

    @Value
    @AllArgsConstructor
    @Builder
    public static class Acknowledgement {
        AcknowledgementStatus status;
        UUID eventId;
    }

    public enum AcknowledgementStatus {
        OK;
    }
}
