package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.clients.model.User;
import in.projecteka.datanotificationsubscription.subscription.model.ListResult;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.function.Consumer;

import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.user;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {
    @Mock
    UserServiceClient userServiceClient;
    @Mock
    SubscriptionRepository subscriptionRepository;

    SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        subscriptionService = new SubscriptionService(userServiceClient, subscriptionRepository);
    }

    @Test
    void shouldFindSubscriptionsForHIUAndPatient() {
        String healthIdNumber = "1234";
        User user = user().healthIdNumber(healthIdNumber).build();
        String hiuId = "100010";
        String userId = "test@ncg";
        ListResult<List<SubscriptionResponse>> listResult = new ListResult<>(asList(new SubscriptionResponse()), 10);

        when(userServiceClient.userOf(anyString())).thenReturn(Mono.just(user));
        when(subscriptionRepository.getSubscriptionsFor(anyString(), anyString(), anyInt(), anyInt())).thenReturn(Mono.just(listResult));

        Mono<ListResult<List<SubscriptionResponse>>> subscriptions = subscriptionService.getSubscriptionsFor(userId, hiuId, 10, 5);
        StepVerifier.create(subscriptions)
                .assertNext(result -> assertThat(result).isEqualTo(listResult))
                .verifyComplete();

        verify(userServiceClient, times(1)).userOf(userId);
        verify(subscriptionRepository, times(1)).getSubscriptionsFor(healthIdNumber, hiuId, 10, 5);
    }
}