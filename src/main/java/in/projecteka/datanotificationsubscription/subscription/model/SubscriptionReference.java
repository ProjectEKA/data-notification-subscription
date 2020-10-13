package in.projecteka.datanotificationsubscription.subscription.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionReference {
    private String id;
    private RequestStatus status;
}
