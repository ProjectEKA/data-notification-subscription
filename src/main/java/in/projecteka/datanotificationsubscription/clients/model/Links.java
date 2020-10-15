package in.projecteka.datanotificationsubscription.clients.model;

import in.projecteka.datanotificationsubscription.subscription.model.HipDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor
public class Links {
    private HipDetail hip;
}
