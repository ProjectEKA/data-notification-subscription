package in.projecteka.datanotificationsubscription.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

import java.util.List;

@Getter
@Value
@Builder
@AllArgsConstructor
public class ServiceCaller {
    String clientId;
    List<Role> roles;
}

