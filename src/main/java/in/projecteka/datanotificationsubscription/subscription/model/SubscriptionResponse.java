package in.projecteka.datanotificationsubscription.subscription.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import in.projecteka.datanotificationsubscription.common.model.RequesterType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubscriptionResponse {
    UUID subscriptionId;
    @JsonIgnore
    String subscriptionRequestId;
    SubscriptionPurpose purpose;
    LocalDateTime dateCreated;
    RequestStatus status;
    LocalDateTime dateGranted;
    PatientDetail patient;
    Requester requester;
    List<SubscriptionSource> includedSources;
    List<SubscriptionSource> excludedSources;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Requester {
        String id;
        String name;
        RequesterType type;
    }
}
