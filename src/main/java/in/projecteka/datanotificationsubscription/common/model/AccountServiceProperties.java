package in.projecteka.datanotificationsubscription.common.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "subscriptionmanager.accountservice")
@AllArgsConstructor
@ConstructorBinding
@Getter
public class AccountServiceProperties {
        private final boolean usingUnsecureSSL;
        private final String url;
        private final boolean enableOfflineVerification;
        private final String clientId;
        private final String clientSecret;
        private final String hasAuthUrl;
        private final boolean hasBehindGateway;
}