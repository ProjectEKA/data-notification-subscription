package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.ConceptValidator;
import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.common.ClientError;
import in.projecteka.datanotificationsubscription.common.Error;
import in.projecteka.datanotificationsubscription.common.ErrorRepresentation;
import in.projecteka.datanotificationsubscription.common.model.HIType;
import in.projecteka.datanotificationsubscription.subscription.model.GrantedSubscription;
import in.projecteka.datanotificationsubscription.subscription.model.ListResult;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionApprovalResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionProperties;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestDetails;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static in.projecteka.datanotificationsubscription.common.ErrorCode.INVALID_HITYPE;
import static in.projecteka.datanotificationsubscription.common.ErrorCode.USER_NOT_FOUND;
import static in.projecteka.datanotificationsubscription.subscription.model.RequestStatus.GRANTED;
import static in.projecteka.datanotificationsubscription.subscription.model.RequestStatus.REQUESTED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@AllArgsConstructor
public class SubscriptionRequestService {
    private SubscriptionRequestRepository subscriptionRequestRepository;
    private final UserServiceClient userServiceClient;

    private final ConceptValidator conceptValidator;
    private SubscriptionProperties subscriptionProperties;
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRequestService.class);
    public static final String ALL_SUBSCRIPTION_REQUESTS = "ALL";

    public Mono<Void> subscriptionRequest(SubscriptionDetail subscription, UUID requestId) {
        logger.info("Received a subscription request: " + requestId);
        return Mono.just(subscription)
                .flatMap(request -> validatePatient(request.getPatient().getId())
                        .then(saveSubscriptionRequest(request)));
    }

    private Mono<Void> saveSubscriptionRequest(SubscriptionDetail subscriptionDetail) {
        var acknowledgmentId = UUID.randomUUID();
        return subscriptionRequestRepository.insert(subscriptionDetail, acknowledgmentId)
                .then();
    }

    public Mono<ListResult<List<SubscriptionRequestDetails>>> getAllSubscriptions(String username, int limit, int offset, String status) {
        return status.equals(ALL_SUBSCRIPTION_REQUESTS)
                ? subscriptionRequestRepository.getAllSubscriptionRequests(username, limit, offset, null)
                : subscriptionRequestRepository.getAllSubscriptionRequests(username, limit, offset, status);
    }

    public Mono<SubscriptionApprovalResponse> approveSubscription(String username, String requestId, List<GrantedSubscription> grantedSubscriptions) {
        return validatePatient(username)
                .then(validateDate(grantedSubscriptions))
                .then(validateHiTypes(in(grantedSubscriptions)))
                .then(validateSubscriptionRequest(requestId, username))
                .filter(subscriptionRequestDetails -> !isSubscriptionRequestExpired(subscriptionRequestDetails.getCreatedAt()))
                .switchIfEmpty(Mono.error(ClientError.consentRequestExpired()))
                .flatMap(subscriptionRequest -> {
                    String subscriptionId = UUID.randomUUID().toString();
                    return updateHIUSubscription(requestId, subscriptionId).then(
                            insertIntoSubscriptionSource(subscriptionId, grantedSubscriptions))
                            .thenReturn(new SubscriptionApprovalResponse(subscriptionId));
                });

    }

    private Mono<Void> insertIntoSubscriptionSource(String subscriptionId, List<GrantedSubscription> grantedSubscriptions) {
        return Flux.fromIterable(grantedSubscriptions)
                .flatMap(grantedSubscription -> subscriptionRequestRepository.insertIntoSubscriptionSource(subscriptionId, grantedSubscription.getPeriod().getFromDate(),
                        grantedSubscription.getPeriod().getToDate(), grantedSubscription.getHip().getId(), grantedSubscription.getHiTypes(),
                        grantedSubscription.isLinkCategory(), grantedSubscription.isDataCategory()))
                .collectList()
                .then();
    }


    private Mono<Void> updateHIUSubscription(String requestId, String subscriptionId) {
        return subscriptionRequestRepository.updateHIUSubscription(requestId, subscriptionId, GRANTED.name());
    }

    private boolean isSubscriptionRequestExpired(LocalDateTime createdAt) {
        LocalDateTime requestExpiry = createdAt.plusMinutes(subscriptionProperties.getSubscriptionRequestExpiry());
        return requestExpiry.isBefore(LocalDateTime.now(ZoneOffset.UTC));
    }


    private Mono<Void> validateHiTypes(HIType[] hiTypes) {
        return conceptValidator.validateHITypes(Arrays.stream(hiTypes)
                .map(HIType::getValue)
                .collect(Collectors.toList()))
                .filter(Predicate.isEqual(false))
                .flatMap(invalidHiTypesExists ->
                        Mono.error(new ClientError(BAD_REQUEST,
                                new ErrorRepresentation(new Error(INVALID_HITYPE, "Invalid HI Type")))));
    }

    private Mono<SubscriptionRequestDetails> validateSubscriptionRequest(String requestId, String patientId) {
        return subscriptionRequestRepository.requestOf(requestId, REQUESTED.toString(), patientId)
                .switchIfEmpty(Mono.error(ClientError.consentRequestNotFound()));
    }


    private HIType[] in(List<GrantedSubscription> grantedSubscriptions) {
        return grantedSubscriptions.stream()
                .parallel()
                .flatMap(grantedConsent -> Arrays.stream(grantedConsent.getHiTypes()))
                .toArray(HIType[]::new);
    }

    private Mono<Void> validateDate(List<GrantedSubscription> grantedSubscriptions) {
        boolean validDates = grantedSubscriptions.stream().allMatch(grantedConsent -> isDateValidatedForNullAndFuture(grantedConsent));
        if (!validDates)
            return Mono.error(ClientError.invalidDateRange());
        else
            return Mono.empty();
    }

    private Boolean isDateValidatedForNullAndFuture(GrantedSubscription grantedSubscription) {
        return grantedSubscription.getPeriod().getFromDate() != null &&
                grantedSubscription.getPeriod().getFromDate().isBefore(LocalDateTime.now(ZoneOffset.UTC)) &&
                grantedSubscription.getPeriod().getToDate() != null;
    }


    private Mono<Boolean> validatePatient(String patientId) {
        return userServiceClient.userOf(patientId)
                .onErrorResume(ClientError.class,
                        clientError -> Mono.error(new ClientError(BAD_REQUEST,
                                new ErrorRepresentation(new Error(USER_NOT_FOUND, "Invalid patient")))))
                .map(Objects::nonNull);
    }
}
