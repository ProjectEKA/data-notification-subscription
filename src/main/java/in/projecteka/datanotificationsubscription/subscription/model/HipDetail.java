package in.projecteka.datanotificationsubscription.subscription.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import javax.annotation.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class HipDetail {
    private String id;
    @Nullable
    private String name;
}
