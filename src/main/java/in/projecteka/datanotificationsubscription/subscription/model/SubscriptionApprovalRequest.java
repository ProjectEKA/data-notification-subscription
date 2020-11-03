package in.projecteka.datanotificationsubscription.subscription.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class SubscriptionApprovalRequest {
    @Valid
    @NotNull(message = "Sources are not specified")
    @JsonAlias({"includedSources", "sources"}) //TODO: Remove sources once app is compatible
    private List<GrantedSubscription> includedSources;
}
