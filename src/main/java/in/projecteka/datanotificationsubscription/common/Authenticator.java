package in.projecteka.datanotificationsubscription.common;

import in.projecteka.datanotificationsubscription.common.model.Caller;
import reactor.core.publisher.Mono;

public interface Authenticator {
    Mono<Caller> verify(String token);
}
