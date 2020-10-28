package in.projecteka.datanotificationsubscription.subscription.model;

import in.projecteka.datanotificationsubscription.common.ClientError;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;

/*TODO: Should be registered as spring hook instead of manual trigger
 */
public class SubscriptionApprovalRequestValidator {

    public Mono<Void> validateRequest(SubscriptionApprovalRequest approvalRequest) {
        if (CollectionUtils.isEmpty(approvalRequest.getSources())) {
            return Mono.error(ClientError.invalidSubscriptionApprovalRequest("Sources are not specified"));
        }
        if (approvalRequest.isApplicableForAllHIPs()) {
            if (approvalRequest.getSources().size() > 1) {
                return Mono.error(ClientError.invalidSubscriptionApprovalRequest("Only one source needed when applicable for all HIPs"));
            }
            if (approvalRequest.getSources().get(0).getHip() != null) {
                return Mono.error(ClientError.invalidSubscriptionApprovalRequest("HIP details are not allowed in sources when applicable for all HIPs"));
            }
            if (!CollectionUtils.isEmpty(approvalRequest.getExcludeSources()) && hasEmptyHIPs(approvalRequest.getExcludeSources())) {
                return Mono.error(ClientError.invalidSubscriptionApprovalRequest("HIP details cannot be empty in exclude list"));
            }
        }
        else {
            if (hasEmptyHIPs(approvalRequest.getSources())){
                return Mono.error(ClientError.invalidSubscriptionApprovalRequest("HIP details cannot be empty in source list"));
            }
            if (!CollectionUtils.isEmpty(approvalRequest.getExcludeSources())){
                return Mono.error(ClientError.invalidSubscriptionApprovalRequest("excludeSources is not allowed for individual HIPs"));
            }
        }
        return Mono.empty();
    }

    private boolean hasEmptyHIPs(List<GrantedSubscription> subscriptions) {
        return subscriptions
                .stream()
                .noneMatch(grantedSubscription -> grantedSubscription.getHip() == null || StringUtils.isEmpty(grantedSubscription.getHip().getId()));
    }
}
