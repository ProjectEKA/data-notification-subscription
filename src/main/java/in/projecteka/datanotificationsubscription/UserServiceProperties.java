package in.projecteka.datanotificationsubscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "subscriptionmanager.userservice")
@Getter
@AllArgsConstructor
@ConstructorBinding
@Builder
public class UserServiceProperties {
    private final String url;
    private final String userIdSuffix;
}
