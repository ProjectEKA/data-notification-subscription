package in.projecteka.datanotificationsubscription.common;

import java.time.LocalDateTime;

public class Constants {
    public static final String API_VERSION = "v1";
    public static final String BLOCK_LIST = "blockList";
    public static final String BLOCK_LIST_FORMAT = "%s:%s";
    public static final LocalDateTime DEFAULT_CACHE_VALUE = LocalDateTime.MIN;
    public static final String CORRELATION_ID = "CORRELATION-ID";

    //APIs
    public static final String CURRENT_VERSION = "/" + API_VERSION;
    public static final String PATH_HEARTBEAT = CURRENT_VERSION + "/heartbeat";
    public static final String HELLO_WORLD = CURRENT_VERSION + "/hello";
    public static final String PATH_SUBSCRIPTION_REQUEST_SUBSCRIBE = "/v0.5/subscription-requests/cm/init";
    public static final String PATH_SUBSCRIPTION_REQUESTS = CURRENT_VERSION+"/subscription-requests";
    public static final String GATEWAY_SESSIONS = "/sessions";

    //rabbitmq
    public static final String DUMMY_QUEUE = "dummy-queue";
    public static final String EXCHANGE = "dummy.exchange";
}
