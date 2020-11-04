package in.projecteka.datanotificationsubscription;

import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.clients.model.User;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@AllArgsConstructor
public class HIUSubscriptionManager {
    private final SubscriptionRequestRepository subscriptionRequestRepository;
    private final GatewayServiceClient gatewayServiceClient;
    private final UserServiceClient userServiceClient;

    private final Logger logger = LoggerFactory.getLogger(HIUSubscriptionManager.class);

    public Flux<Void> notifySubscribers(NewCCLinkEvent ccLinkEvent) {
        String healthId = ccLinkEvent.getHealthNumber();
        String hipId = ccLinkEvent.getHipId();
        Mono<Map<String, List<Subscription>>> subscriptionsByHIU = subscriptionRequestRepository
                .findLinkSubscriptionsFor(healthId, hipId)
                .map(subscriptions -> subscriptions.stream().collect(Collectors.groupingBy(subscription -> subscription.getHiu().getId())))
                .map(subscriptionPerHIU -> filterIfHIPExcluded(subscriptionPerHIU, ccLinkEvent.getHipId()))
                .doOnNext(logSubscribers(ccLinkEvent));
        Mono<User> userMono = userServiceClient.userOf(healthId);

        //Temp: Fetch User healthid and pass that as patient-id instead of healthid number
        Mono<List<SubscriptionNotification>> notificationEvents = Mono.zip(userMono, subscriptionsByHIU)
                .map(tuple -> {
                    User user = tuple.getT1();
                    Map<String, List<Subscription>> hiuSubscriptions = tuple.getT2();
                    return hiuSubscriptions.entrySet().stream().map(
                            subscriptionForHIU -> buildNotifications(ccLinkEvent, subscriptionForHIU, user.getIdentifier())).collect(Collectors.toList()
                    );
                });

        Flux<SubscriptionNotification> notificationFlux = notificationEvents.flatMapMany(Flux::fromIterable);
        return notificationFlux
                .flatMap(this::notifyHIU);
    }

    private Map<String, List<Subscription>> filterIfHIPExcluded(Map<String, List<Subscription>> subscriptionsByHIU, String hipId) {
        return subscriptionsByHIU
                .entrySet()
                .stream()
                .filter(hiuSubscription -> hiuSubscription.getValue().stream()
                        .noneMatch(subscription -> {
                            if(subscription.isExcluded() && subscription.getHip().getId().equals(hipId)){
                                logger.info("Notification is excluded for HIU {} from HIP {}", subscription.getHiu().getId(), subscription.getHip().getId());
                                return true;
                            }
                            return false;
                        })
                ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Consumer<Map<String, List<Subscription>>> logSubscribers(NewCCLinkEvent ccLinkEvent) {
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

    private SubscriptionNotification buildNotifications(NewCCLinkEvent ccLinkEvent, Map.Entry<String, List<Subscription>> hiuSubscription, String patientId) {
        //if there are multiple subscriptions applicable for the same HIU, send just one notification
        Subscription subscription = hiuSubscription.getValue().get(0);
        NotificationContent notificationContent = NotificationContent.builder()
                .hip(subscription.getHip())
                .patient(PatientDetail.builder().id(patientId).build())
                .context(buildContext(ccLinkEvent.getCareContexts()))
                .build();

        NotificationEvent notificationEvent = NotificationEvent.builder()
                .id(UUID.randomUUID())
                .published(ccLinkEvent.getTimestamp())
                .category(Category.LINK)
                .subscriptionId(subscription.getId())
                .content(notificationContent)
                .build();
        return SubscriptionNotification.builder()
                .hiuId(subscription.getHiu().getId())
                .event(notificationEvent)
                .build();
    }

    private List<NotificationContext> buildContext(List<PatientCareContext> careContexts) {
        return careContexts.stream().map(
                patientCareContext -> NotificationContext.builder().careContext(patientCareContext).build()
        ).collect(Collectors.toList());
    }
}
