package in.projecteka.datanotificationsubscription.common;

import com.google.common.base.Strings;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;

import in.projecteka.datanotificationsubscription.common.cache.CacheAdapter;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.text.ParseException;


import static in.projecteka.datanotificationsubscription.common.Constants.BLOCK_LIST;
import static in.projecteka.datanotificationsubscription.common.Constants.BLOCK_LIST_FORMAT;
import static java.lang.String.format;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.just;

@AllArgsConstructor
public class ExternalIDPOfflineAuthenticator implements Authenticator {
    private final RSASSAVerifier tokenVerifier;
    private final CacheAdapter<String, String> blockListedTokens;
    private static final Logger logger = LoggerFactory.getLogger(ExternalIDPOfflineAuthenticator.class);

    @Override
    public Mono<Caller> verify(String token) {
        try {
            var parts = token.split(" ");
            if (parts.length != 2) {
                return empty();
            }
            var credentials = parts[1];
            SignedJWT signedJWT = SignedJWT.parse(credentials);
            if (!signedJWT.verify(tokenVerifier)) {
                return empty();
            }
            var healthId = signedJWT.getJWTClaimsSet().getClaim("healthId").toString();
            if (Strings.isNullOrEmpty(healthId)) {
                return empty();
            }
            return blockListedTokens.exists(String.format(BLOCK_LIST_FORMAT, BLOCK_LIST, credentials))
                    .filter(exists -> !exists)
                    .flatMap(uselessFalse -> just(Caller.builder().username(healthId).isServiceAccount(false).build()));
        } catch (ParseException | JOSEException e) {
            logger.error(format("Unauthorized access with token: %s %s", token, e));
            return empty();
        }
    }
}