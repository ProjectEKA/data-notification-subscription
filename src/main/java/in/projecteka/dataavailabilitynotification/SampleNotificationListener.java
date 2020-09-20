package in.projecteka.dataavailabilitynotification;

import in.projecteka.dataavailabilitynotification.common.model.Message;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import javax.annotation.PostConstruct;

import static in.projecteka.dataavailabilitynotification.common.Constants.DUMMY_QUEUE;

@AllArgsConstructor
public class SampleNotificationListener {
    private static final Logger logger = LoggerFactory.getLogger(SampleNotificationPublisher.class);
    private final MessageListenerContainerFactory messageListenerContainerFactory;
    private final Jackson2JsonMessageConverter converter;

    @PostConstruct
    public void subscribe() {
        var mlc = messageListenerContainerFactory.createMessageListenerContainer(DUMMY_QUEUE);
        MessageListener messageListener = message -> {
            try {
                Message receivedMessage = (Message) converter.fromMessage(message);
                logger.info("Received message {}", receivedMessage);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
            }
        };
        mlc.setupMessageListener(messageListener);
        mlc.start();
    }
}
