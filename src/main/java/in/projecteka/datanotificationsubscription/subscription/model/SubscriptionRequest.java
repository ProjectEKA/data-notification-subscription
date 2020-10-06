package in.projecteka.datanotificationsubscription.subscription.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@Builder
public class SubscriptionRequest {
    private UUID requestId;
    private LocalDateTime timestamp;
    private SubscriptionDetail subscription;
}
