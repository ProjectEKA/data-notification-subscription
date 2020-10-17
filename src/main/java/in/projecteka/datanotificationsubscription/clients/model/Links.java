package in.projecteka.datanotificationsubscription.clients.model;

import in.projecteka.datanotificationsubscription.subscription.model.HipDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Links {
    private HipDetail hip;
}
