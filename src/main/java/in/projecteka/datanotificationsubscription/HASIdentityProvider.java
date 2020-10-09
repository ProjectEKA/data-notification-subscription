package in.projecteka.datanotificationsubscription;

import in.projecteka.datanotificationsubscription.common.model.Session;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static in.projecteka.datanotificationsubscription.common.ClientError.networkServiceCallFailed;
import static in.projecteka.datanotificationsubscription.common.ClientError.unAuthorized;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

public class HASIdentityProvider implements IdentityProvider {
    private static final Logger logger = LoggerFactory.getLogger(HASIdentityProvider.class);
    private final WebClient webClient;
    private final IDPProperties idpProperties;

    public HASIdentityProvider(WebClient.Builder builder, IDPProperties idpProperties) {
        this.webClient = builder.build();
        this.idpProperties = idpProperties;
    }

    @Override
    public Mono<String> fetchCertificate() {
        return getToken()
                .flatMap(token -> webClient
                .get()
                .uri(idpProperties.getIdpCertPath())
                .header(AUTHORIZATION, String.format("Bearer %s", token))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSubscribe(subscription -> logger.info("About to fetch certificate"))
                .map(this::extractPublicKey));
    }

    private String extractPublicKey(String response) {
        if (StringUtils.isEmpty(response)) return null;
        return response.replaceAll("\\n", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "");
    }

    private Mono<String> getToken() {
        return webClient
                .post()
                .uri(idpProperties.getIdpAuthURL())
                .contentType(APPLICATION_JSON)
                .accept(APPLICATION_JSON)
                .body(BodyInserters.fromValue(requestWith(idpProperties.getIdpClientId(), idpProperties.getIdpClientSecret())))
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .doOnNext(logger::error).then(Mono.error(unAuthorized())))
                .onStatus(HttpStatus::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .doOnNext(logger::error)
                        .then(Mono.error(networkServiceCallFailed())))
                .bodyToMono(Session.class)
                .map(Session::getAccessToken)
                .doOnSubscribe(subscription -> logger.info("About to call gateway to get access token for HAS"));
    }

    private SessionRequest requestWith(String clientId, String clientSecret) {
        return new SessionRequest(clientId, clientSecret);
    }

}

@AllArgsConstructor
@Value
class SessionRequest {
    String clientId;
    String clientSecret;
}
