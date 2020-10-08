package in.projecteka.datanotificationsubscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import in.projecteka.datanotificationsubscription.common.Authenticator;
import in.projecteka.datanotificationsubscription.common.GatewayTokenVerifier;
import in.projecteka.datanotificationsubscription.common.GlobalExceptionHandler;
import in.projecteka.datanotificationsubscription.common.IDPOfflineAuthenticator;
import in.projecteka.datanotificationsubscription.common.RequestValidator;
import in.projecteka.datanotificationsubscription.common.cache.CacheAdapter;
import in.projecteka.datanotificationsubscription.common.cache.LoadingCacheAdapter;
import in.projecteka.datanotificationsubscription.common.cache.LoadingCacheGenericAdapter;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionRequestRepository;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionRequestService;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
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

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Objects;
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
    public SubscriptionRequestService subscriptionRequestService(SubscriptionRequestRepository subscriptionRepository) {
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

    //TODO: Configure connection pooling
    @Bean("cmHttpConnector")
    public ClientHttpConnector clientHttpConnector() {
        return new ReactorClientHttpConnector(HttpClient.create(ConnectionProvider.newConnection()));
    }

    @Bean("customBuilder")
    public WebClient.Builder webClient(
            @Qualifier("cmHttpConnector") final ClientHttpConnector clientHttpConnector,
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

    @Bean
    @ConditionalOnProperty(value = "subscriptionmanager.authorization.requireAuthForCerts", havingValue = "false", matchIfMissing = true)
    public IdentityProvider cmIdentityProvider(@Qualifier("customBuilder") WebClient.Builder builder,
                                               IDPProperties idpProperties) {
        return new CMIdentityProvider(builder, idpProperties);
    }

    @Bean
    @ConditionalOnProperty(value = "subscriptionmanager.authorization.requireAuthForCerts", havingValue = "true")
    public IdentityProvider hasIdentityProvider(@Qualifier("customBuilder") WebClient.Builder builder,
                                                IDPProperties idpProperties) {
        return new HASIdentityProvider(builder, idpProperties);
    }

    @Bean("userAuthenticator")
    public Authenticator accountServiceTokenAuthenticator(IdentityProvider identityProvider,
                                                          CacheAdapter<String, String> blockListedTokens)
            throws InvalidKeySpecException, NoSuchAlgorithmException {
        String certificate = identityProvider.fetchCertificate().block();
        var kf = KeyFactory.getInstance("RSA");
        var keySpecX509 = new X509EncodedKeySpec(Base64.getDecoder().decode(certificate));
        RSASSAVerifier tokenVerifier = new RSASSAVerifier((RSAPublicKey) kf.generatePublic(keySpecX509));

        return new IDPOfflineAuthenticator(tokenVerifier, blockListedTokens);
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
}
