package in.projecteka.datanotificationsubscription.common.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConditionalOnProperty(value = "subscriptionmanager.cacheMethod", havingValue = "redis")
@ConfigurationProperties(prefix = "subscriptionmanager.redis")
@Getter
@AllArgsConstructor
@ConstructorBinding
public class RedisOptions {
    private final String host;
    private final int port;
    private final String password;
    private final boolean keepAliveEnabled;
    private final int retry;
}
