package in.projecteka.datanotificationsubscription.subscription.model;

import in.projecteka.datanotificationsubscription.common.model.HIType;
import in.projecteka.datanotificationsubscription.common.model.RequesterType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
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
    SubscriptionPurpose purpose;
    LocalDateTime dateCreated;
    RequestStatus status;
    LocalDateTime dateGranted;
    PatientDetail patient;
    Requester requester;
    List<SubscriptionSource> sources;

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

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SubscriptionSource {
        HipDetail hipDetail;
        List<Category> categories;
        List<HIType> hiTypes;
        AccessPeriod period;
        SubscriptionStatus status;
    }
}
