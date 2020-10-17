package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.ConceptValidator;
import in.projecteka.datanotificationsubscription.clients.LinkServiceClient;
import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.clients.model.Links;
import in.projecteka.datanotificationsubscription.clients.model.User;
import in.projecteka.datanotificationsubscription.common.ClientError;
import in.projecteka.datanotificationsubscription.common.Error;
import in.projecteka.datanotificationsubscription.common.ErrorRepresentation;
import in.projecteka.datanotificationsubscription.common.GatewayServiceClient;
import in.projecteka.datanotificationsubscription.common.model.HIType;
import in.projecteka.datanotificationsubscription.common.model.ServiceInfo;
import in.projecteka.datanotificationsubscription.subscription.model.GatewayResponse;
import in.projecteka.datanotificationsubscription.subscription.model.GrantedSubscription;
import in.projecteka.datanotificationsubscription.subscription.model.HipDetail;
import in.projecteka.datanotificationsubscription.subscription.model.ListResult;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionApprovalResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionOnInitRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionProperties;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestAck;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestDetails;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static in.projecteka.datanotificationsubscription.common.ErrorCode.INVALID_HITYPE;
import static in.projecteka.datanotificationsubscription.common.ErrorCode.USER_NOT_FOUND;
import static in.projecteka.datanotificationsubscription.subscription.model.RequestStatus.GRANTED;
import static in.projecteka.datanotificationsubscription.subscription.model.RequestStatus.REQUESTED;
import static java.time.LocalDateTime.now;
import static java.time.ZoneOffset.UTC;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@AllArgsConstructor
public class SubscriptionRequestService {
    private final SubscriptionRequestRepository subscriptionRequestRepository;
    private final UserServiceClient userServiceClient;
    private final GatewayServiceClient gatewayServiceClient;
    private final LinkServiceClient linkServiceClient;

    private final ConceptValidator conceptValidator;
    private SubscriptionProperties subscriptionProperties;
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRequestService.class);
    public static final String ALL_SUBSCRIPTION_REQUESTS = "ALL";

    public Mono<Void> subscriptionRequest(SubscriptionDetail subscription, UUID gatewayRequestId) {
        logger.debug("Received a subscription request: " + gatewayRequestId);
        return Mono.just(subscription)
                .flatMap(request -> saveSubscriptionRequestAndNotify(request, gatewayRequestId));
    }


    private Mono<User> findPatient(String patientId) {
        //TODO: cache findPatient
        return userServiceClient.userOf(patientId)
                .onErrorResume(ClientError.class,
                        clientError -> Mono.error(new ClientError(BAD_REQUEST,
                                new ErrorRepresentation(Error.builder().code(USER_NOT_FOUND).message("Invalid Patient")
                                        .build()))));
    }

    private Mono<SubscriptionDetail> populateHIPsIfNotPresent(SubscriptionDetail subscriptionDetail) {
        //TODO: Refactor CM to find links by HelathId/HealthID Number
        if (!CollectionUtils.isEmpty(subscriptionDetail.getHips())) return Mono.just(subscriptionDetail);
        return linkServiceClient.getUserLinks(subscriptionDetail.getPatient().getId())
                .map(patientLinksResponse -> {
                    List<HipDetail> linkedHIPs = patientLinksResponse.getPatient().getLinks().stream().map(Links::getHip).collect(Collectors.toList());
                    subscriptionDetail.setHips(linkedHIPs);
                    return subscriptionDetail;
                });
    }

    private Mono<Void> saveSubscriptionRequestAndNotify(SubscriptionDetail subscriptionDetail, UUID gatewayRequestId) {
        return findPatient(subscriptionDetail.getPatient().getId())
                .flatMap(patient -> {
                    final String patientId = getPatientId(subscriptionDetail.getPatient().getId(), patient);

                    return populateHIPsIfNotPresent(subscriptionDetail)
                            .flatMap(updatedDetails -> {
                                String serviceId = updatedDetails.getHiu().getId();
                                Mono<ServiceInfo> gatewayResult = gatewayServiceClient.getServiceInfo(serviceId);
                                return gatewayResult.flatMap(serviceInfo -> {
                                    updatedDetails.getHiu().setName(serviceInfo.getName());
                                    var acknowledgmentId = UUID.randomUUID();
                                    return subscriptionRequestRepository.insert(updatedDetails, acknowledgmentId, serviceInfo.getType(), patientId)
                                            .then(gatewayServiceClient.subscriptionRequestOnInit(onInitRequest(acknowledgmentId, gatewayRequestId), updatedDetails.getHiu().getId()));
                                });
                            });
                });
    }

    private SubscriptionOnInitRequest onInitRequest(UUID acknowledgmentId, UUID gatewayRequestId) {
        return SubscriptionOnInitRequest.builder()
                .requestId(UUID.randomUUID())
                .timestamp(now(UTC))
                .resp(GatewayResponse.builder().requestId(gatewayRequestId.toString()).build())
                .subscriptionRequest(SubscriptionRequestAck.builder().id(acknowledgmentId).build())
                .build();
    }

    public Mono<ListResult<List<SubscriptionRequestDetails>>> getAllSubscriptions(String username, int limit, int offset, String status) {
        //TODO: Cache findPatient
        return findPatient(username)
                .flatMap(user -> {
                    String patientId = getPatientId(username, user);
                    return status.equals(ALL_SUBSCRIPTION_REQUESTS)
                            ? subscriptionRequestRepository.getAllSubscriptionRequests(patientId, limit, offset, null)
                            : subscriptionRequestRepository.getAllSubscriptionRequests(patientId, limit, offset, status);
                });

    }

    private String getPatientId(String username, User user) {
        return StringUtils.isEmpty(user.getHealthIdNumber())
                ? username :
                user.getHealthIdNumber();
    }

    public Mono<SubscriptionApprovalResponse> approveSubscription(String username, String requestId, List<GrantedSubscription> grantedSubscriptions) {
        return findPatient(username)
                .flatMap(user -> {
                    String patientId = getPatientId(username, user);
                    return Mono.just(user)
                            .then(validateDate(grantedSubscriptions))
                            .then(validateHiTypes(in(grantedSubscriptions)))
                            .then(validateSubscriptionRequest(requestId, patientId))
                            .filter(subscriptionRequestDetails -> !isSubscriptionRequestExpired(subscriptionRequestDetails.getCreatedAt()))
                            .switchIfEmpty(Mono.error(ClientError.subscriptionRequestExpired()))
                            .flatMap(subscriptionRequest -> {
                                String subscriptionId = UUID.randomUUID().toString();
                                return updateHIUSubscription(requestId, subscriptionId).then(
                                        insertIntoSubscriptionSource(subscriptionId, grantedSubscriptions))
                                        .thenReturn(new SubscriptionApprovalResponse(subscriptionId));
                            });
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
                .switchIfEmpty(Mono.error(ClientError.subscriptionRequestNotFound()));
    }


    private HIType[] in(List<GrantedSubscription> grantedSubscriptions) {
        return grantedSubscriptions.stream()
                .parallel()
                .flatMap(grantedSubscription -> Arrays.stream(grantedSubscription.getHiTypes()))
                .toArray(HIType[]::new);
    }

    private Mono<Void> validateDate(List<GrantedSubscription> grantedSubscriptions) {
        boolean validDates = grantedSubscriptions.stream().allMatch(grantedSubscription -> isDateValidatedForNullAndFuture(grantedSubscription));
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
}
