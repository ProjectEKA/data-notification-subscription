package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.common.DbOperationError;
import in.projecteka.datanotificationsubscription.subscription.model.ListResult;
import in.projecteka.datanotificationsubscription.subscription.model.RequestStatus;
import in.projecteka.datanotificationsubscription.subscription.model.Requester;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestDetails;
import io.vertx.core.AsyncResult;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static in.projecteka.datanotificationsubscription.common.Serializer.from;
import static in.projecteka.datanotificationsubscription.common.Serializer.to;

@AllArgsConstructor
public class SubscriptionRequestRepository {

    public static final String SUBSCRIPTION_DETAIL = "details";
    public static final String DATE_MODIFIED = "date_modified";
    public static final String DATE_CREATED = "date_created";
    public static final String STATUS = "status";
    public static final String REQUEST_ID = "request_id";
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRequestRepository.class);

    private static final String INSERT_SUBSCRIPTION_REQUEST_QUERY = "INSERT INTO hiu_subscription " +
            "(request_id, patient_id, status, details, requester_type) VALUES ($1, $2, $3, $4, $5)";

    private static final String GET_SUBSCRIPTION_REQUEST_QUERY = "SELECT details, request_id, status, date_created, date_modified, requester_type FROM "
            + "hiu_subscription WHERE patient_id=$1 and (status=$4 OR $4 IS NULL) " +
            "ORDER BY date_modified DESC" +
            " LIMIT $2 OFFSET $3";

    private static final String SELECT_SUBSCRIPTION_REQUEST_COUNT = "SELECT COUNT(*) FROM hiu_subscription " +
            "WHERE patient_id=$1 AND (status=$2 OR $2 IS NULL)";

    private static final String FAILED_TO_SAVE_SUBSCRIPTION_REQUEST = "Failed to save subscription request";

    private final PgPool readWriteClient;
    private final PgPool readOnlyClient;


    public Mono<Void> insert(SubscriptionDetail requestedDetail, UUID requestId) {
        return Mono.create(monoSink ->
                readWriteClient.preparedQuery(INSERT_SUBSCRIPTION_REQUEST_QUERY)
                        .execute(Tuple.of(requestId.toString(),
                                requestedDetail.getPatient().getId(),
                                RequestStatus.REQUESTED.name(),
                                new JsonObject(from(requestedDetail)), Requester.HEALTH_LOCKER.name()),
                                handler -> {
                                    if (handler.failed()) {
                                        logger.error(handler.cause().getMessage(), handler.cause());
                                        monoSink.error(new Exception(FAILED_TO_SAVE_SUBSCRIPTION_REQUEST));
                                        return;
                                    }
                                    monoSink.success();
                                }));
    }

    public Mono<ListResult<List<SubscriptionRequestDetails>>> getAllSubscriptionRequests(String username, int limit, int offset, String status) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(GET_SUBSCRIPTION_REQUEST_QUERY)
                .execute(Tuple.of(username, limit, offset, status), handler -> {
                    List<SubscriptionRequestDetails> subscriptions = getSubscriptionRequestRepresentation(handler);
                    readOnlyClient.preparedQuery(SELECT_SUBSCRIPTION_REQUEST_COUNT)
                            .execute(Tuple.of(username, status), counter -> {
                                if (counter.failed()) {
                                    logger.error(counter.cause().getMessage(), counter.cause());
                                    monoSink.error(new DbOperationError());
                                    return;
                                }
                                Integer count = counter.result().iterator().next().getInteger("count");
                                monoSink.success(new ListResult<>(subscriptions, count));
                            });
                }));
    }

    private List<SubscriptionRequestDetails> getSubscriptionRequestRepresentation(AsyncResult<RowSet<Row>> handler) {
        if (handler.failed()) {
            return new ArrayList<>();
        }
        List<SubscriptionRequestDetails> subscriptions = new ArrayList<>();
        RowSet<Row> results = handler.result();
        for (Row result : results) {
            subscriptions.add(getSubscriptionRequestRepresentation(result));
        }
        return subscriptions;
    }

    private SubscriptionRequestDetails getSubscriptionRequestRepresentation(Row row) {
        SubscriptionDetail subscriptionDetail = to(row.getValue(SUBSCRIPTION_DETAIL).toString(),
                SubscriptionDetail.class);

        return SubscriptionRequestDetails
                .builder()
                .createdAt(row.getLocalDateTime(DATE_CREATED))
                .lastUpdated(row.getLocalDateTime(DATE_MODIFIED))
                .hips(subscriptionDetail.getHips())
                .hiu(subscriptionDetail.getHiu())
                .id(to(row.getString(REQUEST_ID), UUID.class))
                .patient(subscriptionDetail.getPatient())
                .period(subscriptionDetail.getPeriod())
                .purpose(subscriptionDetail.getPurpose())
                .status(to(row.getString(STATUS), RequestStatus.class))
                .categories(subscriptionDetail.getCategories())
                .requester(to(row.getString("requester_type"),Requester.class))
                .build();

    }
}
