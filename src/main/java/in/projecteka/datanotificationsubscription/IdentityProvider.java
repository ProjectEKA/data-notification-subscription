package in.projecteka.datanotificationsubscription;

import reactor.core.publisher.Mono;

public interface IdentityProvider {
    Mono<String> fetchCertificate();
}
