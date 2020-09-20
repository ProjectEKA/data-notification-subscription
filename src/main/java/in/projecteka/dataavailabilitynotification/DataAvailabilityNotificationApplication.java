package in.projecteka.dataavailabilitynotification;

import in.projecteka.dataavailabilitynotification.common.RabbitMQOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RabbitMQOptions.class})
public class DataAvailabilityNotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataAvailabilityNotificationApplication.class, args);
    }

}
