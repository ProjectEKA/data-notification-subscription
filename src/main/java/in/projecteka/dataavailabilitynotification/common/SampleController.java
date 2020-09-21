package in.projecteka.dataavailabilitynotification.common;

import in.projecteka.dataavailabilitynotification.SampleNotificationPublisher;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class SampleController {

    private static final Logger logger = LoggerFactory.getLogger(SampleController.class);
    private final SampleNotificationPublisher samplePublisher;

    @PostMapping(Constants.HELLO_WORLD)
    public Mono<ResponseEntity> helloWorld() {
        logger.info("Received a request for path: {}, method: {}", Constants.HELLO_WORLD, "GET");
        logger.info("Hello World");
        samplePublisher.broadcastNotification("Hello World").subscribe();
        return Mono.just(new ResponseEntity(HttpStatus.ACCEPTED));
    }
}
