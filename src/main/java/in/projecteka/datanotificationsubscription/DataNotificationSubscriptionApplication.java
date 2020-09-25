package in.projecteka.datanotificationsubscription;

import in.projecteka.datanotificationsubscription.common.RabbitMQOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RabbitMQOptions.class})
public class DataNotificationSubscriptionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataNotificationSubscriptionApplication.class, args);
    }

}
