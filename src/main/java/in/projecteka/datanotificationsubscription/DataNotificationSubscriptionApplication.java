package in.projecteka.datanotificationsubscription;

import in.projecteka.datanotificationsubscription.auth.IDPProperties;
import in.projecteka.datanotificationsubscription.common.RabbitMQOptions;
import in.projecteka.datanotificationsubscription.common.cache.RedisOptions;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({RabbitMQOptions.class,
                                DbOptions.class,
                                IdentityServiceProperties.class,
                                GatewayServiceProperties.class,
                                UserServiceProperties.class,
                                IDPProperties.class,
                                SubscriptionProperties.class,
                                RedisOptions.class,
                                WebClientOptions.class})
public class DataNotificationSubscriptionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataNotificationSubscriptionApplication.class, args);
    }

}
