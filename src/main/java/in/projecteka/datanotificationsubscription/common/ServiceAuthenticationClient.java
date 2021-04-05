package in.projecteka.datanotificationsubscription.common;

import in.projecteka.datanotificationsubscription.common.model.Session;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Properties;

import static in.projecteka.datanotificationsubscription.common.ClientError.unAuthorized;
import static in.projecteka.datanotificationsubscription.common.Constants.CORRELATION_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON;

public class ServiceAuthenticationClient {
    private final Logger logger = LoggerFactory.getLogger(ServiceAuthenticationClient.class);
    private final WebClient webClient;

    public ServiceAuthenticationClient(WebClient.Builder webClient, String baseUrl) {
        this.webClient = webClient.baseUrl(baseUrl).build();
    }

    public Mono<Session> getTokenFor(String clientId, String clientSecret) {
        return webClient
                .post()
                .uri("/sessions")
                .contentType(APPLICATION_JSON)
                .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                .accept(APPLICATION_JSON)
                .body(BodyInserters.fromValue(requestWith(clientId, clientSecret)))
                .retrieve()
                .onStatus(HttpStatus::isError, clientResponse -> clientResponse.bodyToMono(Properties.class)
                        .doOnNext(properties -> logger.error(properties.toString()))
                        .then(Mono.error(unAuthorized())))
                .bodyToMono(Session.class)
                .publishOn(Schedulers.elastic())
                .doOnSubscribe(subscription -> logger.info("About to call gateway to get token"));
    }

    private SessionRequest requestWith(String clientId, String clientSecret) {
        return new SessionRequest(clientId, clientSecret);
    }

    @AllArgsConstructor
    @Value
    private static class SessionRequest {
        String clientId;
        String clientSecret;
    }
}
