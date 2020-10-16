package in.projecteka.datanotificationsubscription.subscription.model;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public class NotificationEvent {
    UUID id;
    LocalDateTime published;
    UUID subscriptionId;
    Categories category;
    NotificationContent content;
}
