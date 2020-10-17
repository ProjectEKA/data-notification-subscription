package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.subscription.model.HipDetail;
import in.projecteka.datanotificationsubscription.subscription.model.HiuDetail;
import in.projecteka.datanotificationsubscription.subscription.model.PatientDetail;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Builder
@Data
public class Subscription {
    UUID id;
    PatientDetail patient;
    HiuDetail hiuDetail;
    HipDetail hipDetail;
}
