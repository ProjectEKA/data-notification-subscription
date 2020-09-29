package in.projecteka.datanotificationsubscription.common;

public class Constants {
    public static final String API_VERSION = "v1";

    //APIs
    public static final String CURRENT_VERSION = "/" + API_VERSION;
    public static final String PATH_HEARTBEAT = CURRENT_VERSION + "/heartbeat";
    public static final String HELLO_WORLD = CURRENT_VERSION + "/hello";
    public static final String PATH_SUBSCRIPTION_REQUEST_SUBSCRIBE = CURRENT_VERSION + "/subscriptions/subscribe";
    public static final String PATH_SUBSCRIPTION_REQUEST_ON_SUBSCRIBE = "/v0.5/subscriptions/on-subscribe";

    //rabbitmq
    public static final String DUMMY_QUEUE = "dummy-queue";
    public static final String EXCHANGE = "dummy.exchange";
}
