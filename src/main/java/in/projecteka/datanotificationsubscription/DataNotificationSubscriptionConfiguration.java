package in.projecteka.datanotificationsubscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import in.projecteka.datanotificationsubscription.common.CMTokenAuthenticator;
import in.projecteka.datanotificationsubscription.hipLink.HipLinkNotificationListener;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import in.projecteka.datanotificationsubscription.auth.ExternalIdentityProvider;
import in.projecteka.datanotificationsubscription.auth.IDPProperties;
import in.projecteka.datanotificationsubscription.auth.IdentityProvider;
import in.projecteka.datanotificationsubscription.clients.IdentityServiceClient;
import in.projecteka.datanotificationsubscription.clients.LinkServiceClient;
import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.common.Authenticator;
import in.projecteka.datanotificationsubscription.common.GatewayServiceClient;
import in.projecteka.datanotificationsubscription.common.GatewayTokenVerifier;
import in.projecteka.datanotificationsubscription.common.GlobalExceptionHandler;
import in.projecteka.datanotificationsubscription.common.ExternalIDPOfflineAuthenticator;
import in.projecteka.datanotificationsubscription.common.IdentityService;
import in.projecteka.datanotificationsubscription.common.RequestValidator;
import in.projecteka.datanotificationsubscription.common.ServiceAuthentication;
import in.projecteka.datanotificationsubscription.common.ServiceAuthenticationClient;
import in.projecteka.datanotificationsubscription.common.cache.CacheAdapter;
import in.projecteka.datanotificationsubscription.common.cache.LoadingCacheAdapter;
import in.projecteka.datanotificationsubscription.common.cache.LoadingCacheGenericAdapter;
import in.projecteka.datanotificationsubscription.common.cache.RedisCacheAdapter;
import in.projecteka.datanotificationsubscription.common.cache.RedisGenericAdapter;
import in.projecteka.datanotificationsubscription.common.cache.RedisOptions;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionResponseMapper;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionService;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionRequestRepository;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionRequestService;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionApprovalRequestValidator;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionProperties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
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
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.ReactiveRedisClusterConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static in.projecteka.datanotificationsubscription.common.Constants.CM_EXCHANGE;
import static in.projecteka.datanotificationsubscription.common.Constants.DEFAULT_CACHE_VALUE;
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
    public HIUSubscriptionManager subscriptionManager(SubscriptionRequestRepository subscriptionRequestRepository,
                                                      GatewayServiceClient gatewayServiceClient,
                                                      UserServiceClient userServiceClient) {
        return new HIUSubscriptionManager(subscriptionRequestRepository, gatewayServiceClient, userServiceClient);
    }

    @Bean
    public HipLinkNotificationListener linkNotificationListener(MessageListenerContainerFactory messageListenerContainerFactory,
                                                                Jackson2JsonMessageConverter converter,
                                                                HIUSubscriptionManager subscriptionManager) {
        return new HipLinkNotificationListener(messageListenerContainerFactory, converter, subscriptionManager);
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
    public ServiceAuthenticationClient serviceAuthenticationClient(
            @Qualifier("customBuilder") WebClient.Builder webClientBuilder,
            GatewayServiceProperties gatewayServiceProperties) {
        return new ServiceAuthenticationClient(webClientBuilder, gatewayServiceProperties.getBaseUrl());
    }

    @Bean
    public ServiceAuthentication serviceAuthentication(ServiceAuthenticationClient serviceAuthenticationClient,
                                                       GatewayServiceProperties gatewayServiceProperties,
                                                       @Qualifier("accessTokenCache") CacheAdapter<String, String> accessTokenCache) {
        return new ServiceAuthentication(serviceAuthenticationClient, gatewayServiceProperties, accessTokenCache);
    }

    @Bean
    public GatewayServiceClient gatewayServiceClient(@Qualifier("customBuilder") WebClient.Builder webClientBuilder,
                                                     GatewayServiceProperties gatewayServiceProperties,
                                                     ServiceAuthentication serviceAuthentication) {
        return new GatewayServiceClient(webClientBuilder, gatewayServiceProperties, serviceAuthentication);
    }

    @Bean
    public SubscriptionRequestService subscriptionRequestService(SubscriptionRequestRepository subscriptionRepository,
                                                                 UserServiceClient userServiceClient,
                                                                 GatewayServiceClient gatewayServiceClient,
                                                                 LinkServiceClient linkServiceClient,
                                                                 ConceptValidator conceptValidator,
                                                                 SubscriptionProperties subscriptionProperties) {
        return new SubscriptionRequestService(subscriptionRepository, userServiceClient,
                gatewayServiceClient, linkServiceClient, conceptValidator, subscriptionProperties);
    }

    @Bean
    public SubscriptionRequestRepository subscriptionRequestRepository(@Qualifier("readWriteClient") PgPool readWriteClient,
                                                                @Qualifier("readOnlyClient") PgPool readOnlyClient) {
        return new SubscriptionRequestRepository(readWriteClient, readOnlyClient);
    }

    @Bean
    public SubscriptionResponseMapper subscriptionResponseMapper(){
        return new SubscriptionResponseMapper();
    }

    @Bean
    public SubscriptionRepository subscriptionRepository(
            @Qualifier("readOnlyClient") PgPool readOnlyClient,
            SubscriptionResponseMapper subscriptionResponseMapper) {
        return new SubscriptionRepository(readOnlyClient, subscriptionResponseMapper);
    }

    @Bean
    public SubscriptionService subscriptionService(SubscriptionRepository subscriptionRepository,
                                                   UserServiceClient userServiceClient){
        return new SubscriptionService(userServiceClient, subscriptionRepository);
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

    @ConditionalOnProperty(value = "subscriptionmanager.cacheMethod", havingValue = "guava")
    @Bean({"accessTokenCache", "blockListedTokens"})
    public CacheAdapter<String, String> createLoadingCacheAdapterForAccessToken() {
        return new LoadingCacheAdapter(stringStringLoadingCache(5));
    }

    @ConditionalOnProperty(value = "subscriptionmanager.cacheMethod", havingValue = "redis")
    @Bean({"accessTokenCache", "blockListedTokens"})
    public CacheAdapter<String, String> createRedisCacheAdapter(
            ReactiveRedisOperations<String, String> stringReactiveRedisOperations,
            RedisOptions redisOptions) {
        return new RedisCacheAdapter(stringReactiveRedisOperations, 5,
                redisOptions.getRetry());
    }


    @ConditionalOnProperty(value = "subscriptionmanager.cacheMethod", havingValue = "redis")
    @Bean("Lettuce")
    ReactiveRedisConnectionFactory redisConnection(RedisOptions redisOptions) {
        var socketOptions = SocketOptions.builder().keepAlive(redisOptions.isKeepAliveEnabled()).build();
        var clientConfiguration = LettuceClientConfiguration.builder()
                .clientOptions(ClientOptions.builder().socketOptions(socketOptions).build())
                .build();
        var configuration = new RedisStandaloneConfiguration(redisOptions.getHost(), redisOptions.getPort());
        configuration.setPassword(redisOptions.getPassword());
        return new LettuceConnectionFactory(configuration, clientConfiguration);
    }

    @ConditionalOnProperty(value = "subscriptionmanager.cacheMethod", havingValue = "guava", matchIfMissing = true)
    @Bean("Lettuce")
    ReactiveRedisConnectionFactory dummyRedisConnection() {
        return new ReactiveRedisConnectionFactory() {
            @Override
            public ReactiveRedisConnection getReactiveConnection() {
                return null;
            }

            @Override
            public ReactiveRedisClusterConnection getReactiveClusterConnection() {
                return null;
            }

            @Override
            public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
                return null;
            }
        };
    }


    @ConditionalOnProperty(value = "subscriptionmanager.cacheMethod", havingValue = "redis")
    @Bean
    ReactiveRedisOperations<String, String> stringReactiveRedisOperations(
            @Qualifier("Lettuce") ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<String> serializer = new Jackson2JsonRedisSerializer<>(String.class);
        RedisSerializationContext.RedisSerializationContextBuilder<String, String> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        RedisSerializationContext<String, String> context = builder.value(serializer).build();
        return new ReactiveRedisTemplate<>(factory, context);
    }

    @ConditionalOnProperty(value = "subscriptionmanager.cacheMethod", havingValue = "redis")
    @Bean
    ReactiveRedisOperations<String, Integer> stringIntegerReactiveRedisOperations(
            @Qualifier("Lettuce") ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<Integer> serializer = new Jackson2JsonRedisSerializer<>(Integer.class);
        RedisSerializationContext.RedisSerializationContextBuilder<String, Integer> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        RedisSerializationContext<String, Integer> context = builder.value(serializer).build();
        return new ReactiveRedisTemplate<>(factory, context);
    }

    @ConditionalOnProperty(value = "subscriptionmanager.cacheMethod", havingValue = "redis")
    @Bean
    ReactiveRedisOperations<String, LocalDateTime> stringBooleanReactiveRedisOperations(
            @Qualifier("Lettuce") ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<LocalDateTime> serializer = new Jackson2JsonRedisSerializer<>(LocalDateTime.class);
        RedisSerializationContext.RedisSerializationContextBuilder<String, LocalDateTime> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        RedisSerializationContext<String, LocalDateTime> context = builder.value(serializer).build();
        return new ReactiveRedisTemplate<>(factory, context);
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

    @ConditionalOnProperty(value = "subscriptionmanager.cacheMethod", havingValue = "redis")
    @Bean({"cacheForReplayAttack"})
    public CacheAdapter<String, LocalDateTime> createRedisCacheAdapterForReplayAttack(
            ReactiveRedisOperations<String, LocalDateTime> localDateTimeOps,
            RedisOptions redisOptions) {
        return new RedisGenericAdapter<>(localDateTimeOps, 10, redisOptions.getRetry());
    }


    @Bean
    public IdentityServiceClient keycloakClient(@Qualifier("customBuilder") WebClient.Builder builder,
                                                IdentityServiceProperties identityServiceProperties) {
        return new IdentityServiceClient(builder, identityServiceProperties);
    }

    @Bean
    public IdentityService identityService(
            @Qualifier("accessTokenCache") CacheAdapter<String, String> accessTokenCache,
            IdentityServiceClient identityServiceClient,
            IdentityServiceProperties identityServiceProperties) {
        return new IdentityService(accessTokenCache, identityServiceClient, identityServiceProperties);
    }

    @Bean("userAuthenticator")
    @ConditionalOnProperty(value = "subscriptionmanager.authorization.externalIDPForUserAuth", havingValue = "false", matchIfMissing = true)
    public Authenticator cmTokenAuthenticator(
            @Qualifier("identityServiceJWKSet") JWKSet jwkSet,
            CacheAdapter<String, String> blockListedTokens,
            ConfigurableJWTProcessor<SecurityContext> jwtProcessor) {
        return new CMTokenAuthenticator(jwkSet, blockListedTokens, jwtProcessor);
    }

    @Bean
    @ConditionalOnProperty(value = "subscriptionmanager.authorization.externalIDPForUserAuth", havingValue = "true")
    public IdentityProvider externalIdentityProvider(@Qualifier("customBuilder") WebClient.Builder builder,
                                                IDPProperties idpProperties) {
        return new ExternalIdentityProvider(builder, idpProperties);
    }

    @Bean("userAuthenticator")
    @ConditionalOnProperty(value = "subscriptionmanager.authorization.externalIDPForUserAuth", havingValue = "true")
    public Authenticator externalTokenAuthenticator(IdentityProvider identityProvider,
                                                    CacheAdapter<String, String> blockListedTokens)
            throws InvalidKeySpecException, NoSuchAlgorithmException {
        String certificate = identityProvider.fetchCertificate().block();
        var kf = KeyFactory.getInstance("RSA");
        var keySpecX509 = new X509EncodedKeySpec(Base64.getDecoder().decode(certificate));
        RSASSAVerifier tokenVerifier = new RSASSAVerifier((RSAPublicKey) kf.generatePublic(keySpecX509));

        return new ExternalIDPOfflineAuthenticator(tokenVerifier, blockListedTokens);
    }

    @Bean
    public RequestValidator requestValidator(
            @Qualifier("cacheForReplayAttack") CacheAdapter<String, LocalDateTime> cacheForReplayAttack) {
        return new RequestValidator(cacheForReplayAttack);
    }

    @Bean
    public SubscriptionApprovalRequestValidator subscriptionApprovalRequestValidator() {
        return new SubscriptionApprovalRequestValidator();
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

    @Bean("subscriptionHttpConnector")
    @ConditionalOnProperty(value = "webclient.use-connection-pool", havingValue = "false")
    public ClientHttpConnector clientHttpConnector() {
        return new ReactorClientHttpConnector(HttpClient.create(ConnectionProvider.newConnection()));
    }

    @Bean("subscriptionHttpConnector")
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

    @Bean
    public LinkServiceClient linkServiceClient(
            @Qualifier("customBuilder") WebClient.Builder builder,
            LinkServiceProperties linkServiceProperties,
            IdentityService identityService,
            @Value("${subscriptionmanager.authorization.header}") String authorizationHeader) {
        return new LinkServiceClient(builder.build(),
                linkServiceProperties.getUrl(),
                identityService::authenticate,
                authorizationHeader);
    }

    @Bean
    public ReactorClientHttpConnector reactorClientHttpConnector() {
        HttpClient httpClient = null;
        try {
            SslContext sslContext = SslContextBuilder
                    .forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext));
        } catch (SSLException e) {
            e.printStackTrace();
        }
        return new ReactorClientHttpConnector(Objects.requireNonNull(httpClient));
    }

    @Bean("customBuilder")
    public WebClient.Builder webClient(
            @Qualifier("subscriptionHttpConnector") final ClientHttpConnector clientHttpConnector,
            ObjectMapper objectMapper) {
        return WebClient
                .builder()
                .exchangeStrategies(exchangeStrategies(objectMapper))
                .clientConnector(clientHttpConnector);
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
}
