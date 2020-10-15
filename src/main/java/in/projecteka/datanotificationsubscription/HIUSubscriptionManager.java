package in.projecteka.datanotificationsubscription;

import in.projecteka.datanotificationsubscription.hipLink.NewCCLinkEvent;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class HIUSubscriptionManager {

    public void notifySubscribers(NewCCLinkEvent ccLinkEvent) {
        //TODO: find out list of subscribers for category LINK, applicable for the period
        //if LINK is not there as a separate DB column, loop through the subscriptions and filter
        //WIP - resume when the subscription approval part is done
    }
}
