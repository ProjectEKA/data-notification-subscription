package in.projecteka.datanotificationsubscription.subscription.model;

import in.projecteka.datanotificationsubscription.common.model.HIType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class GrantedSubscription {
    @Valid
    @NotNull(message = "Purpose not specified.")
    private SubscriptionPurpose careContexts;

    @NotNull(message = "HI Types are not specified.")
    private HIType[] hiTypes;

    @Valid
    @NotNull(message = "Permission is not specified.")
    private HipDetail hip;

    @Valid
    private List<Categories> categories;

    @Valid
    private AccessPeriod period;

    public boolean isLinkCategory() {
        return this.categories.contains(Categories.LINK);
    }

    public boolean isDataCategory() {
        return this.categories.contains(Categories.DATA);
    }

}
