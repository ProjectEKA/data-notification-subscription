package in.projecteka.datanotificationsubscription.subscription.model;

import in.projecteka.datanotificationsubscription.common.model.PatientCareContext;
import lombok.Builder;

import java.util.List;

@Builder
public class NotificationContext {
    PatientCareContext careContext;
    List<String> hiTypes;
}
