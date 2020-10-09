package in.projecteka.datanotificationsubscription.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class CMIdentityProviderTest {
    private @Captor
    ArgumentCaptor<ClientRequest> captor;

    @Mock
    private ExchangeFunction exchangeFunction;
    private CMIdentityProvider cmIdentityProvider;
    private String idpCertPath;

    @BeforeEach
    void setUp() {
        idpCertPath = "http://localhost:8000/idpauth/";
        IDPProperties idpProperties = IDPProperties
                .builder()
                .idpCertPath(idpCertPath)
                .build();

        MockitoAnnotations.initMocks(this);
        WebClient.Builder webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction);
        cmIdentityProvider = new CMIdentityProvider(webClientBuilder, idpProperties);
    }

    @Test
    void shouldFetchTheFirstKeyFromCM() {
        when(exchangeFunction.exchange(captor.capture())).thenReturn(
                Mono.just(
                        ClientResponse
                                .create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body("{\"keys\":[{\"publicKey\":\"first key\",\"startDate\":\"2020-01-01:00:00:00Z\"},{\"publicKey\":\"second key\",\"startDate\":\"2020-01-01:00:00:00Z\"}]}")
                                .build()));

        Mono<String> certificate = cmIdentityProvider.fetchCertificate();

        StepVerifier.create(certificate).assertNext(
                cert -> assertThat(cert).isEqualTo("first key")
        ).verifyComplete();

        assertThat(captor.getValue().url().toString()).isEqualTo(idpCertPath);
    }
}