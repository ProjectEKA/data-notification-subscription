package in.projecteka.datanotificationsubscription.common;


import in.projecteka.datanotificationsubscription.common.model.AccountServiceProperties;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class IdentityService {
    private final HASGatewayClient hasGatewayClient;
    private final AccountServiceProperties accountServiceProperties;

    public Mono<String> authenticateForHASGateway() {
        return hasGatewayClient.
                getToken(accountServiceProperties.getClientId(), accountServiceProperties.getClientSecret())
                .map(session -> String.format("%s %s", "Bearer", session.getAccessToken()));
    }
}