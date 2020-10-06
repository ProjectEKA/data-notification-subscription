package in.projecteka.datanotificationsubscription.subscription.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubscriptionDetail {
    private SubscriptionPurpose purpose;
    private PatientDetail patient;
    private HiuDetail hiu;
    private List<HipDetail> hips;
    private List<Type> types;
    private AccessPeriod period;
}
