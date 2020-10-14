package in.projecteka.datanotificationsubscription.subscription.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@AllArgsConstructor
@Builder
public class SubscriptionRequestAck {
    UUID id;
}
