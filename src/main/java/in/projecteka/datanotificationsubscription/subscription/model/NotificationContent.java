package in.projecteka.datanotificationsubscription.subscription.model;

import lombok.Builder;

import java.util.List;

@Builder
public class NotificationContent {
    PatientDetail patient;
    HipDetail hip;
    List<NotificationContext> context;
}
