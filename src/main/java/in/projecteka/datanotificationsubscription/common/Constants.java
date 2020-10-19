package in.projecteka.datanotificationsubscription.common;

import java.time.LocalDateTime;

public class Constants {
    public static final String API_VERSION = "v0.5";
    public static final String BLOCK_LIST = "blockList";
    public static final String BLOCK_LIST_FORMAT = "%s:%s";
    public static final LocalDateTime DEFAULT_CACHE_VALUE = LocalDateTime.MIN;
    public static final String CORRELATION_ID = "CORRELATION-ID";
    public static final String HDR_HIP_ID = "X-HIP-ID";
    public static final String HDR_HIU_ID = "X-HIU-ID";

    //APIs
    public static final String CURRENT_VERSION = "/" + API_VERSION;
    public static final String PATH_HEARTBEAT = CURRENT_VERSION + "/heartbeat";
    public static final String PATH_SUBSCRIPTION_REQUEST_SUBSCRIBE = "/v0.5/subscription-requests/cm/init";
    public static final String APP_PATH_SUBSCRIPTION_REQUESTS = "/subscription-requests";
    public static final String GATEWAY_SESSIONS = "/sessions";
    public static final String APP_PATH_APPROVE_SUBSCRIPTION_REQUESTS = "/subscription-requests/{request-id}/approve";
    public static final String AUTHORIZATION = "Authorization";

    public static final String SUBSCRIPTION_REQUEST_INIT_URL_PATH = "/subscription-requests/cm/on-init";
    public static final String GET_SERVICE_INFO = "/hi-services/%s";
    public static final String SUBSCRIPTION_HIU_NOTIFY = "/subscriptions/hiu/notify";
    public static final String SUBSCRIPTION_REQUEST_HIU_NOTIFY = "/subscription-requests/hiu/notify";
    public static final String SUBSCRIPTION_REQUEST_HIU_ON_NOTIFY = "/v0.5/subscription-requests/hiu/on-notify";
    public static final String SUBSCRIPTION_HIU_ON_NOTIFY = "/v0.5/subscriptions/hiu/on-notify";


    //rabbitmq
    public static final String DUMMY_QUEUE = "dummy-queue";
    public static final String EXCHANGE = "dummy.exchange";
    public static final String HIP_LINK_QUEUE = "cm-hip-link-queue";
    public static final String CM_EXCHANGE = "exchange";
}
