package in.projecteka.datanotificationsubscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "subscriptionmanager.user-auth-service")
@Getter
@AllArgsConstructor
@ConstructorBinding
@Builder
public class UserAuthorizationServiceProperties {
    private final String url;
}
