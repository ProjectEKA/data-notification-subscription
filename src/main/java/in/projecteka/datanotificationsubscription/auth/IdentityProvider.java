package in.projecteka.datanotificationsubscription.auth;

import reactor.core.publisher.Mono;

public interface IdentityProvider {
    Mono<String> fetchCertificate();
}
