package in.projecteka.dataavailabilitynotification.common;

import in.projecteka.dataavailabilitynotification.SampleNotificationPublisher;
import in.projecteka.dataavailabilitynotification.common.model.Message;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class SampleController {

    private static final Logger logger = LoggerFactory.getLogger(SampleController.class);
    private final SampleNotificationPublisher samplePublisher;

    @GetMapping(Constants.HELLO_WORLD)
    public Mono<ResponseEntity> helloWorld() {
        logger.info("Received a request for path: {}, method: {}", Constants.HELLO_WORLD, "GET");
        Message message = Message.builder().value("Hello World").build();
        logger.info(message.getValue());
        samplePublisher.broadcastNotification(message);
        return Mono.just(new ResponseEntity(HttpStatus.ACCEPTED));
    }
}
