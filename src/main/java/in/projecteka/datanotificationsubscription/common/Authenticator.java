package in.projecteka.datanotificationsubscription.common;

import reactor.core.publisher.Mono;

public interface Authenticator {
    Mono<Caller> verify(String token);
}
