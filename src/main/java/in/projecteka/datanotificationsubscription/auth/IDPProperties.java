package in.projecteka.datanotificationsubscription.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@Builder
@ConfigurationProperties(prefix = "subscriptionmanager.authorization")
@AllArgsConstructor
@ConstructorBinding
@Getter
public class IDPProperties {
    private final String idpCertPath;
    private final boolean requireAuthForCerts;
    private final String idpClientId;
    private final String idpClientSecret;
    private final String idpAuthURL;
}
