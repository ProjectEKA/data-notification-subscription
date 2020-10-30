package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.clients.model.User;
import in.projecteka.datanotificationsubscription.subscription.model.ListResult;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestDetails;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionResponse;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@AllArgsConstructor
public class SubscriptionService {
    private final UserServiceClient userServiceClient;
    private final SubscriptionRepository subscriptionRepository;

    public Mono<ListResult<List<SubscriptionResponse>>> getSubscriptionsFor(String patientId, String hiuId, int limit, int offset){
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
}
