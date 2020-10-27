package in.projecteka.datanotificationsubscription.subscription.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.CollectionUtils;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Builder
@NoArgsConstructor
@Data
public class SubscriptionApprovalRequest {
    private boolean isApplicableForAllHIPs;

    @Valid
    @NotNull(message = "Sources are not specified")
    @JsonAlias({"includedSources", "sources"}) //TODO: Remove sources once app is compatible
    private List<GrantedSubscription> includedSources;

    private List<GrantedSubscription> excludeSources;

    public List<GrantedSubscription> getExcludeSources() {
        return CollectionUtils.isEmpty(excludeSources)
                ? new ArrayList<>()
                : excludeSources;
    }
}
