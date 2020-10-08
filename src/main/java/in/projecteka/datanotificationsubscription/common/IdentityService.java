package in.projecteka.datanotificationsubscription.common;


import in.projecteka.datanotificationsubscription.IDPProperties;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class IdentityService {
    private final HASGatewayClient hasGatewayClient;
    private final IDPProperties idpProperties;

    public Mono<String> authenticateForHASGateway() {
        return hasGatewayClient.
                getToken(idpProperties.getIdpClientId(), idpProperties.getIdpClientSecret())
                .map(session -> String.format("%s %s", "Bearer", session.getAccessToken()));
    }
}