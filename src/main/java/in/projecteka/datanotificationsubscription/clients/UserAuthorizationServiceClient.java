package in.projecteka.datanotificationsubscription.clients;

import in.projecteka.datanotificationsubscription.clients.model.AuthRequestRepresentation;
import in.projecteka.datanotificationsubscription.common.ClientError;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

import static in.projecteka.datanotificationsubscription.common.Constants.CORRELATION_ID;

@AllArgsConstructor
public class UserAuthorizationServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(UserAuthorizationServiceClient.class);
    public static final String INTERNAL_PATH_PATIENT_AUTH_REQUESTS_BY_HIP =
            "/internal/patients/{patient-id}/authorization-requests/{hip-id}";

    private final WebClient webClient;
    private final String authorizationHeader;
    private final Supplier<Mono<String>> tokenGenerator;


    public Mono<AuthRequestRepresentation[]> authRequestsForPatientByHIP(String patientId, String hipId) {
        return tokenGenerator.get()
                .flatMap(authorization ->
                        webClient
                                .get()
                                .uri(uriBuilder -> uriBuilder
                                        .path(INTERNAL_PATH_PATIENT_AUTH_REQUESTS_BY_HIP)
                                        .build(patientId, hipId))
                                .header(authorizationHeader, authorization)
                                .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                                .retrieve()
                                .onStatus(httpStatus -> httpStatus.value() == 401,
                                        clientResponse -> Mono.error(ClientError.unAuthorized()))
                                .onStatus(HttpStatus::isError, clientResponse -> Mono.error(ClientError.networkServiceCallFailed()))
                                .bodyToMono(AuthRequestRepresentation[].class));
    }

}
