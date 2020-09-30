package in.projecteka.datanotificationsubscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.nimbusds.jose.jwk.JWKSet;
import in.projecteka.datanotificationsubscription.common.GatewayTokenVerifier;
import in.projecteka.datanotificationsubscription.common.cache.CacheAdapter;
import in.projecteka.datanotificationsubscription.common.cache.LoadingCacheAdapter;
import in.projecteka.datanotificationsubscription.common.cache.LoadingCacheGenericAdapter;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static in.projecteka.datanotificationsubscription.common.Constants.DEFAULT_CACHE_VALUE;
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

    @Bean("identityServiceJWKSet")
    public JWKSet identityServiceJWKSet(IdentityServiceProperties identityServiceProperties)
            throws IOException, ParseException {
        return JWKSet.load(new URL(identityServiceProperties.getJwkUrl()));
    }

    @Bean("centralRegistryJWKSet")
    public JWKSet jwkSet(GatewayServiceProperties gatewayServiceProperties)
            throws IOException, ParseException {
        return JWKSet.load(new URL(gatewayServiceProperties.getJwkUrl()));
    }

    @Bean
    public GatewayTokenVerifier centralRegistryTokenVerifier(@Qualifier("centralRegistryJWKSet") JWKSet jwkSet) {
        return new GatewayTokenVerifier(jwkSet);
    }

    @ConditionalOnProperty(value = "consentmanager.cacheMethod", havingValue = "guava", matchIfMissing = true)
    @Bean({"accessTokenCache"})
    public CacheAdapter<String, String> createLoadingCacheAdapterForAccessToken() {
        return new LoadingCacheAdapter(stringStringLoadingCache(5));
    }

    public LoadingCache<String, String> stringStringLoadingCache(int duration) {
        return CacheBuilder
                .newBuilder()
                .expireAfterWrite(duration, TimeUnit.MINUTES)
                .build(new CacheLoader<String, String>() {
                    public String load(String key) {
                        return "";
                    }
                });
    }

    public LoadingCache<String, Integer> stringIntegerLoadingCache(int duration) {
        return CacheBuilder
                .newBuilder()
                .expireAfterWrite(duration, TimeUnit.MINUTES)
                .build(new CacheLoader<String, Integer>() {
                    public Integer load(String key) {
                        return 0;
                    }
                });
    }

    public LoadingCache<String, Boolean> stringBooleanLoadingCache(int duration) {
        return CacheBuilder
                .newBuilder()
                .expireAfterWrite(duration, TimeUnit.MINUTES)
                .build(new CacheLoader<>() {
                    public Boolean load(String key) {
                        return false;
                    }
                });
    }

    public LoadingCache<String, LocalDateTime> stringLocalDateTimeLoadingCache(int duration) {
        return CacheBuilder
                .newBuilder()
                .expireAfterWrite(duration, TimeUnit.MINUTES)
                .build(new CacheLoader<>() {
                    public LocalDateTime load(String key) {
                        return DEFAULT_CACHE_VALUE;
                    }
                });
    }
}
