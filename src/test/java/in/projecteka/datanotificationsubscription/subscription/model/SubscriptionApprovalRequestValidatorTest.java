package in.projecteka.datanotificationsubscription.subscription.model;

import in.projecteka.datanotificationsubscription.common.ClientError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.grantedSubscription;
import static java.util.Arrays.asList;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

class SubscriptionApprovalRequestValidatorTest {
     SubscriptionApprovalRequestValidator subscriptionApprovalRequestValidator;

    @BeforeEach
    void setUp() {
        subscriptionApprovalRequestValidator = new SubscriptionApprovalRequestValidator();
    }

    @Test
    void shouldErrorOutWhenSourcesAreNotProvided() {
        SubscriptionApprovalRequest approvalRequest = SubscriptionApprovalRequest.builder()
                .build();

        Mono<Void> request = subscriptionApprovalRequestValidator.validateRequest(approvalRequest);
        StepVerifier.create(request)
                .expectErrorMatches(e -> matchingClientError(e, "Sources are not specified"))
                .verify();
    }

    @Test
    void shouldErrorOutWhenMultipleSourceAndApplicableForAllHIPs() {
        GrantedSubscription subscription1 = grantedSubscription().build();
        GrantedSubscription subscription2 = grantedSubscription().build();
        SubscriptionApprovalRequest approvalRequest = SubscriptionApprovalRequest.builder()
                .isApplicableForAllHIPs(true)
                .includedSources(asList(subscription1, subscription2))
                .build();

        Mono<Void> request = subscriptionApprovalRequestValidator.validateRequest(approvalRequest);
        StepVerifier.create(request)
                .expectErrorMatches(e -> matchingClientError(e, "Only one source needed when applicable for all HIPs"))
                .verify();
    }

    @Test
    void shouldErrorOutWhenSourceHIPsAreProvidedAndApplicableForAllHIPs() {
        GrantedSubscription subscription1 = grantedSubscription().hip(HipDetail.builder().id("1244").build()).build();
        SubscriptionApprovalRequest approvalRequest = SubscriptionApprovalRequest.builder()
                .isApplicableForAllHIPs(true)
                .includedSources(asList(subscription1))
                .build();

        Mono<Void> request = subscriptionApprovalRequestValidator.validateRequest(approvalRequest);
        StepVerifier.create(request)
                .expectErrorMatches(e -> matchingClientError(e, "HIP details are not allowed in sources when applicable for all HIPs"))
                .verify();
    }

    @Test
    void shouldErrorOutWhenHipDetailsAreNotProvidedInExcludeList() {
        GrantedSubscription subscription1 = grantedSubscription().hip(null).build();
        GrantedSubscription subscription2 = grantedSubscription().hip(null).build();

        SubscriptionApprovalRequest approvalRequest = SubscriptionApprovalRequest.builder()
                .isApplicableForAllHIPs(true)
                .includedSources(asList(subscription1))
                .excludedSources(asList(subscription2))
                .build();

        Mono<Void> request = subscriptionApprovalRequestValidator.validateRequest(approvalRequest);
        StepVerifier.create(request)
                .expectErrorMatches(e -> matchingClientError(e, "HIP details cannot be empty in exclude list"))
                .verify();
    }

    @Test
    void shouldErrorOutWhenHIPDetailsAreNotProvidedInSourcesAndNotApplicableForAllHIPs() {
        GrantedSubscription subscription1 = grantedSubscription().build();
        GrantedSubscription subscription2 = grantedSubscription().hip(null).build();

        SubscriptionApprovalRequest approvalRequest = SubscriptionApprovalRequest.builder()
                .isApplicableForAllHIPs(false)
                .includedSources(asList(subscription1, subscription2))
                .build();

        Mono<Void> request = subscriptionApprovalRequestValidator.validateRequest(approvalRequest);
        StepVerifier.create(request)
                .expectErrorMatches(e -> matchingClientError(e, "HIP details cannot be empty in source list"))
                .verify();
    }

    @Test
    void shouldErrorOutWhenExcludeListIsProvidedButNotApplicableForAllHIPs() {
        GrantedSubscription subscription1 = grantedSubscription().build();
        GrantedSubscription subscription2 = grantedSubscription().build();

        SubscriptionApprovalRequest approvalRequest = SubscriptionApprovalRequest.builder()
                .isApplicableForAllHIPs(false)
                .includedSources(asList(subscription1))
                .excludedSources(asList(subscription2))
                .build();

        Mono<Void> request = subscriptionApprovalRequestValidator.validateRequest(approvalRequest);
        StepVerifier.create(request)
                .expectErrorMatches(e -> matchingClientError(e, "excludeSources is not allowed for individual HIPs"))
                .verify();
    }

    private boolean matchingClientError(Throwable e, String message) {
        return (e instanceof ClientError) && ((ClientError) e).getHttpStatus() == BAD_REQUEST
                && ((ClientError) e).getError().getError().getMessage().equals(message);
    }
}