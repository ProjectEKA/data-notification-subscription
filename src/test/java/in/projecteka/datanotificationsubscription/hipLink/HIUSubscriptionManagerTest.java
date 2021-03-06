package in.projecteka.datanotificationsubscription.hipLink;

import in.projecteka.datanotificationsubscription.HIUSubscriptionManager;
import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.clients.model.User;
import in.projecteka.datanotificationsubscription.common.GatewayServiceClient;
import in.projecteka.datanotificationsubscription.subscription.Subscription;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionRequestRepository;
import in.projecteka.datanotificationsubscription.subscription.model.Category;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionNotificationRequest;
import in.projecteka.datanotificationsubscription.subscription.model.HipDetail;
import in.projecteka.datanotificationsubscription.subscription.model.HiuDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.newCCLinkEvent;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.patientCareContext;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.subscription;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.user;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

class HIUSubscriptionManagerTest {
    @Mock
    private SubscriptionRequestRepository subscriptionRequestRepository;
    @Mock
    private GatewayServiceClient gatewayServiceClient;
    @Mock
    private UserServiceClient userServiceClient;

    HIUSubscriptionManager hiuSubscriptionManager;

    @BeforeEach
    void setUp() {
        initMocks(this);
        hiuSubscriptionManager = new HIUSubscriptionManager(subscriptionRequestRepository, gatewayServiceClient, userServiceClient);
    }

    @Test
    void shouldNotifyAllSubscribersWithMatchingHIPsForGivenLinkEvent() {
        NewCCLinkEvent linkEvent = newCCLinkEvent()
                .careContexts(asList(patientCareContext().build()))
                .build();

        HipDetail hipDetail = HipDetail.builder().id(linkEvent.getHipId()).build();
        Subscription subscription1 = subscription().hiu(HiuDetail.builder().id("first").build()).hip(hipDetail).excluded(false).build();
        Subscription subscription2 = subscription().hiu(HiuDetail.builder().id("second").build()).hip(hipDetail).excluded(false).build();
        List<Subscription> subscriptions = asList(subscription1, subscription2);

        User user = user().build();
        when(userServiceClient.userOf(anyString())).thenReturn(Mono.just(user));
        when(subscriptionRequestRepository.findLinkSubscriptionsFor(anyString(), anyString())).thenReturn(Mono.just(subscriptions));
        when(gatewayServiceClient.notifyForSubscription(any(HIUSubscriptionNotificationRequest.class), anyString())).thenReturn(Mono.empty());

        Flux<Void> notifications = hiuSubscriptionManager.notifySubscribers(linkEvent);
        StepVerifier.create(notifications)
                .expectNext()
                .verifyComplete();

        ArgumentCaptor<HIUSubscriptionNotificationRequest> notificationRequestCaptor = ArgumentCaptor.forClass(HIUSubscriptionNotificationRequest.class);
        ArgumentCaptor<String> hiuIdCaptor = ArgumentCaptor.forClass(String.class);

        verify(userServiceClient, times(1)).userOf(linkEvent.getHealthNumber());
        verify(subscriptionRequestRepository, times(1)).findLinkSubscriptionsFor(linkEvent.getHealthNumber(), linkEvent.getHipId());
        verify(gatewayServiceClient, times(2)).notifyForSubscription(notificationRequestCaptor.capture(), hiuIdCaptor.capture());

        List<HIUSubscriptionNotificationRequest> notificationRequests = notificationRequestCaptor.getAllValues();
        List<String> hiuIds = hiuIdCaptor.getAllValues();

        assertThat(notificationRequests.get(0).getEvent().getCategory()).isEqualTo(Category.LINK);
        assertThat(notificationRequests.get(0).getEvent().getSubscriptionId()).isEqualTo(subscription1.getId());
        assertThat(notificationRequests.get(0).getEvent().getContent().getHip().getId()).isEqualTo(linkEvent.getHipId());
        assertThat(notificationRequests.get(0).getEvent().getContent().getContext().get(0).getCareContext()).isEqualTo(linkEvent.getCareContexts().get(0));
        assertThat(notificationRequests.get(0).getEvent().getContent().getContext().get(0).getHiTypes()).isNullOrEmpty();
        assertThat(notificationRequests.get(0).getEvent().getContent().getPatient().getId()).isEqualTo(user.getIdentifier());
        assertThat(hiuIds.get(0)).isEqualTo(subscription1.getHiu().getId());

        assertThat(notificationRequests.get(1).getEvent().getCategory()).isEqualTo(Category.LINK);
        assertThat(notificationRequests.get(1).getEvent().getSubscriptionId()).isEqualTo(subscription2.getId());
        assertThat(notificationRequests.get(1).getEvent().getContent().getHip().getId()).isEqualTo(linkEvent.getHipId());
        assertThat(notificationRequests.get(1).getEvent().getContent().getContext().get(0).getCareContext()).isEqualTo(linkEvent.getCareContexts().get(0));
        assertThat(notificationRequests.get(1).getEvent().getContent().getContext().get(0).getHiTypes()).isNullOrEmpty();
        assertThat(notificationRequests.get(1).getEvent().getContent().getPatient().getId()).isEqualTo(user.getIdentifier());
        assertThat(hiuIds.get(1)).isEqualTo(subscription2.getHiu().getId());
    }

    @Test
    void shouldNotifyOnlyOnceWhenThereAreMutlipleSubscriptionForSameHIU() {
        NewCCLinkEvent linkEvent = newCCLinkEvent()
                .careContexts(asList(patientCareContext().build()))
                .build();

        HipDetail hipDetail = HipDetail.builder().id(linkEvent.getHipId()).build();
        Subscription subscription1 = subscription().hiu(HiuDetail.builder().id("first").build()).hip(hipDetail).excluded(false).build();
        Subscription subscription2 = subscription().hiu(HiuDetail.builder().id("first").build()).hip(hipDetail).excluded(false).build();
        List<Subscription> subscriptions = asList(subscription1, subscription2);

        User user = user().build();
        when(userServiceClient.userOf(anyString())).thenReturn(Mono.just(user));
        when(subscriptionRequestRepository.findLinkSubscriptionsFor(anyString(), anyString())).thenReturn(Mono.just(subscriptions));
        when(gatewayServiceClient.notifyForSubscription(any(HIUSubscriptionNotificationRequest.class), anyString())).thenReturn(Mono.empty());

        Flux<Void> notifications = hiuSubscriptionManager.notifySubscribers(linkEvent);
        StepVerifier.create(notifications)
                .expectNext()
                .verifyComplete();

        ArgumentCaptor<HIUSubscriptionNotificationRequest> notificationRequestCaptor = ArgumentCaptor.forClass(HIUSubscriptionNotificationRequest.class);
        ArgumentCaptor<String> hiuIdCaptor = ArgumentCaptor.forClass(String.class);

        verify(userServiceClient, times(1)).userOf(linkEvent.getHealthNumber());
        verify(subscriptionRequestRepository, times(1)).findLinkSubscriptionsFor(linkEvent.getHealthNumber(), linkEvent.getHipId());
        verify(gatewayServiceClient, times(1)).notifyForSubscription(notificationRequestCaptor.capture(), hiuIdCaptor.capture());

        HIUSubscriptionNotificationRequest notificationRequest = notificationRequestCaptor.getValue();
        String hiuId = hiuIdCaptor.getValue();

        assertThat(notificationRequest.getEvent().getCategory()).isEqualTo(Category.LINK);
        assertThat(notificationRequest.getEvent().getSubscriptionId()).isEqualTo(subscription1.getId());
        assertThat(notificationRequest.getEvent().getContent().getHip().getId()).isEqualTo(linkEvent.getHipId());
        assertThat(notificationRequest.getEvent().getContent().getContext().get(0).getCareContext()).isEqualTo(linkEvent.getCareContexts().get(0));
        assertThat(notificationRequest.getEvent().getContent().getContext().get(0).getHiTypes()).isNullOrEmpty();
        assertThat(notificationRequest.getEvent().getContent().getPatient().getId()).isEqualTo(user.getIdentifier());
        assertThat(hiuId).isEqualTo(subscription1.getHiu().getId());
    }

    @Test
    void shouldNotNotifySubscribersWhichAreExcludedForCurrentHIP() {
        NewCCLinkEvent linkEvent = newCCLinkEvent()
                .careContexts(asList(patientCareContext().build()))
                .build();

        HipDetail hipDetail = HipDetail.builder().id(linkEvent.getHipId()).build();
        Subscription subscription1 = subscription().hiu(HiuDetail.builder().id("first").build()).hip(hipDetail).excluded(true).build();
        Subscription subscription2 = subscription().hiu(HiuDetail.builder().id("second").build()).hip(hipDetail).excluded(false).build();
        List<Subscription> subscriptions = asList(subscription1, subscription2);

        User user = user().build();
        when(userServiceClient.userOf(anyString())).thenReturn(Mono.just(user));
        when(subscriptionRequestRepository.findLinkSubscriptionsFor(anyString(), anyString())).thenReturn(Mono.just(subscriptions));
        when(gatewayServiceClient.notifyForSubscription(any(HIUSubscriptionNotificationRequest.class), anyString())).thenReturn(Mono.empty());

        Flux<Void> notifications = hiuSubscriptionManager.notifySubscribers(linkEvent);
        StepVerifier.create(notifications)
                .expectNext()
                .verifyComplete();

        ArgumentCaptor<HIUSubscriptionNotificationRequest> notificationRequestCaptor = ArgumentCaptor.forClass(HIUSubscriptionNotificationRequest.class);
        ArgumentCaptor<String> hiuIdCaptor = ArgumentCaptor.forClass(String.class);

        verify(userServiceClient, times(1)).userOf(linkEvent.getHealthNumber());
        verify(subscriptionRequestRepository, times(1)).findLinkSubscriptionsFor(linkEvent.getHealthNumber(), linkEvent.getHipId());
        verify(gatewayServiceClient, times(1)).notifyForSubscription(notificationRequestCaptor.capture(), hiuIdCaptor.capture());

        HIUSubscriptionNotificationRequest notificationRequest = notificationRequestCaptor.getValue();
        String hiuId = hiuIdCaptor.getValue();

        assertThat(notificationRequest.getEvent().getCategory()).isEqualTo(Category.LINK);
        assertThat(notificationRequest.getEvent().getSubscriptionId()).isEqualTo(subscription2.getId());
        assertThat(notificationRequest.getEvent().getContent().getHip().getId()).isEqualTo(linkEvent.getHipId());
        assertThat(notificationRequest.getEvent().getContent().getContext().get(0).getCareContext()).isEqualTo(linkEvent.getCareContexts().get(0));
        assertThat(notificationRequest.getEvent().getContent().getContext().get(0).getHiTypes()).isNullOrEmpty();
        assertThat(notificationRequest.getEvent().getContent().getPatient().getId()).isEqualTo(user.getIdentifier());
        assertThat(hiuId).isEqualTo(subscription2.getHiu().getId());
    }

    @Test
    void shouldNotNotifyTheSubscriberWhichHasGeneratedTheLink() {
        //Scenario: A locker has subscribed to ALL HIPs and when they add-context they should not be notified back
        NewCCLinkEvent linkEvent = newCCLinkEvent()
                .hipId("first")
                .careContexts(asList(patientCareContext().build()))
                .build();

        HipDetail hipDetail = HipDetail.builder().id(linkEvent.getHipId()).build();
        Subscription subscription1 = subscription().hiu(HiuDetail.builder().id("first").build()).hip(hipDetail).excluded(false).build();
        Subscription subscription2 = subscription().hiu(HiuDetail.builder().id("second").build()).hip(hipDetail).excluded(false).build();
        List<Subscription> subscriptions = asList(subscription1, subscription2);

        User user = user().build();
        when(userServiceClient.userOf(anyString())).thenReturn(Mono.just(user));
        when(subscriptionRequestRepository.findLinkSubscriptionsFor(anyString(), anyString())).thenReturn(Mono.just(subscriptions));
        when(gatewayServiceClient.notifyForSubscription(any(HIUSubscriptionNotificationRequest.class), anyString())).thenReturn(Mono.empty());

        Flux<Void> notifications = hiuSubscriptionManager.notifySubscribers(linkEvent);
        StepVerifier.create(notifications)
                .expectNext()
                .verifyComplete();

        ArgumentCaptor<HIUSubscriptionNotificationRequest> notificationRequestCaptor = ArgumentCaptor.forClass(HIUSubscriptionNotificationRequest.class);
        ArgumentCaptor<String> hiuIdCaptor = ArgumentCaptor.forClass(String.class);

        verify(userServiceClient, times(1)).userOf(linkEvent.getHealthNumber());
        verify(subscriptionRequestRepository, times(1)).findLinkSubscriptionsFor(linkEvent.getHealthNumber(), linkEvent.getHipId());
        verify(gatewayServiceClient, times(1)).notifyForSubscription(notificationRequestCaptor.capture(), hiuIdCaptor.capture());

        HIUSubscriptionNotificationRequest notificationRequest = notificationRequestCaptor.getValue();
        String hiuId = hiuIdCaptor.getValue();

        assertThat(notificationRequest.getEvent().getCategory()).isEqualTo(Category.LINK);
        assertThat(notificationRequest.getEvent().getSubscriptionId()).isEqualTo(subscription2.getId());
        assertThat(notificationRequest.getEvent().getContent().getHip().getId()).isEqualTo(linkEvent.getHipId());
        assertThat(notificationRequest.getEvent().getContent().getContext().get(0).getCareContext()).isEqualTo(linkEvent.getCareContexts().get(0));
        assertThat(notificationRequest.getEvent().getContent().getContext().get(0).getHiTypes()).isNullOrEmpty();
        assertThat(notificationRequest.getEvent().getContent().getPatient().getId()).isEqualTo(user.getIdentifier());
        assertThat(hiuId).isEqualTo(subscription2.getHiu().getId());
    }
}