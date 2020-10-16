package in.projecteka.datanotificationsubscription.subscription.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class SubscriptionNotification {
    String hiuId;
    NotificationEvent event;
}
