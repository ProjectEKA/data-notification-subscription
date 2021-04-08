package in.projecteka.datanotificationsubscription.common;

import in.projecteka.consentmanager.common.TraceableMessage;
import in.projecteka.datanotificationsubscription.DestinationsConfig;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

import javax.annotation.PreDestroy;

import static in.projecteka.datanotificationsubscription.common.Constants.APP_PUSH_NOTIFICATION_QUEUE;
import static in.projecteka.datanotificationsubscription.common.Constants.CORRELATION_ID;
import static in.projecteka.datanotificationsubscription.common.Serializer.from;

@AllArgsConstructor
public class AppPushNotificationPublisher {
    private static final Logger logger = LoggerFactory.getLogger(AppPushNotificationPublisher.class);
    private final Sender sender;
    private final DestinationsConfig destinationsConfig;

    public Mono<Void> publish(PushNotificationData message) {
        return broadcastAppPushNotification(message);
    }

    private Mono<Void> broadcastAppPushNotification(PushNotificationData message) {
        DestinationsConfig.DestinationInfo destinationInfo = destinationsConfig.getQueues()
                .get(APP_PUSH_NOTIFICATION_QUEUE);

        return sendMessage(message, destinationInfo.getExchange(), destinationInfo.getRoutingKey())
                .doOnSuccess(unused -> logger.info("Broadcasting App push notification"));
    }

    private Mono<Void> sendMessage(Object message, String exchange, String routingKey) {
        TraceableMessage traceableMessage = TraceableMessage.builder()
                .correlationId(MDC.get(CORRELATION_ID))
                .message(message)
                .build();

        byte[] bytes = from(traceableMessage).getBytes();
        OutboundMessage outboundMessage = new OutboundMessage(exchange, routingKey, bytes);
        return sender.send(Mono.just(outboundMessage));
    }

    @PreDestroy
    public void closeConnection() {
        sender.close();
    }
}
