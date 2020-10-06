package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.common.ClientError;
import in.projecteka.datanotificationsubscription.common.RequestValidator;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.validation.Valid;

import static in.projecteka.datanotificationsubscription.common.Constants.PATH_SUBSCRIPTION_REQUEST_SUBSCRIBE;
import static reactor.core.publisher.Mono.error;

@RestController
@AllArgsConstructor
public class SubscriptionRequestController {
    private final SubscriptionRequestService requestService;
    private final RequestValidator validator;

    @PostMapping(value = PATH_SUBSCRIPTION_REQUEST_SUBSCRIBE )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> subscriptionRequest(
            @RequestBody @Valid SubscriptionRequest request) {
        return Mono.just(request)
                .filterWhen(req -> validator.validate(request.getRequestId().toString(), request.getTimestamp()))
                .switchIfEmpty(error(ClientError.tooManyRequests()))
                .flatMap(validatedRequest -> validator.put(request.getRequestId().toString(), request.getTimestamp())
                        .then(requestService.subscriptionRequest(request.getSubscription(), request.getRequestId())));
    }
}
