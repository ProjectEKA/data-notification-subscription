package in.projecteka.dataavailabilitynotification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

import static in.projecteka.dataavailabilitynotification.common.Constants.DUMMY_QUEUE;
import static in.projecteka.dataavailabilitynotification.common.Constants.EXCHANGE;

@Configuration
public class DataAvailabilityNotificationConfiguration {
    @Bean
    public DestinationsConfig destinationsConfig() {
        HashMap<String, DestinationsConfig.DestinationInfo> queues = new HashMap<>();
        queues.put(DUMMY_QUEUE,
                new DestinationsConfig.DestinationInfo(EXCHANGE, DUMMY_QUEUE));
        return new DestinationsConfig(queues);
    }
}
