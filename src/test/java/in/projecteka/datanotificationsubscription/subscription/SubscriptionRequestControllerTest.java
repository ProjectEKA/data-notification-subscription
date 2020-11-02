package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.common.Authenticator;
import in.projecteka.datanotificationsubscription.common.Caller;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.string;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class SubscriptionRequestControllerTest {
    @Autowired
    private WebTestClient webClient;

    @MockBean(name = "userAuthenticator")
    private Authenticator authenticator;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldAllowTheApprovalRequestWithMultipleSources() {
        var token = string();
        var session = "{\"accessToken\": \"eyJhbGc\", \"refreshToken\": \"refresh\"}";

//        String approvalRequest = "{" +
//                "\"isApplicableForAllHIPs\":\"true\"," +
//                "\"sources\":[{\"categories\":[\"LINK\"],\"hiTypes\":[\"DiagnosticReport\",\"Prescription\",\"DischargeSummary\",\"OPConsultation\"],\"period\":{\"from\":\"2017-10-08T08:51:11.721\",\"to\":\"2021-10-08T08:51:11.721\"},\"purpose\":{\"code\":\"PATRQT\",\"text\":\"Self Requested\"}}]," +
//                "\"excludeSources\":[{\"categories\":[\"LINK\"],\"hiTypes\":[\"DiagnosticReport\",\"Prescription\",\"DischargeSummary\",\"OPConsultation\"],\"hip\":{\"id\":\"10000005\"},\"period\":{\"from\":\"2017-10-08T08:51:11.721\",\"to\":\"2021-10-08T08:51:11.721\"},\"purpose\":{\"code\":\"PATRQT\",\"text\":\"Self Requested\"}}]" +
//                "}";
        when(authenticator.verify("Bearer " + token)).thenReturn(Mono.just(new Caller(
                "consent-manager-service", true)));

        String approvalRequest = "{" +
                "\"sources\":[" +
                "{\"categories\":[\"LINK\"],\"hiTypes\":[\"DiagnosticReport\",\"Prescription\",\"DischargeSummary\",\"OPConsultation\"],\"hip\":{\"id\":\"10000005\"},\"period\":{\"from\":\"2017-10-08T08:51:11.721\",\"to\":\"2021-10-08T08:51:11.721\"},\"purpose\":{\"code\":\"PATRQT\",\"text\":\"Self Requested\"}}," +
                "{\"categories\":[\"LINK\"],\"hiTypes\":[\"DiagnosticReport\",\"Prescription\",\"DischargeSummary\",\"OPConsultation\"],\"hip\":{\"id\":\"10000006\"},\"period\":{\"from\":\"2017-10-08T08:51:11.721\",\"to\":\"2021-10-08T08:51:11.721\"},\"purpose\":{\"code\":\"PATRQT\",\"text\":\"Self Requested\"}}" +
                "]}";

        String requestId = UUID.randomUUID().toString();
        webClient.post()
                .uri("/subscription-requests/" + requestId + "/approve")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, token)
                .bodyValue(approvalRequest)
                .exchange()
                .expectStatus()
                .is2xxSuccessful();
    }
}