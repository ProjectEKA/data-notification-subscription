package in.projecteka.datanotificationsubscription.subscription.model;

import in.projecteka.datanotificationsubscription.common.model.RequesterType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SubscriptionRequestDetails {
    private UUID id;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private SubscriptionPurpose purpose;
    private PatientDetail patient;
    private HiuDetail hiu;
    private List<HipDetail> hips;
    private List<Category> categories;
    private AccessPeriod period;
    private RequestStatus status;
    private RequesterType requester;
    private UUID subscriptionId;
}
