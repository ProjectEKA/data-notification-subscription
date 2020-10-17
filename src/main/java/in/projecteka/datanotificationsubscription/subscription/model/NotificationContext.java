package in.projecteka.datanotificationsubscription.subscription.model;

import in.projecteka.datanotificationsubscription.common.model.PatientCareContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor
@Builder
public class NotificationContext {
    PatientCareContext careContext;
    List<String> hiTypes;
}
