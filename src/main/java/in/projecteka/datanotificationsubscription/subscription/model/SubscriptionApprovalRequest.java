package in.projecteka.datanotificationsubscription.subscription.model;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class SubscriptionApprovalRequest {
    @Valid
    @NotNull(message = "Consents are not specified")
    private List<GrantedSubscription> sources;
}
