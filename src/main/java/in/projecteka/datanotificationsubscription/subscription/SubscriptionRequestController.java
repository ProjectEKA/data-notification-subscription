package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.common.Caller;
import in.projecteka.datanotificationsubscription.common.ClientError;
import in.projecteka.datanotificationsubscription.common.RequestValidator;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionNotifyResponse;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionRequestNotifyResponse;
import in.projecteka.datanotificationsubscription.subscription.model.PatientSubscriptionRequestsRepresentation;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionEditAndApprovalRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionEditAndApprovalRequestValidator;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionApprovalResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionProperties;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestDetails;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestsRepresentation;
import lombok.AllArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static in.projecteka.datanotificationsubscription.common.Constants.APP_PATH_APPROVE_SUBSCRIPTION_REQUESTS;
import static in.projecteka.datanotificationsubscription.common.Constants.APP_PATH_DENY_SUBSCRIPTION_REQUESTS;
import static in.projecteka.datanotificationsubscription.common.Constants.APP_PATH_INTERNAL_SUBSCRIPTION_REQUESTS;
import static in.projecteka.datanotificationsubscription.common.Constants.APP_PATH_SUBSCRIPTION_REQUESTS;
import static in.projecteka.datanotificationsubscription.common.Constants.CORRELATION_ID;
import static in.projecteka.datanotificationsubscription.common.Constants.INTERNAL_PATH_APPROVE_SUBSCRIPTION_REQUESTS;
import static in.projecteka.datanotificationsubscription.common.Constants.INTERNAL_PATH_PATIENT_SUBSCRIPTION_REQUESTS_BY_HIU;
import static in.projecteka.datanotificationsubscription.common.Constants.INTERNAL_PATH_SUBSCRIPTION_REQUEST_DETAILS;
import static in.projecteka.datanotificationsubscription.common.Constants.PATH_SUBSCRIPTION_REQUEST_SUBSCRIBE;
import static in.projecteka.datanotificationsubscription.common.Constants.SUBSCRIPTION_HIU_ON_NOTIFY;
import static in.projecteka.datanotificationsubscription.common.Constants.SUBSCRIPTION_REQUEST_HIU_ON_NOTIFY;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;

@RestController
@AllArgsConstructor
public class SubscriptionRequestController {
    private final SubscriptionRequestService requestService;
    private final RequestValidator validator;
    private final SubscriptionProperties subscriptionProperties;
    private final SubscriptionEditAndApprovalRequestValidator approvalRequestValidator;

    @PostMapping(value = PATH_SUBSCRIPTION_REQUEST_SUBSCRIBE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> subscriptionRequest(
            @RequestBody @Validated({SubscriptionEditAndApprovalRequestValidator.class}) SubscriptionRequest request) {
        return just(request)
                .filterWhen(req -> validator.validate(request.getRequestId().toString(), request.getTimestamp()))
                .switchIfEmpty(error(ClientError.tooManyRequests()))
                .flatMap(validatedRequest -> validator.put(request.getRequestId().toString(), request.getTimestamp())
                        .then(requestService.subscriptionRequest(request.getSubscription(), request.getRequestId())));
    }

    @GetMapping(value = APP_PATH_SUBSCRIPTION_REQUESTS)
    public Mono<SubscriptionRequestsRepresentation> getSubscriptionRequest(@RequestParam(defaultValue = "-1") int limit,
                                                                           @RequestParam(defaultValue = "0") int offset,
                                                                           @RequestParam(defaultValue = "ALL") String status) {
        int pageSize = getPageSize(limit);
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .flatMap(caller -> requestService.getAllSubscriptions(caller.getUsername(), pageSize, offset, status))

                .map(subscriptions -> SubscriptionRequestsRepresentation.builder()
                        .requests(subscriptions.getResult())
                        .size(subscriptions.getTotal())
                        .limit(pageSize)
                        .offset(offset).build());
    }

    @GetMapping(value = INTERNAL_PATH_SUBSCRIPTION_REQUEST_DETAILS)
    public Mono<SubscriptionRequestDetails> getSubscriptionRequest(@PathVariable("request-id") String requestId) {
        return requestService.getSubscriptionRequestDetails(requestId);
    }

    @GetMapping(value = INTERNAL_PATH_PATIENT_SUBSCRIPTION_REQUESTS_BY_HIU)
    public Mono<List<SubscriptionRequestDetails>> getPatientSubscriptionRequestForHIU(@PathVariable("patient-id") String patientId,
                                                                                      @PathVariable("hiu-id") String hiuId) {
        return requestService.getPatientSubscriptionRequestForHIU(patientId, hiuId);
    }


    @GetMapping(value = APP_PATH_INTERNAL_SUBSCRIPTION_REQUESTS)
    public Mono<PatientSubscriptionRequestsRepresentation> getSubscriptionRequest(
            @PathVariable(value = "patient-id") String patientId,
            @RequestParam(defaultValue = "-1") int subscriptionLimit,
            @RequestParam(defaultValue = "0") int subscriptionOffset,
            @RequestParam(defaultValue = "-1") int lockerLimit,
            @RequestParam(defaultValue = "0") int lockerOffset,
            @RequestParam(defaultValue = "ALL") String status) {
        int hiuRequestsPageSize = getInternalSubscriptionPageSize(subscriptionLimit);
        int lockerRequestsPageSize = getInternalSubscriptionPageSize(lockerLimit);

        return requestService.getPatientSubscriptions(patientId, hiuRequestsPageSize, subscriptionOffset, lockerRequestsPageSize, lockerOffset, status)
                .map(subscriptions -> PatientSubscriptionRequestsRepresentation.builder()
                        .hiuSubscriptionRequestsRepresentation(SubscriptionRequestsRepresentation.builder()
                                .requests(subscriptions.getT1().getResult())
                                .size(subscriptions.getT1().getTotal())
                                .limit(hiuRequestsPageSize)
                                .offset(subscriptionOffset).build())
                        .lockerSubscriptionRequestsRepresentation(SubscriptionRequestsRepresentation.builder()
                                .requests(subscriptions.getT2().getResult())
                                .size(subscriptions.getT2().getTotal())
                                .limit(hiuRequestsPageSize)
                                .offset(subscriptionOffset).build())
                        .build());
    }

    private int getInternalSubscriptionPageSize(int limit) {
        if (limit < 0) {
            return 5;
        }
        return Math.min(limit, 5);
    }

    @PostMapping(value = APP_PATH_APPROVE_SUBSCRIPTION_REQUESTS)
    public Mono<SubscriptionApprovalResponse> approveSubscription(
            @PathVariable(value = "request-id") String requestId,
            @Valid @RequestBody SubscriptionEditAndApprovalRequest subscriptionApprovalRequest) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .flatMap(caller -> approvalRequestValidator
                        .validateRequest(subscriptionApprovalRequest)
                        .then(requestService.approveSubscription(caller.getUsername(), requestId, subscriptionApprovalRequest))
                );
    }

    @PostMapping(value = INTERNAL_PATH_APPROVE_SUBSCRIPTION_REQUESTS)
    public Mono<SubscriptionApprovalResponse> approveSubscription(
            @PathVariable(value = "patient-id") String patientId,
            @PathVariable(value = "request-id") String requestId,
            @Valid @RequestBody SubscriptionEditAndApprovalRequest subscriptionApprovalRequest) {
        return approvalRequestValidator.validateRequest(subscriptionApprovalRequest)
                .then(requestService.approveSubscription(patientId, requestId, subscriptionApprovalRequest));
    }

    @PostMapping(value = APP_PATH_DENY_SUBSCRIPTION_REQUESTS)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> denySubscription(@PathVariable(value = "request-id") String requestId) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .flatMap(caller -> requestService
                        .denySubscription(caller.getUsername(), requestId));
    }

    @PostMapping(value = SUBSCRIPTION_REQUEST_HIU_ON_NOTIFY)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> subscriptionRequestOnNotify(
            @Valid @RequestBody HIUSubscriptionRequestNotifyResponse response) {
        return just(response)
                .filterWhen(req ->
                        validator.validate(response.getRequestId().toString(), response.getTimestamp()))
                .switchIfEmpty(Mono.error(ClientError.tooManyRequests()))
                .doOnSuccess(requester -> defer(() -> {
                    validator.put(response.getRequestId().toString(), response.getTimestamp());
                    return requestService.subscriptionRequestOnNotify(response);
                }).subscriberContext(ctx -> {
                    Optional<String> correlationId = Optional.ofNullable(MDC.get(CORRELATION_ID));
                    return correlationId.map(id -> ctx.put(CORRELATION_ID, id))
                            .orElseGet(() -> ctx.put(CORRELATION_ID, UUID.randomUUID().toString()));
                }).subscribe())
                .then();
    }

    @PostMapping(value = SUBSCRIPTION_HIU_ON_NOTIFY)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> subscriptionOnNotify(
            @Valid @RequestBody HIUSubscriptionNotifyResponse response) {
        return just(response)
                .filterWhen(req ->
                        validator.validate(response.getRequestId().toString(), response.getTimestamp()))
                .switchIfEmpty(Mono.error(ClientError.tooManyRequests()))
                .doOnSuccess(requester -> defer(() -> {
                    validator.put(response.getRequestId().toString(), response.getTimestamp());
                    return requestService.subscriptionOnNotify(response);
                }).subscriberContext(ctx -> {
                    Optional<String> correlationId = Optional.ofNullable(MDC.get(CORRELATION_ID));
                    return correlationId.map(id -> ctx.put(CORRELATION_ID, id))
                            .orElseGet(() -> ctx.put(CORRELATION_ID, UUID.randomUUID().toString()));
                }).subscribe())
                .then();
    }

    private int getPageSize(int limit) {
        if (limit < 0) {
            return subscriptionProperties.getDefaultPageSize();
        }
        return Math.min(limit, subscriptionProperties.getMaxPageSize());
    }
}
