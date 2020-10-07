package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.subscription.model.ListResult;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRepresentation;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
public class SubscriptionRequestService {
    private SubscriptionRequestRepository subscriptionRequestRepository;
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRequestService.class);
    public static final String ALL_SUBSCRIPTION_REQUESTS = "ALL";

    public Mono<Void> subscriptionRequest(SubscriptionDetail subscription, UUID requestId) {
        logger.info("Received a subscription request: " + requestId);
        return saveSubscriptionRequest(subscription, requestId);
    }

    private Mono<Void> saveSubscriptionRequest(SubscriptionDetail subscriptionDetail, UUID requestId){
        var acknowledgmentId = UUID.randomUUID();
        return subscriptionRequestRepository.insert(subscriptionDetail, acknowledgmentId)
                .then();
    }

    public Mono<ListResult<List<SubscriptionRepresentation>>> getAllSubscriptions(String username, int limit, int offset, String filters) {
        return filters.equals(ALL_SUBSCRIPTION_REQUESTS)
                ? subscriptionRequestRepository.getAllSubscriptionRequests(username, limit, offset, null)
                : subscriptionRequestRepository.getAllSubscriptionRequests(username, limit, offset, filters);
    }
}
