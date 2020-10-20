package in.projecteka.datanotificationsubscription;

import in.projecteka.datanotificationsubscription.common.GatewayServiceClient;
import in.projecteka.datanotificationsubscription.common.model.PatientCareContext;
import in.projecteka.datanotificationsubscription.hipLink.NewCCLinkEvent;
import in.projecteka.datanotificationsubscription.subscription.Subscription;
import in.projecteka.datanotificationsubscription.subscription.SubscriptionRequestRepository;
import in.projecteka.datanotificationsubscription.subscription.model.Category;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionNotificationRequest;
import in.projecteka.datanotificationsubscription.subscription.model.NotificationContent;
import in.projecteka.datanotificationsubscription.subscription.model.NotificationContext;
import in.projecteka.datanotificationsubscription.subscription.model.NotificationEvent;
import in.projecteka.datanotificationsubscription.subscription.model.PatientDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionNotification;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@AllArgsConstructor
public class HIUSubscriptionManager {
    private final SubscriptionRequestRepository subscriptionRequestRepository;
    private final GatewayServiceClient gatewayServiceClient;
    private final UserServiceProperties userServiceProperties;

    private final Logger logger = LoggerFactory.getLogger(HIUSubscriptionManager.class);

    public Flux<Void> notifySubscribers(NewCCLinkEvent ccLinkEvent) {
        String healthId = ccLinkEvent.getHealthNumber();
        String hipId = ccLinkEvent.getHipId();
        Mono<List<Subscription>> applicableSubscriptions = subscriptionRequestRepository
                .findLinkSubscriptionsFor(healthId, hipId)
                .doOnNext(logSubscribers(ccLinkEvent));
        Mono<List<SubscriptionNotification>> notificationEvents = applicableSubscriptions.map(subscriptions ->
                subscriptions.stream().map(subscription ->
                        buildNotifications(ccLinkEvent, subscription)).collect(Collectors.toList())
        );

        //TODO: Revisit, is this the best way of doing it
        Flux<SubscriptionNotification> notificationFlux = notificationEvents.flatMapMany(Flux::fromIterable);
        return notificationFlux
                .flatMap(this::notifyHIU);
    }

    private Consumer<List<Subscription>> logSubscribers(NewCCLinkEvent ccLinkEvent) {
        return subscriptions -> {
            if (CollectionUtils.isEmpty(subscriptions)) {
                logger.info("No active subscribers for patient-id {} and hip {}", ccLinkEvent.getHealthNumber(), ccLinkEvent.getHipId());
            } else {
                logger.info("Found {} active subscribers for patient-id {} and hip {}", subscriptions.size(), ccLinkEvent.getHealthNumber(), ccLinkEvent.getHipId());
            }
        };
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
                .patient(getPatientWithSuffix(subscription.getPatient()))
                .context(buildContext(ccLinkEvent.getCareContexts()))
                .build();

        NotificationEvent notificationEvent = NotificationEvent.builder()
                .id(UUID.randomUUID()) //TODO: Should this be stored?
                .published(ccLinkEvent.getTimestamp())
                .category(Category.LINK)
                .subscriptionId(subscription.getId())
                .content(notificationContent)
                .build();
        return SubscriptionNotification.builder()
                .hiuId(subscription.getHiuDetail().getId())
                .event(notificationEvent)
                .build();
    }

    private PatientDetail getPatientWithSuffix(PatientDetail patientDetail) {
        String suffix = userServiceProperties.getUserIdSuffix();
        if (!StringUtils.endsWithIgnoreCase(patientDetail.getId(), suffix)) { //TODO: Get it from properties
            return PatientDetail.builder()
                    .id(patientDetail.getId() + suffix)
                    .build();
        }
        return patientDetail;
    }

    private List<NotificationContext> buildContext(List<PatientCareContext> careContexts) {
        return careContexts.stream().map(
                patientCareContext -> NotificationContext.builder().careContext(patientCareContext).build()
        ).collect(Collectors.toList());
    }
}
