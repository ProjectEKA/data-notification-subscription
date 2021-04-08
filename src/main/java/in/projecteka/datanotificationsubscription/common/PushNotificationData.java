package in.projecteka.datanotificationsubscription.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class PushNotificationData {
    String healthId;
    String target;
    Object params;
    String title;
    String body;
}
