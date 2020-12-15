package in.projecteka.datanotificationsubscription.subscription.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Builder
@Data
public class PatientSubscriptionRequestsRepresentation {
    private SubscriptionRequestsRepresentation hiuSubscriptionRequestsRepresentation;
    private SubscriptionRequestsRepresentation lockerSubscriptionRequestsRepresentation;
}
