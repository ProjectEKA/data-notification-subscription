package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.ConceptValidator;
import in.projecteka.datanotificationsubscription.clients.LinkServiceClient;
import in.projecteka.datanotificationsubscription.clients.UserAuthorizationServiceClient;
import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.clients.model.AuthRequestRepresentation;
import in.projecteka.datanotificationsubscription.clients.model.User;
import in.projecteka.datanotificationsubscription.common.AppPushNotificationPublisher;
import in.projecteka.datanotificationsubscription.common.ClientError;
import in.projecteka.datanotificationsubscription.common.Error;
import in.projecteka.datanotificationsubscription.common.ErrorRepresentation;
import in.projecteka.datanotificationsubscription.common.GatewayServiceClient;
import in.projecteka.datanotificationsubscription.common.PushNotificationData;
import in.projecteka.datanotificationsubscription.common.model.HIType;
import in.projecteka.datanotificationsubscription.common.model.RequesterType;
import in.projecteka.datanotificationsubscription.common.model.ServiceInfo;
import in.projecteka.datanotificationsubscription.subscription.model.GatewayResponse;
import in.projecteka.datanotificationsubscription.subscription.model.GrantedSubscription;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionNotifyResponse;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionRequestNotification;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionRequestNotifyRequest;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionRequestNotifyResponse;
import in.projecteka.datanotificationsubscription.subscription.model.HipDetail;
import in.projecteka.datanotificationsubscription.subscription.model.ListResult;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionApprovalResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionEditAndApprovalRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionOnInitRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionProperties;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestAck;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestDetails;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static in.projecteka.datanotificationsubscription.common.ErrorCode.INVALID_HITYPE;
import static in.projecteka.datanotificationsubscription.common.ErrorCode.USER_NOT_FOUND;
import static in.projecteka.datanotificationsubscription.subscription.model.RequestStatus.DENIED;
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
    private final ConceptValidator conceptValidator;
    private final SubscriptionProperties subscriptionProperties;
    private final AppPushNotificationPublisher appPushNotificationPublisher;
    private final UserAuthorizationServiceClient userAuthorizationServiceClient;
    private final LinkServiceClient linkServiceClient;

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRequestService.class);
    public static final String ALL_SUBSCRIPTION_REQUESTS = "ALL";
    public static final String LOCKER_SETUP_TARGET_ACTIVITY = "in.nhdm.phr.ui.activity.LinkLockerDetailsActivity";

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

    private Mono<Void> saveSubscriptionRequestAndNotify(SubscriptionDetail subscriptionDetail, UUID gatewayRequestId) {
        return findPatient(subscriptionDetail.getPatient().getId())
                .flatMap(patient -> {
                    final String patientId = getPatientId(subscriptionDetail.getPatient().getId(), patient);
                    String serviceId = subscriptionDetail.getHiu().getId();
                    Mono<ServiceInfo> gatewayResult = gatewayServiceClient.getServiceInfo(serviceId);
                    return gatewayResult.flatMap(serviceInfo -> {
                        subscriptionDetail.getHiu().setName(serviceInfo.getName());
                        var acknowledgmentId = UUID.randomUUID();
                        return subscriptionRequestRepository.insert(subscriptionDetail, acknowledgmentId, serviceInfo.getType(), patientId)
                                .then(gatewayServiceClient.subscriptionRequestOnInit(onInitRequest(acknowledgmentId, gatewayRequestId), subscriptionDetail.getHiu().getId()))
                                .doOnSuccess((unused -> {
                                    publishNotificationForLockerSetup(acknowledgmentId.toString(), serviceInfo, patient.getIdentifier())
                                            .subscribeOn(Schedulers.elastic())
                                            .subscribe();
                                }));
                    });
                });
    }

    private Mono<Void> publishNotificationForLockerSetup(String subscriptionRequestId, ServiceInfo serviceInfo, String healthId) {
        if (!serviceInfo.getType().equals(RequesterType.HEALTH_LOCKER)) {
            return Mono.empty();
        }
        return userAuthorizationServiceClient.authRequestsForPatientByHIP(healthId, serviceInfo.getId())
                .filter(this::isAnyAuthorizationInRequestedState)
                .flatMap(authRequests -> {
                    var requestedAuthorization = Arrays.stream(authRequests)
                            .filter(this::isAuthorizationRequested)
                            .findFirst();

                    if (requestedAuthorization.isEmpty()) {
                        return Mono.empty();
                    }
                    String lockerName = StringUtils.isEmpty(serviceInfo.getName()) ? "Unknown Locker" : serviceInfo.getName();
                    Map<String, String> params = new HashMap<>();
                    params.put("subscription_request_id", subscriptionRequestId);
                    params.put("authorization_request_id", requestedAuthorization.get().getRequestId());

                    PushNotificationData pushNotificationData = PushNotificationData.builder()
                            .healthId(healthId)
                            .title("Locker Setup Request")
                            .body(lockerName)
                            .target(LOCKER_SETUP_TARGET_ACTIVITY)
                            .params(params)
                            .build();

                    return appPushNotificationPublisher.publish(pushNotificationData);
                });
    }

    private boolean isAnyAuthorizationInRequestedState(AuthRequestRepresentation[] authRequests) {
        return Arrays.stream(authRequests).anyMatch(this::isAuthorizationRequested);
    }

    private boolean isAuthorizationRequested(AuthRequestRepresentation authRequest) {
        return authRequest.getStatus().equalsIgnoreCase("REQUESTED");
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


    public Mono<Tuple2<ListResult<List<SubscriptionRequestDetails>>, ListResult<List<SubscriptionRequestDetails>>>> getPatientSubscriptions(String username,
                                                                                                                                            int hiuRequestsLimit,
                                                                                                                                            int hiuRequestsOffset,
                                                                                                                                            int lockerRequestsLimit,
                                                                                                                                            int lockerRequestsOffset,
                                                                                                                                            String status) {
        var subscriptionRequesters = List.of(RequesterType.HIU.toString(), RequesterType.HIP_AND_HIU.toString());
        var lockerSetupRequesters = List.of(RequesterType.HEALTH_LOCKER.toString());
        return findPatient(username)
                .flatMap(user -> {
                    String patientId = getPatientId(username, user);
                    Mono<ListResult<List<SubscriptionRequestDetails>>> listHIUSubscriptionRequests = status.equals(ALL_SUBSCRIPTION_REQUESTS)
                            ? subscriptionRequestRepository.getPatientSubscriptionRequests(patientId, hiuRequestsLimit, hiuRequestsOffset, null, subscriptionRequesters)
                            : subscriptionRequestRepository.getPatientSubscriptionRequests(patientId, hiuRequestsLimit, hiuRequestsOffset, status, subscriptionRequesters);
                    Mono<ListResult<List<SubscriptionRequestDetails>>> listHLSubscriptionRequests = status.equals(ALL_SUBSCRIPTION_REQUESTS)
                            ? subscriptionRequestRepository.getPatientSubscriptionRequests(patientId, lockerRequestsLimit, lockerRequestsOffset, null, lockerSetupRequesters)
                            : subscriptionRequestRepository.getPatientSubscriptionRequests(patientId, lockerRequestsLimit, lockerRequestsOffset, status, lockerSetupRequesters);
                    return Mono.zip(listHIUSubscriptionRequests, listHLSubscriptionRequests);

                });
    }

    private String getPatientId(String username, User user) {
        return StringUtils.isEmpty(user.getHealthIdNumber())
                ? username :
                user.getHealthIdNumber();
    }

    public Mono<SubscriptionApprovalResponse> approveSubscription(String username, String requestId, SubscriptionEditAndApprovalRequest subscriptionApprovalRequest) {
        return findPatient(username)
                .flatMap(user -> {
                    String patientId = getPatientId(username, user);
                    return Mono.just(user)
                            .then(validateDate(subscriptionApprovalRequest.getIncludedSources()))
                            .then(validateHiTypes(in(subscriptionApprovalRequest.getIncludedSources())))
                            .then(validateSubscriptionRequest(requestId, patientId))
                            .filter(subscriptionRequestDetails -> !isSubscriptionRequestExpired(subscriptionRequestDetails.getCreatedAt()))
                            .switchIfEmpty(Mono.error(ClientError.subscriptionRequestExpired()))
                            .flatMap(subscriptionRequest -> insertAndNotifyHIU(requestId, subscriptionApprovalRequest, subscriptionRequest, patientId));
                });

    }

    public Mono<Void> denySubscription(String username, String requestId) {
        return findPatient(username)
                .flatMap(user -> {
                    String patientId = getPatientId(username, user);
                    return Mono.just(user)
                            .then(validateSubscriptionRequest(requestId, patientId))
                            .filter(subscriptionRequestDetails -> !isSubscriptionRequestExpired(subscriptionRequestDetails.getCreatedAt()))
                            .switchIfEmpty(Mono.error(ClientError.subscriptionRequestExpired()))
                            .flatMap(subscriptionRequest -> {
                                String hiuId = subscriptionRequest.getHiu().getId();
                                HIUSubscriptionRequestNotification notification = HIUSubscriptionRequestNotification.builder()
                                        .subscriptionRequestId(subscriptionRequest.getId())
                                        .status(DENIED.name()).build();

                                HIUSubscriptionRequestNotifyRequest request = HIUSubscriptionRequestNotifyRequest.builder()
                                        .requestId(UUID.randomUUID())
                                        .timestamp(now(UTC))
                                        .notification(notification)
                                        .build();

                                return subscriptionRequestRepository
                                        .updateHIUSubscription(requestId, null, DENIED.name())
                                        .then(gatewayServiceClient.subscriptionRequestNotify(request, hiuId));
                            });
                });
    }

    private Mono<SubscriptionApprovalResponse> insertAndNotifyHIU(String requestId,
                                                                  SubscriptionEditAndApprovalRequest subscriptionApprovalRequest,
                                                                  SubscriptionRequestDetails subscriptionRequest,
                                                                  String patientId) {
        //TODO: What type of notification should be sent in case of ALL, should it have exclusions as well
        String subscriptionId = UUID.randomUUID().toString();
        String hiuId = subscriptionRequest.getHiu().getId();
        return updateHIUSubscription(requestId, subscriptionId)
                .then(insertIntoSubscriptionSource(subscriptionId, subscriptionApprovalRequest))
                .then(deduceGrantedSubscriptions(subscriptionApprovalRequest, patientId))
                .flatMap(grantedSubscriptions -> gatewayServiceClient.subscriptionRequestNotify(subscriptionRequestNotifyRequest(subscriptionRequest, subscriptionId, grantedSubscriptions), hiuId))
                .thenReturn(new SubscriptionApprovalResponse(subscriptionId));
    }

    private Mono<List<GrantedSubscription>> deduceGrantedSubscriptions(SubscriptionEditAndApprovalRequest approvalRequest,
                                                                       String patientId) {
        if (approvalRequest.isApplicableForAllHIPs()) {
            var categories = approvalRequest.getIncludedSources().get(0).getCategories();
            var hiTypes = approvalRequest.getIncludedSources().get(0).getHiTypes();
            var period = approvalRequest.getIncludedSources().get(0).getPeriod();
            var purpose = approvalRequest.getIncludedSources().get(0).getPurpose();

            return linkServiceClient.getUserLinks(patientId)
                    .map(patientLinksResponse -> patientLinksResponse.getPatient().getLinks())
                    .map(links -> links.stream()
                            .map(link -> link.getHip().getId())
                            .map(hipId -> GrantedSubscription.builder()
                                    .categories(categories)
                                    .hiTypes(hiTypes)
                                    .period(period)
                                    .purpose(purpose)
                                    .hip(HipDetail.builder().id(hipId).build())
                                    .build())
                            .collect(Collectors.toList()))
                    .doOnError(error -> {
                        logger.error("Failed to notify HIU for subscription approval for isApplicableForAllHIPs = true", error);
                    });
        }

        return Mono.just(approvalRequest.getIncludedSources());
    }

    private HIUSubscriptionRequestNotifyRequest subscriptionRequestNotifyRequest(SubscriptionRequestDetails subscriptionRequest, String subscriptionId, List<GrantedSubscription> grantedSubscriptions) {
        List<HIUSubscriptionRequestNotification.Source> sources = grantedSubscriptions.stream().map(grantedSubscription -> HIUSubscriptionRequestNotification.Source.builder()
                .categories(grantedSubscription.getCategories())
                .hip(grantedSubscription.getHip())
                .period(grantedSubscription.getPeriod())
                .build()).collect(Collectors.toList());

        HIUSubscriptionRequestNotification notification = HIUSubscriptionRequestNotification.builder()
                .subscriptionRequestId(subscriptionRequest.getId())
                .status(GRANTED.name())
                .subscription(HIUSubscriptionRequestNotification.Subscription.builder()
                        .id(UUID.fromString(subscriptionId))
                        .patient(subscriptionRequest.getPatient())
                        .hiu(subscriptionRequest.getHiu())
                        .sources(sources)
                        .build())
                .build();

        UUID gatewayRequestId = UUID.randomUUID();
        return HIUSubscriptionRequestNotifyRequest.builder()
                .requestId(gatewayRequestId)
                .timestamp(now(UTC))
                .notification(notification)
                .build();
    }

    private Mono<Void> insertIntoSubscriptionSource(String subscriptionId, SubscriptionEditAndApprovalRequest subscriptionApprovalRequest) {
        Mono<List<Void>> sources = Flux.fromIterable(subscriptionApprovalRequest.getIncludedSources())
                .flatMap(grantedSubscription -> subscriptionRequestRepository.insertIntoSubscriptionSource(subscriptionId, grantedSubscription, false))
                .collectList();
        Mono<List<Void>> excludeSources = Flux.fromIterable(subscriptionApprovalRequest.getExcludedSources())
                .flatMap(excludedSubscription -> subscriptionRequestRepository.insertIntoSubscriptionSource(subscriptionId, excludedSubscription, true))
                .collectList();
        return Mono.zip(sources, excludeSources).then();
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

    public Mono<Void> subscriptionRequestOnNotify(HIUSubscriptionRequestNotifyResponse response) {
        if (response.getError() != null) {
            logger.error("Error occurred at subscriptionRequestOnNotify for subscription request id: {}", response.getAcknowledgement().getSubscriptionRequestId());
            logger.error(response.getError().toString());
            return Mono.empty();
        }
        logger.info("Successful acknowledgement at subscriptionRequestOnNotify for subscription request id: {}", response.getAcknowledgement().getSubscriptionRequestId());
        return Mono.empty();
    }

    public Mono<Void> subscriptionOnNotify(HIUSubscriptionNotifyResponse response) {
        if (response.getError() != null) {
            logger.error("Error occurred at subscriptionOnNotify for notification event id: {}", response.getAcknowledgement().getEventId());
            logger.error(response.getError().toString());
            return Mono.empty();
        }
        logger.info("Successful acknowledgement at subscriptionOnNotify for notification event id: {}", response.getAcknowledgement().getEventId());
        return Mono.empty();
    }

    public Mono<SubscriptionRequestDetails> getSubscriptionRequestDetails(String requestId) {
        return subscriptionRequestRepository.getSubscriptionRequest(requestId)
                .switchIfEmpty(Mono.error(ClientError.subscriptionRequestNotFound()));
    }

    public Mono<List<SubscriptionRequestDetails>> getPatientSubscriptionRequestForHIU(String patientId, String hiuId) {
        return findPatient(patientId)
                .flatMap(user -> subscriptionRequestRepository.getPatientSubscriptionRequestsByHIU(patientId, hiuId));
    }
}
