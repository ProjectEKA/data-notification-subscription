package in.projecteka.datanotificationsubscription.hipLink;

import in.projecteka.datanotificationsubscription.HIUSubscriptionManager;
import in.projecteka.datanotificationsubscription.common.GatewayServiceClient;
import in.projecteka.datanotificationsubscription.subscription.Subscription;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionRequestRepository;
import in.projecteka.datanotificationsubscription.subscription.model.Category;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionNotificationRequest;
import in.projecteka.datanotificationsubscription.subscription.model.HipDetail;
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

    HIUSubscriptionManager hiuSubscriptionManager;

    @BeforeEach
    void setUp() {
        initMocks(this);
        hiuSubscriptionManager = new HIUSubscriptionManager(subscriptionRequestRepository, gatewayServiceClient);
    }

    @Test
    void shouldNotifyAllSubscribersApplicableForGivenLinkEvent() {
        NewCCLinkEvent linkEvent = newCCLinkEvent()
                .careContexts(asList(patientCareContext().build()))
                .build();

        HipDetail hipDetail = HipDetail.builder().id(linkEvent.getHipId()).build();
        Subscription subscription1 = subscription().hipDetail(hipDetail).build();
        Subscription subscription2 = subscription().hipDetail(hipDetail).build();


        when(subscriptionRequestRepository.findLinkSubscriptionsFor(anyString(), anyString())).thenReturn(Mono.just(asList(subscription1, subscription2)));
        when(gatewayServiceClient.notifyForSubscription(any(HIUSubscriptionNotificationRequest.class), anyString())).thenReturn(Mono.empty());

        Flux<Void> notifications = hiuSubscriptionManager.notifySubscribers(linkEvent);
        StepVerifier.create(notifications)
                .expectNext()
                .verifyComplete();

        ArgumentCaptor<HIUSubscriptionNotificationRequest> notificationRequestCaptor = ArgumentCaptor.forClass(HIUSubscriptionNotificationRequest.class);
        ArgumentCaptor<String> hiuIdCaptor = ArgumentCaptor.forClass(String.class);

        verify(subscriptionRequestRepository, times(1)).findLinkSubscriptionsFor(linkEvent.getHealthNumber(), linkEvent.getHipId());
        verify(gatewayServiceClient, times(2)).notifyForSubscription(notificationRequestCaptor.capture(), hiuIdCaptor.capture());

        List<HIUSubscriptionNotificationRequest> notificationRequests = notificationRequestCaptor.getAllValues();
        List<String> hiuIds = hiuIdCaptor.getAllValues();

        assertThat(notificationRequests.get(0).getEvent().getCategory()).isEqualTo(Category.LINK);
        assertThat(notificationRequests.get(0).getEvent().getSubscriptionId()).isEqualTo(subscription1.getId());
        assertThat(notificationRequests.get(0).getEvent().getContent().getHip().getId()).isEqualTo(linkEvent.getHipId());
        assertThat(notificationRequests.get(0).getEvent().getContent().getContext().get(0).getCareContext()).isEqualTo(linkEvent.getCareContexts().get(0));
        assertThat(notificationRequests.get(0).getEvent().getContent().getContext().get(0).getHiTypes()).isNullOrEmpty();
        assertThat(hiuIds.get(0)).isEqualTo(subscription1.getHiuDetail().getId());

        assertThat(notificationRequests.get(1).getEvent().getCategory()).isEqualTo(Category.LINK);
        assertThat(notificationRequests.get(1).getEvent().getSubscriptionId()).isEqualTo(subscription2.getId());
        assertThat(notificationRequests.get(1).getEvent().getContent().getHip().getId()).isEqualTo(linkEvent.getHipId());
        assertThat(notificationRequests.get(1).getEvent().getContent().getContext().get(0).getCareContext()).isEqualTo(linkEvent.getCareContexts().get(0));
        assertThat(notificationRequests.get(1).getEvent().getContent().getContext().get(0).getHiTypes()).isNullOrEmpty();
        assertThat(hiuIds.get(1)).isEqualTo(subscription2.getHiuDetail().getId());
    }
}