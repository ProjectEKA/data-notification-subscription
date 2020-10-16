package in.projecteka.datanotificationsubscription.subscription.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@AllArgsConstructor
@Builder
public class NotificationEvent {
    UUID id;
    LocalDateTime published;
    UUID subscriptionId;
    Categories category;
    NotificationContent content;
}
