package in.projecteka.datanotificationsubscription;

import in.projecteka.datanotificationsubscription.common.GatewayServiceClient;
import in.projecteka.datanotificationsubscription.common.model.PatientCareContext;
import in.projecteka.datanotificationsubscription.hipLink.NewCCLinkEvent;
import in.projecteka.datanotificationsubscription.subscription.Subscription;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionRequestRepository;
import in.projecteka.datanotificationsubscription.subscription.model.Categories;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionNotificationRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionNotification;
import in.projecteka.datanotificationsubscription.subscription.model.NotificationContent;
import in.projecteka.datanotificationsubscription.subscription.model.NotificationContext;
import in.projecteka.datanotificationsubscription.subscription.model.NotificationEvent;
import lombok.AllArgsConstructor;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@AllArgsConstructor
public class HIUSubscriptionManager {
    private final SubscriptionRequestRepository subscriptionRequestRepository;
    private final GatewayServiceClient gatewayServiceClient;

    public void notifySubscribers(NewCCLinkEvent ccLinkEvent) {
        String healthId = ccLinkEvent.getHealthNumber();
        String hipId = ccLinkEvent.getHipId();
        Mono<List<Subscription>> applicableSubscriptions = subscriptionRequestRepository.findSubscriptionsFor(healthId, hipId);
        Mono<List<SubscriptionNotification>> notificationEvents = applicableSubscriptions.map(subscriptions ->
                subscriptions.stream().map(subscription ->
                        buildNotifications(ccLinkEvent, subscription)).collect(Collectors.toList())
        );

        //TODO: Revisit, is this the best way of doing it
        Flux<SubscriptionNotification> notificationFlux = notificationEvents.flatMapMany(Flux::fromIterable);
        notificationFlux
                .flatMap((Function<SubscriptionNotification, Publisher<?>>) this::notifyHIU)
        .subscribe();
    }

    private Mono<Void> notifyHIU(SubscriptionNotification notification) {
        HIUSubscriptionNotificationRequest notificationRequest = HIUSubscriptionNotificationRequest.builder()
                .requestId(UUID.randomUUID())
                .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                .event(notification.getEvent())
                .build();
        return gatewayServiceClient.notifyForSubscription(notificationRequest, notification.getHiuId());
    }

    private SubscriptionNotification buildNotifications(NewCCLinkEvent ccLinkEvent, Subscription subscription) {
        NotificationContent notificationContent = NotificationContent.builder()
                .hip(subscription.getHipDetail())
                .patient(subscription.getPatient())
                .context(buildContext(ccLinkEvent.getCareContexts()))
                .build();

        NotificationEvent notificationEvent = NotificationEvent.builder()
                .id(UUID.randomUUID()) //TODO: Should this be stored?
                .published(ccLinkEvent.getTimestamp())
                .category(Categories.LINK)
                .subscriptionId(subscription.getId())
                .content(notificationContent)
                .build();
        return SubscriptionNotification.builder()
                .hiuId(subscription.getHiuDetail().getId())
                .event(notificationEvent)
                .build();
    }

    private List<NotificationContext> buildContext(List<PatientCareContext> careContexts) {
        return careContexts.stream().map(
                patientCareContext -> NotificationContext.builder().careContext(patientCareContext).build()
        ).collect(Collectors.toList());
    }
}
