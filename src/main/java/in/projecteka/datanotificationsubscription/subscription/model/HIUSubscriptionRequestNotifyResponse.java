package in.projecteka.datanotificationsubscription.subscription.model;

import in.projecteka.datanotificationsubscription.common.Error;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@AllArgsConstructor
@Builder
public class HIUSubscriptionRequestNotifyResponse {
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
        String subscriptionRequestId;
    }


    public enum AcknowledgementStatus {
        OK;
    }
}
