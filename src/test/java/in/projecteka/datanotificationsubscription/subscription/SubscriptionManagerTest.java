package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.common.ClientError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static in.projecteka.datanotificationsubscription.common.ClientError.userNotFound;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.subscriptionRequest;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

public class SubscriptionManagerTest {
    @Mock
    private SubscriptionRequestRepository subscriptionRequestRepository;

    @Mock
    private UserServiceClient userServiceClient;

    private SubscriptionRequestService subscriptionRequestService;

    @BeforeEach
    public void setUp() {
        initMocks(this);
        subscriptionRequestService = new SubscriptionRequestService(subscriptionRequestRepository,
                userServiceClient);
    }

    @Test
    void shouldCreateSubscriptionRequest(){
        var subscriptionRequest = subscriptionRequest().build();
        var user = user().build();
        Mockito.when(userServiceClient.userOf(anyString())).thenReturn(Mono.just(user));
        Mockito.when(subscriptionRequestRepository.insert(any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(subscriptionRequestService.subscriptionRequest(subscriptionRequest.getSubscription(),
                subscriptionRequest.getRequestId()))
                .verifyComplete();

        verify(userServiceClient, times(1)).userOf(anyString());
        verify(subscriptionRequestRepository, times(1))
                .insert(any(), any());
    }

    @Test
    void shouldThrowInvalidPatient(){
        var subscriptionRequest = subscriptionRequest().build();
        Mockito.when(userServiceClient.userOf(anyString())).thenReturn(Mono.error(userNotFound()));
        Mockito.when(subscriptionRequestRepository.insert(any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(subscriptionRequestService.subscriptionRequest(subscriptionRequest.getSubscription(),
                subscriptionRequest.getRequestId()))
                .expectErrorMatches(e -> (e instanceof ClientError) &&
                        ((ClientError) e).getHttpStatus() == BAD_REQUEST)
                .verify();
    }

}
