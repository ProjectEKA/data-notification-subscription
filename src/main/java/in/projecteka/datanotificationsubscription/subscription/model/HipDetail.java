package in.projecteka.datanotificationsubscription.subscription.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.annotation.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HipDetail {
    private String id;
    @Nullable
    private String name;
}
