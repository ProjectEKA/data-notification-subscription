package in.projecteka.datanotificationsubscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.nimbusds.jose.jwk.JWKSet;
import in.projecteka.datanotificationsubscription.clients.IdentityServiceClient;
import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.common.GatewayTokenVerifier;
import in.projecteka.datanotificationsubscription.common.GlobalExceptionHandler;
import in.projecteka.datanotificationsubscription.common.IdentityService;
import in.projecteka.datanotificationsubscription.common.RequestValidator;
import in.projecteka.datanotificationsubscription.common.cache.CacheAdapter;
import in.projecteka.datanotificationsubscription.common.cache.LoadingCacheAdapter;
import in.projecteka.datanotificationsubscription.common.cache.LoadingCacheGenericAdapter;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionRequestRepository;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionRequestService;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static in.projecteka.datanotificationsubscription.common.Constants.DEFAULT_CACHE_VALUE;
import static in.projecteka.datanotificationsubscription.common.Constants.DUMMY_QUEUE;
import static in.projecteka.datanotificationsubscription.common.Constants.EXCHANGE;

@Configuration
public class DataNotificationSubscriptionConfiguration {

    @Value("${webclient.maxInMemorySize}")
    private int maxInMemorySize;

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
    public SubscriptionRequestService subscriptionRequestService(SubscriptionRequestRepository subscriptionRepository,
                                                                 UserServiceClient userServiceClient) {
        return new SubscriptionRequestService(subscriptionRepository, userServiceClient);
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

    @ConditionalOnProperty(value = "subscriptionmanager.cacheMethod", havingValue = "guava", matchIfMissing = true)
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

    @ConditionalOnProperty(value = "subscriptionmanager.cacheMethod", havingValue = "guava", matchIfMissing = true)
    @Bean({"cacheForReplayAttack"})
    public CacheAdapter<String, LocalDateTime> stringLocalDateTimeCacheAdapter() {
        return new LoadingCacheGenericAdapter<>(stringLocalDateTimeLoadingCache(10), DEFAULT_CACHE_VALUE);
    }

    @Bean
    public RequestValidator requestValidator(
            @Qualifier("cacheForReplayAttack") CacheAdapter<String, LocalDateTime> cacheForReplayAttack) {
        return new RequestValidator(cacheForReplayAttack);
    }

    @Bean
    // This exception handler needs to be given highest priority compared to DefaultErrorWebExceptionHandler, hence order = -2.
    @Order(-2)
    public GlobalExceptionHandler clientErrorExceptionHandler(ErrorAttributes errorAttributes,
                                                              ResourceProperties resourceProperties,
                                                              ApplicationContext applicationContext,
                                                              ServerCodecConfigurer serverCodecConfigurer) {

        GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler(errorAttributes,
                resourceProperties, applicationContext);
        globalExceptionHandler.setMessageWriters(serverCodecConfigurer.getWriters());
        return globalExceptionHandler;
    }

    @Bean("cmHttpConnector")
    @ConditionalOnProperty(value = "webclient.use-connection-pool", havingValue = "false")
    public ClientHttpConnector clientHttpConnector() {
        return new ReactorClientHttpConnector(HttpClient.create(ConnectionProvider.newConnection()));
    }

    @Bean("cmHttpConnector")
    @ConditionalOnProperty(value = "webclient.use-connection-pool", havingValue = "true")
    public ClientHttpConnector pooledClientHttpConnector(WebClientOptions webClientOptions) {
        return new ReactorClientHttpConnector(
                HttpClient.create(
                        ConnectionProvider.builder("cm-http-connection-pool")
                                .maxConnections(webClientOptions.getPoolSize())
                                .maxLifeTime(Duration.ofMinutes(webClientOptions.getMaxLifeTime()))
                                .maxIdleTime(Duration.ofMinutes(webClientOptions.getMaxIdleTimeout()))
                                .build()
                )
        );
    }

    private ExchangeStrategies exchangeStrategies(ObjectMapper objectMapper) {
        var encoder = new Jackson2JsonEncoder(objectMapper);
        var decoder = new Jackson2JsonDecoder(objectMapper);
        return ExchangeStrategies
                .builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().jackson2JsonEncoder(encoder);
                    configurer.defaultCodecs().jackson2JsonDecoder(decoder);
                }).build();
    }

    @Bean("customBuilder")
    public WebClient.Builder webClient(
            @Qualifier("cmHttpConnector") final ClientHttpConnector clientHttpConnector,
            ObjectMapper objectMapper) {
        return WebClient
                .builder()
                .exchangeStrategies(exchangeStrategies(objectMapper))
                .clientConnector(clientHttpConnector)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(maxInMemorySize));
    }

    @Bean
    public IdentityService identityService(IdentityServiceClient identityServiceClient,
                                           IdentityServiceProperties identityServiceProperties,
                                           @Qualifier("accessTokenCache") CacheAdapter<String, String> accessTokenCache) {
        return new IdentityService(identityServiceClient,
                identityServiceProperties,
                accessTokenCache);
    }

    @Bean
    public IdentityServiceClient keycloakClient(@Qualifier("customBuilder") WebClient.Builder builder,
                                                IdentityServiceProperties identityServiceProperties) {
        return new IdentityServiceClient(builder, identityServiceProperties);
    }

    @Bean
    public UserServiceClient userServiceClient(
            @Qualifier("customBuilder") WebClient.Builder builder,
            UserServiceProperties userServiceProperties,
            IdentityService identityService,
            @Value("${subscriptionmanager.authorization.header}") String authorizationHeader) {
        return new UserServiceClient(builder.build(),
                userServiceProperties.getUrl(),
                identityService::authenticate,
                authorizationHeader);
    }

}
