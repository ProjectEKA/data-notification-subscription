package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.ConceptValidator;
import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.common.ClientError;
import in.projecteka.datanotificationsubscription.common.GatewayServiceClient;
import in.projecteka.datanotificationsubscription.subscription.model.HiuDetail;
import in.projecteka.datanotificationsubscription.subscription.model.PatientDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionOnInitRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static in.projecteka.datanotificationsubscription.common.ClientError.userNotFound;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.subscriptionDetail;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static reactor.core.publisher.Mono.error;

class SubscriptionRequestServiceTest {
    @Mock
    private SubscriptionRequestRepository subscriptionRequestRepository;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private GatewayServiceClient gatewayServiceClient;
    @Mock
    private ConceptValidator conceptValidator;
    @Mock
    private SubscriptionProperties subscriptionProperties;

    private SubscriptionRequestService subscriptionRequestService;

    @BeforeEach
    void setUp() {
        initMocks(this);
        subscriptionRequestService = new SubscriptionRequestService(subscriptionRequestRepository, userServiceClient,
                gatewayServiceClient, conceptValidator, subscriptionProperties);
    }

    @Test
    void shouldSaveSubscriptionRequestAndNotifySuccess() {
        ArgumentCaptor<SubscriptionOnInitRequest> captor = ArgumentCaptor.forClass(SubscriptionOnInitRequest.class);

        String healthId = "test@ncg";
        SubscriptionDetail subscriptionDetail = subscriptionDetail()
                .patient(PatientDetail.builder().id(healthId).build())
                .hiu(HiuDetail.builder().id("hiu-id").build())
                .build();
        UUID gatewayRequestId = UUID.randomUUID();

        when(userServiceClient.userOf(anyString())).thenReturn(Mono.just(user().build()));
        when(subscriptionRequestRepository.insert(any(SubscriptionDetail.class), any(UUID.class))).thenReturn(Mono.empty());
        when(gatewayServiceClient.subscriptionRequestOnInit(any(SubscriptionOnInitRequest.class), anyString())).thenReturn(Mono.empty());

        Mono<Void> result = subscriptionRequestService.subscriptionRequest(subscriptionDetail, gatewayRequestId);
        StepVerifier.create(result).expectComplete().verify();

        verify(userServiceClient, times(1)).userOf(healthId);
        verify(subscriptionRequestRepository, times(1)).insert(eq(subscriptionDetail), any(UUID.class));
        verify(gatewayServiceClient, times(1)).subscriptionRequestOnInit(captor.capture(), eq("hiu-id"));

        SubscriptionOnInitRequest captorValue = captor.getValue();
        assertThat(captorValue.getError()).isNull();
        assertThat(captorValue.getSubscriptionRequest().getId()).isNotNull();
    }

    @Test
    void shouldNotSaveAndNotifyWhenPatientIsNotFound() {
        ArgumentCaptor<SubscriptionOnInitRequest> captor = ArgumentCaptor.forClass(SubscriptionOnInitRequest.class);

        String healthId = "test@ncg";
        SubscriptionDetail subscriptionDetail = subscriptionDetail()
                .patient(PatientDetail.builder().id(healthId).build())
                .hiu(HiuDetail.builder().id("hiu-id").build())
                .build();
        UUID gatewayRequestId = UUID.randomUUID();

        when(userServiceClient.userOf(anyString())).thenReturn(error(userNotFound()));
        when(subscriptionRequestRepository.insert(any(SubscriptionDetail.class), any(UUID.class))).thenReturn(Mono.empty());
        when(gatewayServiceClient.subscriptionRequestOnInit(any(SubscriptionOnInitRequest.class), anyString())).thenReturn(Mono.empty());

        Mono<Void> result = subscriptionRequestService.subscriptionRequest(subscriptionDetail, gatewayRequestId);
        StepVerifier.create(result)
                .expectErrorMatches(e -> (e instanceof ClientError) && ((ClientError) e).getHttpStatus() == BAD_REQUEST)
                .verify();

        verify(userServiceClient, times(1)).userOf(healthId);
        verify(subscriptionRequestRepository, never()).insert(any(), any());
        verify(gatewayServiceClient, never()).subscriptionRequestOnInit(any(), any());
    }
}