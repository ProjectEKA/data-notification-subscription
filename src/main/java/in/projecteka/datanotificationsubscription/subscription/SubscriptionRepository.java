package in.projecteka.datanotificationsubscription.subscription;

import com.fasterxml.jackson.core.type.TypeReference;
import in.projecteka.datanotificationsubscription.common.DbOperationError;
import in.projecteka.datanotificationsubscription.common.model.HIType;
import in.projecteka.datanotificationsubscription.common.model.RequesterType;
import in.projecteka.datanotificationsubscription.subscription.model.AccessPeriod;
import in.projecteka.datanotificationsubscription.subscription.model.Category;
import in.projecteka.datanotificationsubscription.subscription.model.HipDetail;
import in.projecteka.datanotificationsubscription.subscription.model.ListResult;
import in.projecteka.datanotificationsubscription.subscription.model.PatientDetail;
import in.projecteka.datanotificationsubscription.subscription.model.RequestStatus;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestDetails;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionStatus;
import io.vertx.core.AsyncResult;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static in.projecteka.datanotificationsubscription.common.Serializer.from;
import static in.projecteka.datanotificationsubscription.common.Serializer.to;

@AllArgsConstructor
public class SubscriptionRepository {
    private static final String GET_SUBSCRIPTIONS_FOR_PATIENT_BY_HIU_QUERY =
            "SELECT sr.subscription_id, sr.patient_id, sr.status as request_status, sr.date_created, sr.date_modified," +
                    " sr.details, sr.requester_type, s.hip_id, s.category_link, s.category_data, s.hi_types, s.period_from," +
                    " s.period_to, s.status as subscription_status, s.excluded FROM hiu_subscription sr INNER JOIN" +
                    " subscription_source s ON sr.subscription_id=s.subscription_id WHERE sr.patient_id=$1 and" +
                    " sr.details -> 'hiu' ->> 'id'=$2 ORDER BY date_modified DESC LIMIT $3 OFFSET $4";
    private static final String COUNT_SUBSCRIPTIONS_FOR_PATIENT_BY_HIU_QUERY =
            "SELECT count(sr.subscription_id) FROM hiu_subscription sr INNER JOIN subscription_source s " +
                    "ON sr.subscription_id=s.subscription_id WHERE sr.patient_id=$1 and " +
                    "sr.details -> 'hiu' ->> 'id'=$2";
    private static final String GET_SUBSCRIPTION_DETAILS_QUERY =
            "SELECT sr.subscription_id, sr.patient_id, sr.status as request_status, sr.date_created, sr.date_modified," +
                    " sr.details, sr.requester_type, s.hip_id, s.category_link, s.category_data, s.hi_types, s.period_from," +
                    " s.period_to, s.status as subscription_status, s.excluded FROM hiu_subscription sr INNER JOIN" +
                    " subscription_source s ON sr.subscription_id=s.subscription_id WHERE sr.subscription_id=$1 AND s.active=TRUE";
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRequestService.class);

    public static final String SUBSCRIPTION_ID = "subscription_id";
    public static final String DETAILS = "details";
    public static final String PATIENT_ID = "patient_id";
    public static final String REQUEST_STATUS = "request_status";
    public static final String DATE_CREATED = "date_created";
    public static final String DATE_MODIFIED = "date_modified";
    public static final String HIP_ID = "hip_id";
    public static final String HI_TYPES = "hi_types";
    public static final String SUBSCRIPTION_STATUS = "subscription_status";
    public static final String PERIOD_FROM = "period_from";
    public static final String PERIOD_TO = "period_to";
    public static final String CATEGORY_LINK = "category_link";
    public static final String CATEGORY_DATA = "category_data";
    public static final String REQUESTER_TYPE = "requester_type";
    public static final String EXCLUDED = "excluded";

    private final PgPool readOnlyClient;
    private final SubscriptionResponseMapper subscriptionResponseMapper;

    public Mono<ListResult<List<SubscriptionResponse>>> getSubscriptionsFor(String patientId, String hiuId, int limit, int offset) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(GET_SUBSCRIPTIONS_FOR_PATIENT_BY_HIU_QUERY)
                .execute(Tuple.of(patientId, hiuId, limit, offset), handler -> {
                    List<SubscriptionResponse> subscriptions = mapToSubscriptions(handler);
                    readOnlyClient.preparedQuery(COUNT_SUBSCRIPTIONS_FOR_PATIENT_BY_HIU_QUERY)
                            .execute(Tuple.of(patientId, hiuId), counter -> {
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

    public Mono<SubscriptionResponse> getSubscriptionDetailsForID(String subscriptionId) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(GET_SUBSCRIPTION_DETAILS_QUERY)
                .execute(Tuple.of(subscriptionId), handler -> {
                    if(handler.failed()){
                        logger.error(handler.cause().getMessage(), handler.cause());
                        monoSink.error(new DbOperationError());
                        return;
                    }
                    List<SubscriptionResponse> subscriptions = mapToSubscriptions(handler);
                    monoSink.success(subscriptions.get(0));
                }));
    }

    private List<SubscriptionResponse> mapToSubscriptions(AsyncResult<RowSet<Row>> handler) {
        if (handler.failed()) {
            return new ArrayList<>();
        }
        List<Row> rows = new ArrayList<>();
        handler.result().iterator().forEachRemaining(rows::add);
        return subscriptionResponseMapper.mapRowsToSubscriptionResponses(rows);
    }
}
