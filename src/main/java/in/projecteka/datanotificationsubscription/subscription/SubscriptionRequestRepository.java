package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.subscription.model.RequestStatus;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionDetail;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static in.projecteka.datanotificationsubscription.common.Serializer.from;

@AllArgsConstructor
public class SubscriptionRequestRepository {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRequestRepository.class);

    private static final String INSERT_SUBSCRIPTION_REQUEST_QUERY = "INSERT INTO subscription_request " +
            "(request_id, patient_id, status, details) VALUES ($1, $2, $3, $4)";
    private static final String FAILED_TO_SAVE_SUBSCRIPTION_REQUEST = "Failed to save subscription request";

    private final PgPool readWriteClient;
    private final PgPool readOnlyClient;


    public Mono<Void> insert(SubscriptionDetail requestedDetail, UUID requestId) {
        return Mono.create(monoSink ->
                readWriteClient.preparedQuery(INSERT_SUBSCRIPTION_REQUEST_QUERY)
                        .execute(Tuple.of(requestId.toString(),
                                requestedDetail.getPatient().getId(),
                                RequestStatus.REQUESTED.name(),
                                new JsonObject(from(requestedDetail))),
                                handler -> {
                                    if (handler.failed()) {
                                        logger.error(handler.cause().getMessage(), handler.cause());
                                        monoSink.error(new Exception(FAILED_TO_SAVE_SUBSCRIPTION_REQUEST));
                                        return;
                                    }
                                    monoSink.success();
                                }));
    }
}
