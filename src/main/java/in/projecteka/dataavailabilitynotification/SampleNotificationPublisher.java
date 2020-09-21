package in.projecteka.dataavailabilitynotification;

import in.projecteka.dataavailabilitynotification.common.Constants;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class SampleNotificationPublisher {
    private static final Logger logger = LoggerFactory.getLogger(SampleNotificationPublisher.class);
    private final AmqpTemplate amqpTemplate;
    private final DestinationsConfig destinationsConfig;

    @SneakyThrows
    public Mono<Void> broadcastNotification(String message) {
        var destinationInfo = destinationsConfig.getQueues().get(Constants.DUMMY_QUEUE);

        return Mono.create(monoSink -> {
            amqpTemplate.convertAndSend(destinationInfo.getExchange(), destinationInfo.getRoutingKey(), message);
            logger.info("Broadcasting notification");
            monoSink.success();
        });
    }
}
