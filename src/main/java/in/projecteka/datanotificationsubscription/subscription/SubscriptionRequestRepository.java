package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.common.DbOperationError;
import in.projecteka.datanotificationsubscription.common.model.RequesterType;
import in.projecteka.datanotificationsubscription.subscription.model.GrantedSubscription;
import in.projecteka.datanotificationsubscription.subscription.model.HipDetail;
import in.projecteka.datanotificationsubscription.subscription.model.HiuDetail;
import in.projecteka.datanotificationsubscription.subscription.model.ListResult;
import in.projecteka.datanotificationsubscription.subscription.model.PatientDetail;
import in.projecteka.datanotificationsubscription.subscription.model.RequestStatus;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestDetails;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static in.projecteka.datanotificationsubscription.common.Constants.INCLUDE_ALL_HIPS_CODE;
import static in.projecteka.datanotificationsubscription.common.Serializer.from;
import static in.projecteka.datanotificationsubscription.common.Serializer.to;

@AllArgsConstructor
public class SubscriptionRequestRepository {

    public static final String SUBSCRIPTION_DETAIL = "details";
    public static final String DATE_MODIFIED = "date_modified";
    public static final String DATE_CREATED = "date_created";
    public static final String STATUS = "status";
    public static final String REQUEST_ID = "request_id";
    private static final String REQUESTER_TYPE = "requester_type";
    private static final String UNKNOWN_ERROR_OCCURRED = "Unknown error occurred";
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRequestRepository.class);

    private static final String INSERT_SUBSCRIPTION_REQUEST_QUERY = "INSERT INTO hiu_subscription " +
            "(request_id, patient_id, status, details, requester_type) VALUES ($1, $2, $3, $4, $5)";

    private static final String INSERT_SOURCES_REQUEST_QUERY = "INSERT INTO subscription_source " +
            "(subscription_id, period_from, period_to, category_link, category_data, hip_id, hi_types, status, excluded) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)";

    private static final String GET_SUBSCRIPTION_REQUEST_QUERY = "SELECT details, request_id, status, date_created, date_modified, requester_type FROM "
            + "hiu_subscription WHERE patient_id=$1 and (status=$4 OR $4 IS NULL) " +
            "ORDER BY date_modified DESC" +
            " LIMIT $2 OFFSET $3";

    private static final String GET_PATIENT_SUBSCRIPTION_REQUEST_QUERY = "SELECT details, request_id, status, date_created, date_modified, requester_type FROM "
            + "hiu_subscription WHERE patient_id=$1 and (status=$4 OR $4 IS NULL) and requester_type=$5 " +
            "ORDER BY date_modified DESC" +
            " LIMIT $2 OFFSET $3";

    private static final String GET_ACTIVE_LINK_SUBSCRIPTION_QUERY = "SELECT hs.request_id, hs.patient_id, hs.subscription_id, " +
            "hs.details -> 'hiu' -> 'id' AS hiu_id, ss.hip_id, ss.excluded FROM hiu_subscription hs INNER JOIN subscription_source ss " +
            "ON hs.subscription_id = ss.subscription_id WHERE hs.patient_id=$1 AND hs.status=$2 AND (ss.hip_id=$3 OR ss.hip_id IS NULL) " +
            "AND ss.status=$4 AND ss.category_link=$5 AND ss.period_from<=$6 AND ss.period_to>= $7";

    private static final String SELECT_SUBSCRIPTION_REQUEST_COUNT = "SELECT COUNT(*) FROM hiu_subscription " +
            "WHERE patient_id=$1 AND (status=$2 OR $2 IS NULL)";

    private static final String SELECT_PATIENT_SUBSCRIPTION_REQUEST_COUNT = "SELECT COUNT(*) FROM hiu_subscription " +
            "WHERE patient_id=$1 AND (status=$2 OR $2 IS NULL) AND requester_type=$3";

    private static final String FAILED_TO_SAVE_SUBSCRIPTION_REQUEST = "Failed to save subscription request";

    private static final String FAILED_TO_SAVE_SOURCES = "Failed to save sources table";

    private static final String SELECT_SUBSCRIPTION_REQUEST_BY_ID_AND_STATUS = "SELECT request_id, status, details, requester_type, date_created, date_modified FROM hiu_subscription " +
            "where request_id=$1 and status=$2 and patient_id=$3";

    private static final String UPDATE_SUBSCRIPTION_REQUEST_STATUS_QUERY = "UPDATE hiu_subscription SET status=$1, " +
            "subscription_id=$2, date_modified=$3 WHERE request_id=$4";

    private final PgPool readWriteClient;
    private final PgPool readOnlyClient;


    public Mono<Void> insert(SubscriptionDetail requestedDetail, UUID requestId, RequesterType type, String patientId) {
        return Mono.create(monoSink ->
                readWriteClient.preparedQuery(INSERT_SUBSCRIPTION_REQUEST_QUERY)
                        .execute(Tuple.of(requestId.toString(),
                                patientId,
                                RequestStatus.REQUESTED.name(),
                                new JsonObject(from(requestedDetail)),
                                type.name()),
                                handler -> {
                                    if (handler.failed()) {
                                        logger.error(handler.cause().getMessage(), handler.cause());
                                        monoSink.error(new Exception(FAILED_TO_SAVE_SUBSCRIPTION_REQUEST));
                                        return;
                                    }
                                    monoSink.success();
                                }));
    }

    public Mono<Void> insertIntoSubscriptionSource(String subscriptionId, GrantedSubscription grantedSubscription, boolean excluded) {
        return Mono.create(monoSink ->
                readWriteClient.preparedQuery(INSERT_SOURCES_REQUEST_QUERY)
                        .execute(Tuple.of(subscriptionId,
                                grantedSubscription.getPeriod().getFromDate(),
                                grantedSubscription.getPeriod().getToDate(),
                                grantedSubscription.isLinkCategory(),
                                grantedSubscription.isDataCategory(),
                                getHIPId(grantedSubscription),
                                new JsonArray(from(grantedSubscription.getHiTypes())),
                                SubscriptionStatus.GRANTED.name(),
                                excluded
                                ),
                                handler -> {
                                    if (handler.failed()) {
                                        logger.error(handler.cause().getMessage(), handler.cause());
                                        monoSink.error(new Exception(FAILED_TO_SAVE_SOURCES));
                                        return;
                                    }
                                    monoSink.success();
                                }));
    }

    private String getHIPId(GrantedSubscription grantedSubscription) {
        if (grantedSubscription.getHip() == null || StringUtils.isEmpty(grantedSubscription.getHip().getId())) {
            return null;
        }
        return grantedSubscription.getHip().getId();
    }

    public Mono<ListResult<List<SubscriptionRequestDetails>>> getAllSubscriptionRequests(String patientId, int limit, int offset, String status) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(GET_SUBSCRIPTION_REQUEST_QUERY)
                .execute(Tuple.of(patientId, limit, offset, status), handler -> {
                    List<SubscriptionRequestDetails> subscriptions = getSubscriptionRequestRepresentation(handler);
                    readOnlyClient.preparedQuery(SELECT_SUBSCRIPTION_REQUEST_COUNT)
                            .execute(Tuple.of(patientId, status), counter -> {
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
                .id(UUID.fromString(row.getString(REQUEST_ID)))
                .patient(subscriptionDetail.getPatient())
                .period(subscriptionDetail.getPeriod())
                .purpose(subscriptionDetail.getPurpose())
                .status(RequestStatus.valueOf(row.getString(STATUS)))
                .categories(subscriptionDetail.getCategories())
                .requesterType(RequesterType.valueOf(row.getString(REQUESTER_TYPE)))
                .build();
    }

    public Mono<Void> updateHIUSubscription(String requestId, String subscriptionId, String status) {
        return Mono.create(monoSink -> readWriteClient.preparedQuery(UPDATE_SUBSCRIPTION_REQUEST_STATUS_QUERY)
                .execute(Tuple.of(status, subscriptionId, LocalDateTime.now(ZoneOffset.UTC), requestId),
                        updateHandler -> {
                            if (updateHandler.failed()) {
                                monoSink.error(new Exception("Failed to update status", updateHandler.cause()));
                                return;
                            }
                            monoSink.success();
                        }));
    }

    public Mono<SubscriptionRequestDetails> requestOf(String requestId, String status, String patientId) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(SELECT_SUBSCRIPTION_REQUEST_BY_ID_AND_STATUS)
                .execute(Tuple.of(requestId, status, patientId),
                        subscriptionRequestHandler(monoSink)));
    }

    private Handler<AsyncResult<RowSet<Row>>> subscriptionRequestHandler(MonoSink<SubscriptionRequestDetails> monoSink) {
        return handler -> {
            if (handler.failed()) {
                logger.error(handler.cause().getMessage(), handler.cause());
                monoSink.error(new RuntimeException(UNKNOWN_ERROR_OCCURRED));
                return;
            }
            RowSet<Row> results = handler.result();
            SubscriptionRequestDetails subscriptionRequestDetails = null;
            for (Row result : results) {
                subscriptionRequestDetails = getSubscriptionRequestRepresentation(result);
            }
            monoSink.success(subscriptionRequestDetails);
        };
    }

    public Mono<List<Subscription>> findLinkSubscriptionsFor(String patientId, String hipId) {
        LocalDateTime currentTimestamp = LocalDateTime.now(ZoneOffset.UTC);
        Tuple parameters = Tuple.of(patientId, RequestStatus.GRANTED.name(), hipId,
                SubscriptionStatus.GRANTED.name(), true, currentTimestamp, currentTimestamp);
        return Mono.create(monoSink -> {
            readOnlyClient.preparedQuery(GET_ACTIVE_LINK_SUBSCRIPTION_QUERY)
                    .execute(parameters, subscriptionRowHandler(monoSink));
        });
    }

    private Handler<AsyncResult<RowSet<Row>>> subscriptionRowHandler(MonoSink<List<Subscription>> monoSink) {
        return handler -> {
            if (handler.failed()) {
                logger.error(handler.cause().getMessage(), handler.cause());
                monoSink.error(new DbOperationError());
                return;
            }
            var iterator = handler.result().iterator();
            if (!iterator.hasNext()) {
                monoSink.success();
                return;
            }
            List<Subscription> subscriptions = new ArrayList<>();
            RowSet<Row> rows = handler.result();
            for (Row row : rows) {
                Subscription subscription = Subscription.builder()
                        .id(UUID.fromString(row.getString("subscription_id")))
                        .hip(buildHip(row.getString("hip_id")))
                        .patient(PatientDetail.builder().id(row.getString("patient_id")).build())
                        .hiu(HiuDetail.builder().id(row.getString("hiu_id")).build())
                        .excluded(row.getBoolean("excluded"))
                        .build();
                subscriptions.add(subscription);
            }
            monoSink.success(subscriptions);
        };
    }

    private HipDetail buildHip(String hipId) {
        if (StringUtils.isEmpty(hipId)){
            return null;
        }
        return HipDetail.builder().id(hipId).build();
    }

    public Mono<ListResult<List<SubscriptionRequestDetails>>> getPatientSubscriptionRequests(String patientId, int limit, int offset, String status, RequesterType requesterType) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(GET_PATIENT_SUBSCRIPTION_REQUEST_QUERY)
                .execute(Tuple.of(patientId, limit, offset, status, requesterType.toString()), handler -> {
                    List<SubscriptionRequestDetails> subscriptions = getSubscriptionRequestRepresentation(handler);
                    readOnlyClient.preparedQuery(SELECT_PATIENT_SUBSCRIPTION_REQUEST_COUNT)
                            .execute(Tuple.of(patientId, status, requesterType.toString()), counter -> {
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
}
