package in.projecteka.datanotificationsubscription.common;

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

    @PostMapping(Constants.HELLO_WORLD)
    public Mono<ResponseEntity> helloWorld() {
        logger.info("Received a request for path: {}, method: {}", Constants.HELLO_WORLD, "GET");
        logger.info("Hello World");
        return Mono.just(new ResponseEntity(HttpStatus.ACCEPTED));
    }
}
