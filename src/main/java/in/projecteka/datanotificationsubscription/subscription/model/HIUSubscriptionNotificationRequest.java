package in.projecteka.datanotificationsubscription.subscription.model;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public class HIUSubscriptionNotificationRequest {
    UUID requestId;
    LocalDateTime timestamp;
    NotificationEvent event;
}
