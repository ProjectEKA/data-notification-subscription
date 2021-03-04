package in.projecteka.datanotificationsubscription;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "subscriptionmanager.listeners")
@AllArgsConstructor
@Getter
@ConstructorBinding
public class ListenerProperties {
    private final int linkEventMaximumRetries;
    private final long linkEventRetryInterval;
}
