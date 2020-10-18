package in.projecteka.datanotificationsubscription.subscription.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@AllArgsConstructor
@Builder
public class HIUSubscriptionRequestNotifyRequest {
    UUID requestId;
    LocalDateTime timestamp;
    HIUSubscriptionRequestNotification notification;
}
