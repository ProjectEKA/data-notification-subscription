package in.projecteka.datanotificationsubscription.subscription.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;


@Value
@AllArgsConstructor
@Builder
public class HIUSubscriptionRequestNotification {
    UUID subscriptionRequestId;
    String status;
    Subscription subscription;

    @Value
    @AllArgsConstructor
    @Builder
    public static class Subscription {
        UUID id;
        PatientDetail patient;
        HiuDetail hiu;
        List<Source> sources;
    }

    @Value
    @AllArgsConstructor
    @Builder
    public static class Source {
        HipDetail hip;
        List<Category> categories;
        AccessPeriod period;
    }
}
