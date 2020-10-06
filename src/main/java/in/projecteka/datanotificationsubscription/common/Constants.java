package in.projecteka.datanotificationsubscription.common;

public class Constants {
    public static final String API_VERSION = "v1";

    //APIs
    public static final String CURRENT_VERSION = "/" + API_VERSION;
    public static final String PATH_HEARTBEAT = CURRENT_VERSION + "/heartbeat";
    public static final String HELLO_WORLD = CURRENT_VERSION + "/hello";

    //rabbitmq
    public static final String DUMMY_QUEUE = "dummy-queue";
    public static final String EXCHANGE = "dummy.exchange";
    public static final String HIP_LINK_QUEUE = "cm-hip-link-queue";
    public static final String CM_EXCHANGE = "exchange";
}
