package in.projecteka.datanotificationsubscription.subscription.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.projecteka.datanotificationsubscription.common.model.HIType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
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
