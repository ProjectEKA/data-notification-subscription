package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.common.ClientError;
import in.projecteka.datanotificationsubscription.common.Error;
import in.projecteka.datanotificationsubscription.common.ErrorRepresentation;
import in.projecteka.datanotificationsubscription.subscription.model.ListResult;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestDetails;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static in.projecteka.datanotificationsubscription.common.ErrorCode.USER_NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@AllArgsConstructor
public class SubscriptionRequestService {
    private SubscriptionRequestRepository subscriptionRequestRepository;
    private final UserServiceClient userServiceClient;

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRequestService.class);
    public static final String ALL_SUBSCRIPTION_REQUESTS = "ALL";

    public Mono<Void> subscriptionRequest(SubscriptionDetail subscription, UUID requestId) {
        logger.info("Received a subscription request: " + requestId);
        return Mono.just(subscription)
                .flatMap(request -> validatePatient(request.getPatient().getId())
                                    .then(saveSubscriptionRequest(request)));
    }

    private Mono<Boolean> validatePatient(String patientId) {
        return userServiceClient.userOf(patientId)
                .onErrorResume(ClientError.class,
                        clientError -> Mono.error(new ClientError(BAD_REQUEST,
                                new ErrorRepresentation(Error.builder().code(USER_NOT_FOUND).message("Invalid Patient")
                                        .build()))))
                .map(Objects::nonNull);
    }

    private Mono<Void> saveSubscriptionRequest(SubscriptionDetail subscriptionDetail){
        var acknowledgmentId = UUID.randomUUID();
        return subscriptionRequestRepository.insert(subscriptionDetail, acknowledgmentId)
                .then();
    }

    public Mono<ListResult<List<SubscriptionRequestDetails>>> getAllSubscriptions(String username, int limit, int offset, String status) {
        return status.equals(ALL_SUBSCRIPTION_REQUESTS)
                ? subscriptionRequestRepository.getAllSubscriptionRequests(username, limit, offset, null)
                : subscriptionRequestRepository.getAllSubscriptionRequests(username, limit, offset, status);
    }
}
