package in.projecteka.dataavailabilitynotification.common;

import in.projecteka.dataavailabilitynotification.common.model.HeartbeatResponse;
import in.projecteka.dataavailabilitynotification.common.model.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static java.time.LocalDateTime.now;
import static java.time.ZoneOffset.UTC;
import static org.springframework.http.HttpStatus.OK;

@RestController
public class HeartbeatController {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatController.class);

    @GetMapping(Constants.PATH_HEARTBEAT)
    public Mono<ResponseEntity<HeartbeatResponse>> heartbeat() {
        logger.info("Received a request for path: {}, method: {}", Constants.PATH_HEARTBEAT, "GET");
        var heartbeatResponse = HeartbeatResponse.builder().timeStamp(now(UTC)).status(Status.UP).build();
        logger.info("Heartbeat is healthy");
        return Mono.just(new ResponseEntity<>(heartbeatResponse, OK));
    }
}
