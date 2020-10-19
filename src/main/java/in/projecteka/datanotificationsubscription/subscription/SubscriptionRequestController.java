package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.common.Caller;
import in.projecteka.datanotificationsubscription.common.ClientError;
import in.projecteka.datanotificationsubscription.common.RequestValidator;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionNotifyResponse;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionRequestNotifyRequest;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionRequestNotifyResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionApprovalRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionApprovalResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionProperties;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestsRepresentation;
import lombok.AllArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.validation.Valid;

import java.util.Optional;
import java.util.UUID;

import static in.projecteka.datanotificationsubscription.common.Constants.APP_PATH_APPROVE_SUBSCRIPTION_REQUESTS;
import static in.projecteka.datanotificationsubscription.common.Constants.APP_PATH_SUBSCRIPTION_REQUESTS;
import static in.projecteka.datanotificationsubscription.common.Constants.CORRELATION_ID;
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

    @PostMapping(value = PATH_SUBSCRIPTION_REQUEST_SUBSCRIBE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> subscriptionRequest(
            @RequestBody @Valid SubscriptionRequest request) {
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

    @PostMapping(value = APP_PATH_APPROVE_SUBSCRIPTION_REQUESTS)
    public Mono<SubscriptionApprovalResponse> approveSubscription(
            @PathVariable(value = "request-id") String requestId,
            @Valid @RequestBody SubscriptionApprovalRequest subscriptionApprovalRequest) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .flatMap(caller -> requestService
                        .approveSubscription(caller.getUsername(), requestId, subscriptionApprovalRequest.getSources()));
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
