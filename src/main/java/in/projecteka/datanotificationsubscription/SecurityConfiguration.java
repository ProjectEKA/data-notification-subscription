package in.projecteka.datanotificationsubscription;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import in.projecteka.datanotificationsubscription.common.CMTokenAuthenticator;
import in.projecteka.datanotificationsubscription.common.Caller;
import in.projecteka.datanotificationsubscription.common.GatewayTokenVerifier;
import in.projecteka.datanotificationsubscription.common.cache.CacheAdapter;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static in.projecteka.datanotificationsubscription.common.ClientError.unAuthorized;
import static in.projecteka.datanotificationsubscription.common.Constants.PATH_HEARTBEAT;
import static in.projecteka.datanotificationsubscription.common.Constants.PATH_SUBSCRIPTION_REQUESTS;
import static in.projecteka.datanotificationsubscription.common.Constants.PATH_SUBSCRIPTION_REQUEST_SUBSCRIBE;
import static in.projecteka.datanotificationsubscription.common.Role.GATEWAY;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.of;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.justOrEmpty;


@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {
    private static final List<Map.Entry<String, HttpMethod>> SERVICE_ONLY_URLS = new ArrayList<>();
    private static final List<String> INTERNAL_SERVICE_URLS = new ArrayList<>();
    private static final String[] GATEWAY_APIS = new String[]{
            "/hello"
    };

    static {
//        SERVICE_ONLY_URLS.add(Map.entry(PATH_SUBSCRIPTION_REQUESTS, HttpMethod.POST));
        INTERNAL_SERVICE_URLS.add("/internal/**");
    }

    private static final String[] ALLOWED_LIST_URLS = new String[]{"/**.json",
            "/ValueSet/**.json",
            PATH_HEARTBEAT,
            PATH_SUBSCRIPTION_REQUEST_SUBSCRIBE,
            "/**.html",
            "/**.js",
            "/**.yaml",
            "/**.css",
            "/**.png"};

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity httpSecurity,
            ReactiveAuthenticationManager authenticationManager,
            ServerSecurityContextRepository securityContextRepository) {

        httpSecurity.authorizeExchange().pathMatchers(ALLOWED_LIST_URLS).permitAll();
        httpSecurity.httpBasic().disable().formLogin().disable().csrf().disable().logout().disable();
        httpSecurity
                .authorizeExchange()
                .pathMatchers(GATEWAY_APIS).hasAnyRole(GATEWAY.name())
                .pathMatchers("/**")
                .authenticated();
        return httpSecurity
                .authenticationManager(authenticationManager)
                .securityContextRepository(securityContextRepository)
                .build();
    }

    @Bean
    public ReactiveAuthenticationManager authenticationManager() {
        return new AuthenticationManager();
    }

    @Bean({"jwtProcessor"})
    public ConfigurableJWTProcessor<com.nimbusds.jose.proc.SecurityContext> getJWTProcessor() {
        return new DefaultJWTProcessor<>();
    }

    @Bean("internalServiceAuthenticator")
    public CMTokenAuthenticator internalServiceAuthenticator(
            @Qualifier("identityServiceJWKSet") JWKSet jwkSet,
            CacheAdapter<String, String> blockListedTokens,
            ConfigurableJWTProcessor<com.nimbusds.jose.proc.SecurityContext> jwtProcessor) {
        return new CMTokenAuthenticator(jwkSet, blockListedTokens, jwtProcessor);
    }

    @Bean
    public SecurityContextRepository contextRepository(
            GatewayTokenVerifier gatewayTokenVerifier,
            @Value("${subscriptionmanager.authorization.header}") String authorizationHeader,
            @Qualifier("internalServiceAuthenticator") CMTokenAuthenticator cmTokenAuthenticator) {
        return new SecurityContextRepository(
                gatewayTokenVerifier,
                authorizationHeader,
                cmTokenAuthenticator);
    }

    @AllArgsConstructor
    private static class SecurityContextRepository implements ServerSecurityContextRepository {
        private final GatewayTokenVerifier gatewayTokenVerifier;
        private final String authorizationHeader;
        private final CMTokenAuthenticator cmTokenAuthenticator;

        @Override
        public Mono<Void> save(ServerWebExchange exchange, org.springframework.security.core.context.SecurityContext context) {
            throw new UnsupportedOperationException("No need right now!");
        }

        @Override
        public Mono<org.springframework.security.core.context.SecurityContext> load(ServerWebExchange exchange) {
            var requestPath = exchange.getRequest().getPath().toString();
            var requestMethod = exchange.getRequest().getMethod();

            if (isAllowedList(requestPath)) {
                return empty();
            }

            if (isGatewayAuthenticationOnly(requestPath, requestMethod)) {
                return checkGateway(exchange.getRequest().getHeaders().getFirst(AUTHORIZATION))
                        .switchIfEmpty(error(unAuthorized()));
            }

            var token = exchange.getRequest().getHeaders().getFirst(authorizationHeader);

            if (isEmpty(token)) {
                return error(unAuthorized());
            }

            token = addBearerIfNotPresent(token);
            return checkKeycloak(token)
                    .switchIfEmpty(error(unAuthorized()));
        }


        private String addBearerIfNotPresent(String token) {
            if (!StringUtils.startsWithIgnoreCase(token, "Bearer")) {
                return String.format("%s %s", "Bearer", token);
            }
            return token;
        }

        private Mono<org.springframework.security.core.context.SecurityContext> checkKeycloak(String token) {
            return justOrEmpty(token)
                    .flatMap(cmTokenAuthenticator::verify)
                    .filter(Caller::isServiceAccount)
                    .map(caller -> new UsernamePasswordAuthenticationToken(
                            caller,
                            token,
                            new ArrayList<>()))
                    .map(SecurityContextImpl::new);
        }

        private Mono<org.springframework.security.core.context.SecurityContext> checkGateway(String token) {
            return justOrEmpty(token)
                    .flatMap(gatewayTokenVerifier::verify)
                    .map(serviceCaller -> {
                        var authorities = serviceCaller.getRoles()
                                .stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name().toUpperCase()))
                                .collect(toList());
                        return new UsernamePasswordAuthenticationToken(serviceCaller, token, authorities);
                    })
                    .map(SecurityContextImpl::new);
        }

        private boolean isAllowedList(String url) {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            return of(ALLOWED_LIST_URLS)
                    .anyMatch(pattern -> antPathMatcher.match(pattern, url));
        }

        private boolean isInternalService(String requestPath) {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            return INTERNAL_SERVICE_URLS.stream().anyMatch(pattern -> antPathMatcher.match(pattern, requestPath));
        }

        private boolean isGatewayAuthenticationOnly(String url, HttpMethod method) {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            return SERVICE_ONLY_URLS.stream()
                    .anyMatch(pattern ->
                            antPathMatcher.match(pattern.getKey(), url) && pattern.getValue().equals(method));
        }

        private boolean isEmpty(String authToken) {
            return authToken == null || authToken.trim().equals("");
        }
    }

    private static class AuthenticationManager implements ReactiveAuthenticationManager {
        @Override
        public Mono<Authentication> authenticate(Authentication authentication) {
            var token = authentication.getCredentials().toString();
            var auth = new UsernamePasswordAuthenticationToken(
                    authentication.getPrincipal(),
                    token,
                    new ArrayList<>());
            return Mono.just(auth);
        }
    }
}