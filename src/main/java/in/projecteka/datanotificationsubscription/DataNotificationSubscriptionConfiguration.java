package in.projecteka.datanotificationsubscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionRequestRepository;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionRequestService;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequest;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static in.projecteka.datanotificationsubscription.common.Constants.DUMMY_QUEUE;
import static in.projecteka.datanotificationsubscription.common.Constants.EXCHANGE;

@Configuration
public class DataNotificationSubscriptionConfiguration {

    @Bean
    public DestinationsConfig destinationsConfig() {
        HashMap<String, DestinationsConfig.DestinationInfo> queues = new HashMap<>();
        queues.put(DUMMY_QUEUE,
                new DestinationsConfig.DestinationInfo(EXCHANGE, DUMMY_QUEUE));
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
    public SampleNotificationPublisher notificationPublisher(AmqpTemplate amqpTemplate,
                                                             DestinationsConfig destinationsConfig) {
        return new SampleNotificationPublisher(amqpTemplate, destinationsConfig);
    }

    @Bean
    public SampleNotificationListener notificationListener(MessageListenerContainerFactory messageListenerContainerFactory,
                                                           Jackson2JsonMessageConverter converter) {
        return new SampleNotificationListener(messageListenerContainerFactory, converter);
    }

    @Bean("readWriteClient")
    public PgPool readWriteClient(DbOptions dbOptions) {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(dbOptions.getPort())
                .setHost(dbOptions.getHost())
                .setDatabase(dbOptions.getSchema())
                .setUser(dbOptions.getUser())
                .setPassword(dbOptions.getPassword());

        PoolOptions poolOptions = new PoolOptions().setMaxSize(dbOptions.getPoolSize());
        return PgPool.pool(connectOptions, poolOptions);
    }

    @Bean("readOnlyClient")
    public PgPool readOnlyClient(DbOptions dbOptions) {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(dbOptions.getReplica().getPort())
                .setHost(dbOptions.getReplica().getHost())
                .setDatabase(dbOptions.getSchema())
                .setUser(dbOptions.getReplica().getUser())
                .setPassword(dbOptions.getReplica().getPassword());

        PoolOptions poolOptions = new PoolOptions().setMaxSize(dbOptions.getReplica().getPoolSize());
        return PgPool.pool(connectOptions, poolOptions);
    }

    @Bean
    public SubscriptionRequestService subscriptionRequestService(SubscriptionRequestRepository subscriptionRepository){
        return new SubscriptionRequestService(subscriptionRepository);
    }

    @Bean
    public SubscriptionRequestRepository subscriptionRepository(@Qualifier("readWriteClient") PgPool readWriteClient,
                                                       @Qualifier("readOnlyClient") PgPool readOnlyClient) {
        return new SubscriptionRequestRepository(readWriteClient, readOnlyClient);
    }
}
