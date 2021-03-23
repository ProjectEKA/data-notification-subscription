package in.projecteka.datanotificationsubscription.subscription;

import com.google.common.collect.Sets;
import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.clients.model.User;
import in.projecteka.datanotificationsubscription.common.ClientError;
import in.projecteka.datanotificationsubscription.subscription.model.GrantedSubscription;
import in.projecteka.datanotificationsubscription.subscription.model.ListResult;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionEditAndApprovalRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionSource;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
public class SubscriptionService {
    private final UserServiceClient userServiceClient;
    private final SubscriptionRepository subscriptionRepository;

    public Mono<ListResult<List<SubscriptionResponse>>> getSubscriptionsFor(String patientId, String hiuId, int limit, int offset) {
        //TODO: Cache findPatient
        return userServiceClient.userOf(patientId)
                .flatMap(user -> {
                    String patientIdentifier = getPatientId(patientId, user);
                    return subscriptionRepository.getSubscriptionsFor(patientIdentifier, hiuId, limit, offset);
                });
    }

    private String getPatientId(String username, User user) {
        return StringUtils.isEmpty(user.getHealthIdNumber())
                ? username :
                user.getHealthIdNumber();
    }

    public Mono<SubscriptionResponse> getSubscriptionDetailsForID(String subscriptionId) {
        return subscriptionRepository.getSubscriptionDetailsForID(subscriptionId, true)
                        .switchIfEmpty(Mono.error(ClientError.subscriptionRequestNotFound()));
    }

    public Mono<Void> editSubscription(String subscriptionId, SubscriptionEditAndApprovalRequest subscriptionEditRequest) {
        return subscriptionRepository.getSubscriptionDetailsForID(subscriptionId, false)
                .switchIfEmpty(Mono.error(ClientError.subscriptionRequestNotFound()))
                .flatMap(subscriptionResponse -> {
                    if (subscriptionEditRequest.isApplicableForAllHIPs()) {
                        return this.editSubscriptionApplicableForAllHIPs(subscriptionId, subscriptionEditRequest, subscriptionResponse);
                    }
                    return this.editSubscriptionNotApplicableForAllHIPs(subscriptionId, subscriptionEditRequest, subscriptionResponse);
                });
    }

    private Mono<Void> editSubscriptionNotApplicableForAllHIPs(String subscriptionId,
                                                               SubscriptionEditAndApprovalRequest subscriptionEditRequest,
                                                               SubscriptionResponse subscriptionResponse) {
        Set<String> hipsToBeSetAsInactive = getHIPsToBeDeactivated(subscriptionResponse, subscriptionEditRequest.getIncludedSources());
        return subscriptionRepository.editSubscriptionNotApplicableForAllHIPs(subscriptionId,
                subscriptionEditRequest.getIncludedSources(), hipsToBeSetAsInactive);
    }

    private Mono<Void> editSubscriptionApplicableForAllHIPs(String subscriptionId,
                                                            SubscriptionEditAndApprovalRequest subscriptionEditRequest,
                                                            SubscriptionResponse subscriptionResponse) {
        Set<String> hipsToBeSetAsInactive = getHIPsToBeDeactivated(subscriptionResponse, subscriptionEditRequest.getExcludedSources());
        var includedSource = subscriptionEditRequest.getIncludedSources().get(0);

        return subscriptionRepository.editSubscriptionApplicableForAllHIPs(subscriptionId, hipsToBeSetAsInactive,
                includedSource, subscriptionEditRequest.getExcludedSources());
    }

    private Set<String> getHIPsToBeDeactivated(SubscriptionResponse subscriptionResponse, List<GrantedSubscription> grantedSubscriptions) {
        Set<String> existingHipEntries = getAllExistingHIPsFromSources(subscriptionResponse);
        var currentExcludedHips = getHIPIdsFromGrantedSubscriptions(grantedSubscriptions);
        return Sets.difference(existingHipEntries, currentExcludedHips);
    }

    private Set<String> getAllExistingHIPsFromSources(SubscriptionResponse subscriptionResponse) {
        ArrayList<SubscriptionSource> allExistingSources = new ArrayList<>();
        allExistingSources.addAll(subscriptionResponse.getIncludedSources());
        allExistingSources.addAll(subscriptionResponse.getExcludedSources());

        return getHIPIdsFromSources(allExistingSources);
    }

    private Set<String> getHIPIdsFromSources(List<SubscriptionSource> subscriptionSources) {
        return subscriptionSources.stream().map(source -> source.getHip().getId()).collect(Collectors.toSet());
    }

    private Set<String> getHIPIdsFromGrantedSubscriptions(List<GrantedSubscription> grantedSubscriptions) {
        return grantedSubscriptions.stream().map(subscription -> subscription.getHip().getId()).collect(Collectors.toSet());
    }
}
