package in.projecteka.datanotificationsubscription.clients;

import in.projecteka.datanotificationsubscription.clients.model.User;
import in.projecteka.datanotificationsubscription.common.cache.CacheAdapter;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

import static in.projecteka.datanotificationsubscription.common.ClientError.userNotFound;
import static in.projecteka.datanotificationsubscription.common.Constants.CORRELATION_ID;
import static in.projecteka.datanotificationsubscription.common.Serializer.from;
import static in.projecteka.datanotificationsubscription.common.Serializer.to;
import static reactor.core.publisher.Mono.error;

@AllArgsConstructor
public class UserServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceClient.class);
    private static final String INTERNAL_PATH_USER_IDENTIFICATION = "%s/internal/users/%s/";
    private static final String INTERNAL_PATH_USER_LINKS = "%s/internal/patients/%s/links";
    private static final String userCacheKeyPrefixFormat = "USER:%s";

    private final WebClient webClient;
    private final String url;
    private final Supplier<Mono<String>> tokenGenerator;
    private final String authorizationHeader;
    private final CacheAdapter<String, String> patientCache;

    public Mono<User> userOf(String userId) {
        return patientCache.getIfPresent(getCacheKey(userId))
                .map(userData -> to(userData, User.class))
                .switchIfEmpty(Mono.defer(() -> tokenGenerator.get()
                        .flatMap(token -> webClient
                                .get()
                                .uri(String.format(INTERNAL_PATH_USER_IDENTIFICATION, url, userId))
                                .header(authorizationHeader, token)
                                .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                                .retrieve()
                                .onStatus(httpStatus -> httpStatus.value() == 404,
                                        clientResponse -> clientResponse.bodyToMono(String.class)
                                                .doOnNext(logger::error)
                                                .then(error(userNotFound())))
                                .bodyToMono(User.class))
                        .flatMap(user -> patientCache.put(getCacheKey(userId), from(user)).thenReturn(user))
                        .doOnSubscribe(subscription -> logger.info("Call internal user service for user-id: {}", userId))));
    }

    private String getCacheKey(String userId) {
        return String.format(userCacheKeyPrefixFormat, userId);
    }
}