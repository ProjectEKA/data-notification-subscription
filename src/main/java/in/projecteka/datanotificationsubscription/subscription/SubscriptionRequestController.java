package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.common.Caller;
import in.projecteka.datanotificationsubscription.common.ClientError;
import in.projecteka.datanotificationsubscription.common.RequestValidator;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionApprovalRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionApprovalResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionProperties;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestsRepresentation;
import lombok.AllArgsConstructor;
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

import static in.projecteka.datanotificationsubscription.common.Constants.APP_PATH_APPROVE_SUBSCRIPTION_REQUESTS;
import static in.projecteka.datanotificationsubscription.common.Constants.PATH_SUBSCRIPTION_REQUESTS;
import static reactor.core.publisher.Mono.error;

@RestController
@AllArgsConstructor
public class SubscriptionRequestController {
    private final SubscriptionRequestService requestService;
    private final RequestValidator validator;
    private final SubscriptionProperties subscriptionProperties;

    @PostMapping(value = PATH_SUBSCRIPTION_REQUESTS)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> subscriptionRequest(
            @RequestBody @Valid SubscriptionRequest request) {
        return Mono.just(request)
                .filterWhen(req -> validator.validate(request.getRequestId().toString(), request.getTimestamp()))
                .switchIfEmpty(error(ClientError.tooManyRequests()))
                .flatMap(validatedRequest -> validator.put(request.getRequestId().toString(), request.getTimestamp())
                        .then(requestService.subscriptionRequest(request.getSubscription(), request.getRequestId())));
    }

    @GetMapping(value = PATH_SUBSCRIPTION_REQUESTS)
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
    public Mono<SubscriptionApprovalResponse> approveConsent(
            @PathVariable(value = "request-id") String requestId,
            @Valid @RequestBody SubscriptionApprovalRequest subscriptionApprovalRequest) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .flatMap(caller -> requestService
                        .approveSubscription(caller.getUsername(), requestId, subscriptionApprovalRequest.getSources()));
    }

    private int getPageSize(int limit) {
        if (limit < 0) {
            return subscriptionProperties.getDefaultPageSize();
        }
        return Math.min(limit, subscriptionProperties.getMaxPageSize());
    }
}
