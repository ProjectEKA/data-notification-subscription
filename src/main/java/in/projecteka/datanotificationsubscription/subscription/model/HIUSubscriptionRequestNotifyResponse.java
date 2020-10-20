package in.projecteka.datanotificationsubscription.subscription.model;

import in.projecteka.datanotificationsubscription.common.Error;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HIUSubscriptionRequestNotifyResponse {
    UUID requestId;
    LocalDateTime timestamp;
    Acknowledgement acknowledgement;
    Error error;
    GatewayResponse resp;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Acknowledgement {
        AcknowledgementStatus status;
        String subscriptionRequestId;
    }

    public enum AcknowledgementStatus {
        OK;
    }
}
