package in.projecteka.datanotificationsubscription.subscription.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "subscriptionmanager.subscription")
@Getter
@AllArgsConstructor
@ConstructorBinding
public class SubscriptionProperties {
    private static final int DEFAULT_MAX_PAGE_SIZE = 50;
    private final int maxPageSize;
    private final int defaultPageSize;
    private final int subscriptionRequestExpiry;
    private final String url;

    public int getMaxPageSize() {
        return maxPageSize > 0 ? maxPageSize : DEFAULT_MAX_PAGE_SIZE;
    }

    public int getDefaultPageSize() {
        return defaultPageSize > 0 ? defaultPageSize : DEFAULT_MAX_PAGE_SIZE;
    }
}
