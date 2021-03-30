package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.common.Caller;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionEditAndApprovalRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionEditAndApprovalRequestValidator;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionProperties;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionsRepresentation;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.validation.Valid;

import static in.projecteka.datanotificationsubscription.common.Constants.APP_PATH_EDIT_SUBSCRIPTION;
import static in.projecteka.datanotificationsubscription.common.Constants.APP_PATH_SUBSCRIPTION_DETAILS;
import static in.projecteka.datanotificationsubscription.common.Constants.INTERNAL_PATH_SUBSCRIPTIONS;

@RestController
@AllArgsConstructor
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final SubscriptionProperties subscriptionProperties;
    private final SubscriptionEditAndApprovalRequestValidator subscriptionEditRequestValidator;

    @GetMapping(value = INTERNAL_PATH_SUBSCRIPTIONS)
    public Mono<SubscriptionsRepresentation> getSubscriptions(@RequestParam String patientId,
                                                              @RequestParam String hiuId,
                                                              @RequestParam(defaultValue = "-1") int limit,
                                                              @RequestParam(defaultValue = "0") int offset) {
        int pageSize = getPageSize(limit);
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .flatMap(caller -> subscriptionService.getSubscriptionsFor(patientId, hiuId, pageSize, offset))

                .map(subscriptions -> SubscriptionsRepresentation.builder()
                        .requests(subscriptions.getResult())
                        .size(subscriptions.getTotal())
                        .limit(pageSize)
                        .offset(offset).build());
    }

    private int getPageSize(int limit) {
        if (limit < 0) {
            return subscriptionProperties.getDefaultPageSize();
        }
        return Math.min(limit, subscriptionProperties.getMaxPageSize());
    }

    @GetMapping(value = APP_PATH_SUBSCRIPTION_DETAILS)
    public Mono<SubscriptionResponse> getSubscriptionDetails(@PathVariable("subscription-id") String subscriptionId) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .flatMap(caller -> subscriptionService.getSubscriptionDetailsForID(subscriptionId));
    }

    @PutMapping(value = APP_PATH_EDIT_SUBSCRIPTION)
    public Mono<Void> editSubscription(@PathVariable(value = "subscription-id") String subscriptionId,
                                       @Valid @RequestBody SubscriptionEditAndApprovalRequest subscriptionEditRequest) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .flatMap(caller -> subscriptionEditRequestValidator.validateRequest(subscriptionEditRequest)
                        .then(subscriptionService.editSubscription(subscriptionId, subscriptionEditRequest)));
    }
}
