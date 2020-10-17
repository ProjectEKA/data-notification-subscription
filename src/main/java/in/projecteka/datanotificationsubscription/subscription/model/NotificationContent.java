package in.projecteka.datanotificationsubscription.subscription.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor
@Builder
public class NotificationContent {
    PatientDetail patient;
    HipDetail hip;
    List<NotificationContext> context;
}
