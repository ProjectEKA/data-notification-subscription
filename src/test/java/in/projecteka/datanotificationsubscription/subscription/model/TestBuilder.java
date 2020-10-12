package in.projecteka.datanotificationsubscription.subscription.model;

import in.projecteka.datanotificationsubscription.clients.model.User;
import org.jeasy.random.EasyRandom;

public class TestBuilder {
    private static final EasyRandom easyRandom = new EasyRandom();

    public static SubscriptionRequest.SubscriptionRequestBuilder subscriptionRequest() {
        return easyRandom.nextObject(SubscriptionRequest.SubscriptionRequestBuilder.class);
    }

    public static User.UserBuilder user() {
        return easyRandom.nextObject(User.UserBuilder.class);
    }


    public static String string() {
        return easyRandom.nextObject(String.class);
    }

}
