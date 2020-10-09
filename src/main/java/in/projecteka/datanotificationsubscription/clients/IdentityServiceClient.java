package in.projecteka.datanotificationsubscription.clients;

import in.projecteka.datanotificationsubscription.IdentityServiceProperties;
import in.projecteka.datanotificationsubscription.clients.model.KeyCloakError;
import in.projecteka.datanotificationsubscription.clients.model.KeycloakUser;
import in.projecteka.datanotificationsubscription.clients.model.Session;
import in.projecteka.datanotificationsubscription.common.ClientError;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static in.projecteka.datanotificationsubscription.common.ClientError.networkServiceCallFailed;
import static in.projecteka.datanotificationsubscription.common.ClientError.unknownUnauthorizedError;
import static java.lang.String.format;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.reactive.function.BodyInserters.fromFormData;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;

public class IdentityServiceClient {

    private final WebClient webClient;

    public IdentityServiceClient(WebClient.Builder webClient,
                                 IdentityServiceProperties identityServiceProperties) {
        this.webClient = webClient.baseUrl(identityServiceProperties.getBaseUrl()).build();
    }

    public Mono<Session> getToken(MultiValueMap<String, String> formData) {
        return webClient
                .post()
                .uri(uriBuilder ->
                        uriBuilder.path("/realms/consent-manager/protocol/openid-connect/token").build())
                .contentType(APPLICATION_FORM_URLENCODED)
                .accept(APPLICATION_JSON)
                .body(fromFormData(formData))
                .retrieve()
                .onStatus(httpStatus -> httpStatus.value() == 401,
                        clientResponse -> clientResponse.bodyToMono(KeyCloakError.class).flatMap(toClientError()))
                .onStatus(HttpStatus::isError, clientResponse -> error(ClientError.networkServiceCallFailed()))
                .bodyToMono(Session.class);
    }

    private Function<KeyCloakError, Mono<ClientError>> toClientError() {
        return keyCloakError -> {
            return error(unknownUnauthorizedError(keyCloakError.getErrorDescription()));
        };
    }



}
