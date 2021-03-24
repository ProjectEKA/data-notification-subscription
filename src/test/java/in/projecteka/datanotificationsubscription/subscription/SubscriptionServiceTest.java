package in.projecteka.datanotificationsubscription.subscription;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.clients.model.User;
import in.projecteka.datanotificationsubscription.common.ClientError;
import in.projecteka.datanotificationsubscription.common.GatewayServiceClient;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionRequestNotifyRequest;
import in.projecteka.datanotificationsubscription.subscription.model.HipDetail;
import in.projecteka.datanotificationsubscription.subscription.model.ListResult;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionResponse;
import in.projecteka.datanotificationsubscription.subscription.model.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static in.projecteka.datanotificationsubscription.common.ErrorCode.SUBSCRIPTION_REQUEST_NOT_FOUND;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.user;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {
    @Mock
    UserServiceClient userServiceClient;
    @Mock
    SubscriptionRepository subscriptionRepository;

    @Mock
    GatewayServiceClient gatewayServiceClient;

    SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        subscriptionService = new SubscriptionService(userServiceClient, gatewayServiceClient, subscriptionRepository);
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

    @Test
    void shouldFetchSubscriptionDetailsFromSubscriptionId(){
        var subscriptionId = UUID.randomUUID().toString();
        var subscriptionResponse = TestBuilder.subscriptionResponseBuilder().build();

        when(subscriptionRepository.getSubscriptionDetailsForID(subscriptionId, true)).thenReturn(Mono.just(subscriptionResponse));

        StepVerifier.create(subscriptionService.getSubscriptionDetailsForID(subscriptionId))
                .expectNext(subscriptionResponse)
                .verifyComplete();

        verify(subscriptionRepository, times(1)).getSubscriptionDetailsForID(subscriptionId, true);
    }

    @Test
    void shouldThrowErrorWhenGivenSubscriptionIdIsInvalid(){
        var subscriptionId = UUID.randomUUID().toString();

        when(subscriptionRepository.getSubscriptionDetailsForID(subscriptionId, true)).thenReturn(Mono.empty());

        StepVerifier.create(subscriptionService.getSubscriptionDetailsForID(subscriptionId))
                .expectErrorMatches(e -> e instanceof ClientError && ((ClientError) e).getErrorCode().equals(SUBSCRIPTION_REQUEST_NOT_FOUND))
                .verify();

        verify(subscriptionRepository, times(1)).getSubscriptionDetailsForID(subscriptionId, true);
    }

    @Test
    void shouldEditSubscriptionForNotApplicableToAllHIPsWhenNewHIPsAreIncluded(){
        var subscriptionId = UUID.randomUUID().toString();

        var newHipToInclude = TestBuilder.grantedSubscription()
                .hip(HipDetail.builder().id("hip_2").build())
                .build();

        var subscriptionEditRequest = TestBuilder.subscriptionEditAndApprovalRequestBuilder()
                .isApplicableForAllHIPs(false)
                .includedSources(List.of(newHipToInclude))
                .build();

        var existingIncludedSource = TestBuilder.subscriptionSourceBuilder()
                .hip(HipDetail.builder().id("hip_1").build())
                .build();

        var subscriptionResponse = TestBuilder.subscriptionResponseBuilder()
                .subscriptionId(UUID.fromString(subscriptionId))
                .subscriptionRequestId(UUID.randomUUID().toString())
                .excludedSources(List.of())
                .includedSources(List.of(existingIncludedSource))
                .build();

        //Expectation
        var expectedIncludedSources = subscriptionEditRequest.getIncludedSources();
        var expectedHipsToBeDeactivated = Sets.newHashSet("hip_1");

        var hiuNotificationRequestCaptor = ArgumentCaptor.forClass(HIUSubscriptionRequestNotifyRequest.class);

        when(subscriptionRepository.getSubscriptionDetailsForID(subscriptionId, false)).thenReturn(Mono.just(subscriptionResponse));
        when(subscriptionRepository.editSubscriptionNotApplicableForAllHIPs(subscriptionId, expectedIncludedSources, expectedHipsToBeDeactivated)).thenReturn(Mono.empty());
        when(gatewayServiceClient.subscriptionRequestNotify(hiuNotificationRequestCaptor.capture(), eq(subscriptionResponse.getRequester().getId()))).thenReturn(Mono.empty());

        StepVerifier.create(subscriptionService.editSubscription(subscriptionId, subscriptionEditRequest))
                .verifyComplete();


        var expectedHiuNotificationRequest = hiuNotificationRequestCaptor.getValue();

        assertEquals("GRANTED",expectedHiuNotificationRequest.getNotification().getStatus());
        assertEquals(UUID.fromString(subscriptionResponse.getSubscriptionRequestId()),expectedHiuNotificationRequest.getNotification().getSubscriptionRequestId());
        assertEquals(UUID.fromString(subscriptionId), expectedHiuNotificationRequest.getNotification().getSubscription().getId());
        assertEquals(subscriptionResponse.getRequester().getId(), expectedHiuNotificationRequest.getNotification().getSubscription().getHiu().getId());
        assertEquals(subscriptionResponse.getRequester().getName(), expectedHiuNotificationRequest.getNotification().getSubscription().getHiu().getName());
        assertEquals(subscriptionResponse.getPatient(), expectedHiuNotificationRequest.getNotification().getSubscription().getPatient());

        var expectedIncludedSource = expectedHiuNotificationRequest.getNotification().getSubscription().getSources().get(0);

        assertEquals(newHipToInclude.getCategories(), expectedIncludedSource.getCategories());
        assertEquals(newHipToInclude.getHip(), expectedIncludedSource.getHip());
        assertEquals(newHipToInclude.getPeriod(), expectedIncludedSource.getPeriod());

        verify(subscriptionRepository, times(1)).getSubscriptionDetailsForID(subscriptionId, false);
        verify(subscriptionRepository, times(1)).editSubscriptionNotApplicableForAllHIPs(subscriptionId, expectedIncludedSources, expectedHipsToBeDeactivated);
        verify(gatewayServiceClient, times(1)).subscriptionRequestNotify(any(HIUSubscriptionRequestNotifyRequest.class), eq(subscriptionResponse.getRequester().getId()));
    }

    @Test
    void shouldEditSubscriptionForApplicableToAllHIPsWhenNewHipsAreExcluded(){
        var subscriptionId = UUID.randomUUID().toString();

        var newHipToInclude = TestBuilder.grantedSubscription()
                .hip(HipDetail.builder().id(null).build())
                .build();

        var newHipToExclude = TestBuilder.grantedSubscription()
                .hip(HipDetail.builder().id("hip_2").build())
                .build();

        var subscriptionEditRequest = TestBuilder.subscriptionEditAndApprovalRequestBuilder()
                .isApplicableForAllHIPs(true)
                .includedSources(List.of(newHipToInclude))
                .excludedSources(List.of(newHipToExclude))
                .build();

        var existingIncludedSource = TestBuilder.subscriptionSourceBuilder()
                .hip(HipDetail.builder().id("hip_1").build())
                .build();

        var subscriptionResponse = TestBuilder.subscriptionResponseBuilder()
                .subscriptionId(UUID.fromString(subscriptionId))
                .subscriptionRequestId(UUID.randomUUID().toString())
                .excludedSources(List.of())
                .includedSources(List.of(existingIncludedSource))
                .build();

        //Expectation
        var expectedExcludedSources = subscriptionEditRequest.getExcludedSources();
        var expectedIncludedSource = subscriptionEditRequest.getIncludedSources().get(0);
        var expectedHipsToBeDeactivated = Sets.newHashSet("hip_1");

        var hiuNotificationRequestCaptor = ArgumentCaptor.forClass(HIUSubscriptionRequestNotifyRequest.class);

        when(subscriptionRepository.getSubscriptionDetailsForID(subscriptionId, false)).thenReturn(Mono.just(subscriptionResponse));
        when(subscriptionRepository.editSubscriptionApplicableForAllHIPs(subscriptionId, expectedHipsToBeDeactivated, expectedIncludedSource,  expectedExcludedSources)).thenReturn(Mono.empty());
        when(gatewayServiceClient.subscriptionRequestNotify(hiuNotificationRequestCaptor.capture(), eq(subscriptionResponse.getRequester().getId()))).thenReturn(Mono.empty());

        StepVerifier.create(subscriptionService.editSubscription(subscriptionId, subscriptionEditRequest))
                .verifyComplete();

        var expectedHiuNotificationRequest = hiuNotificationRequestCaptor.getValue();

        assertEquals("GRANTED",expectedHiuNotificationRequest.getNotification().getStatus());
        assertEquals(UUID.fromString(subscriptionResponse.getSubscriptionRequestId()),expectedHiuNotificationRequest.getNotification().getSubscriptionRequestId());
        assertEquals(UUID.fromString(subscriptionId), expectedHiuNotificationRequest.getNotification().getSubscription().getId());
        assertEquals(subscriptionResponse.getRequester().getId(), expectedHiuNotificationRequest.getNotification().getSubscription().getHiu().getId());
        assertEquals(subscriptionResponse.getRequester().getName(), expectedHiuNotificationRequest.getNotification().getSubscription().getHiu().getName());
        assertEquals(subscriptionResponse.getPatient(), expectedHiuNotificationRequest.getNotification().getSubscription().getPatient());

        var expectedSourceToSendToHIU = expectedHiuNotificationRequest.getNotification().getSubscription().getSources().get(0);

        assertEquals(newHipToInclude.getCategories(), expectedSourceToSendToHIU.getCategories());
        assertEquals(newHipToInclude.getHip(), expectedSourceToSendToHIU.getHip());
        assertEquals(newHipToInclude.getPeriod(), expectedSourceToSendToHIU.getPeriod());

        verify(subscriptionRepository, times(1)).getSubscriptionDetailsForID(subscriptionId, false);
        verify(subscriptionRepository, times(1))
                .editSubscriptionApplicableForAllHIPs(subscriptionId, expectedHipsToBeDeactivated, expectedIncludedSource,  expectedExcludedSources);
        verify(gatewayServiceClient, times(1)).subscriptionRequestNotify(any(HIUSubscriptionRequestNotifyRequest.class), eq(subscriptionResponse.getRequester().getId()));
    }
}
