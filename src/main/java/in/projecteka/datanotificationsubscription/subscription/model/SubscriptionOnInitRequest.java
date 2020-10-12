package in.projecteka.datanotificationsubscription.subscription.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@AllArgsConstructor
@Builder
public class SubscriptionOnInitRequest {
    UUID requestId;
    LocalDateTime timestamp;
    SubscriptionRequestAck subscriptionRequest;
    private RespError error;
    @NotNull
    private GatewayResponse resp;
}
