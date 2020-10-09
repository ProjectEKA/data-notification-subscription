package in.projecteka.datanotificationsubscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;

public class CMIdentityProvider implements IdentityProvider{
    private final WebClient webClient;
    private final IDPProperties idpProperties;
    private static final Logger logger = LoggerFactory.getLogger(CMIdentityProvider.class);

    public CMIdentityProvider(WebClient.Builder builder, IDPProperties idpProperties) {
        this.webClient = builder.build();
        this.idpProperties = idpProperties;
    }

    @Override
    public Mono<String> fetchCertificate() {
        return webClient
                .get()
                .uri(idpProperties.getIdpCertPath())
                .retrieve()
                .bodyToMono(CMCert.class)
                .doOnSubscribe(subscription -> logger.info("About to fetch certificate"))
                .map(this::getFirstKey);
    }

    private String getFirstKey(CMCert cmCert) {
        List<HashMap<String, String>> keys = cmCert.getKeys();
        if (CollectionUtils.isEmpty(keys)) return null;
        return keys.get(0).get("publicKey");
    }
}

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
class CMCert{
    private List<HashMap<String, String>> keys;
}
