package in.projecteka.datanotificationsubscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.projecteka.datanotificationsubscription.hipLink.HipLinkNotificationListener;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static in.projecteka.datanotificationsubscription.common.Constants.CM_EXCHANGE;
import static in.projecteka.datanotificationsubscription.common.Constants.DUMMY_QUEUE;
import static in.projecteka.datanotificationsubscription.common.Constants.EXCHANGE;
import static in.projecteka.datanotificationsubscription.common.Constants.HIP_LINK_QUEUE;

@Configuration
public class DataNotificationSubscriptionConfiguration {

    @Bean
    public DestinationsConfig destinationsConfig() {
        HashMap<String, DestinationsConfig.DestinationInfo> queues = new HashMap<>();
        queues.put(HIP_LINK_QUEUE,
                new DestinationsConfig.DestinationInfo(CM_EXCHANGE, HIP_LINK_QUEUE));
        return new DestinationsConfig(queues);
    }

    @Bean
    public Jackson2JsonMessageConverter converter() {
        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public MessageListenerContainerFactory messageListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        return new MessageListenerContainerFactory(connectionFactory, jackson2JsonMessageConverter);
    }

    @Bean
    public HIUSubscriptionManager subscriptionManager() {
        return new HIUSubscriptionManager();
    }

    @Bean
    public HipLinkNotificationListener linkNotificationListener(MessageListenerContainerFactory messageListenerContainerFactory,
                                                                Jackson2JsonMessageConverter converter,
                                                                HIUSubscriptionManager subscriptionManager) {
        return new HipLinkNotificationListener(messageListenerContainerFactory, converter, subscriptionManager);
    }
}
