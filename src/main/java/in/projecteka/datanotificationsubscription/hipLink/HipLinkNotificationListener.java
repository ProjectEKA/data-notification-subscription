package in.projecteka.datanotificationsubscription.hipLink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.projecteka.datanotificationsubscription.HIUSubscriptionManager;
import in.projecteka.datanotificationsubscription.MessageListenerContainerFactory;
import in.projecteka.datanotificationsubscription.SampleNotificationPublisher;
import in.projecteka.consentmanager.common.TraceableMessage;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import javax.annotation.PostConstruct;

import static in.projecteka.datanotificationsubscription.common.Constants.HIP_LINK_QUEUE;

@AllArgsConstructor
public class HipLinkNotificationListener {
    private static final Logger logger = LoggerFactory.getLogger(SampleNotificationPublisher.class);
    private final MessageListenerContainerFactory messageListenerContainerFactory;
    private final Jackson2JsonMessageConverter converter;
    private final HIUSubscriptionManager subscriptionManager;

    @PostConstruct
    public void subscribe() {
        var mlc = messageListenerContainerFactory.createMessageListenerContainer(HIP_LINK_QUEUE);
        MessageListener messageListener = message -> {
            try {
                TraceableMessage traceableMessage = (TraceableMessage) converter.fromMessage(message);
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());
                NewCCLinkEvent hipLinkEvent = mapper.convertValue(traceableMessage.getMessage(), NewCCLinkEvent.class);
                logger.debug("Received message {}", traceableMessage);
                subscriptionManager.notifySubscribers(hipLinkEvent);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
            }
        };
        mlc.setupMessageListener(messageListener);
        mlc.start();
    }
}
