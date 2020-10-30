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
    private static final String SUBSCRIPTION_ID = "subscription_id";


    private static final String GET_SUBSCRIPTIONS_FOR_PATIENT_BY_HIU_QUERY =
            "SELECT sr.subscription_id, sr.patient_id, sr.status as request_status, sr.date_created, sr.date_modified," +
                    " sr.details, sr.requester_type, s.hip_id, s.category_link, s.category_data, s.hi_types, s.period_from," +
                    " s.period_to, s.status as subscription_status FROM hiu_subscription sr INNER JOIN" +
                    " subscription_source s ON sr.subscription_id=s.subscription_id WHERE sr.patient_id=$1 and" +
                    " sr.details -> 'hiu' ->> 'id'=$2 ORDER BY date_modified DESC LIMIT $3 OFFSET $4";
    private static final String COUNT_SUBSCRIPTIONS_FOR_PATIENT_BY_HIU_QUERY =
            "SELECT count(sr.subscription_id) FROM hiu_subscription sr INNER JOIN subscription_source s " +
                    "ON sr.subscription_id=s.subscription_id WHERE sr.patient_id=$1 and " +
                    "sr.details -> 'hiu' ->> 'id'=$2";
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRequestService.class);

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

    private final PgPool readOnlyClient;

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

    private List<SubscriptionResponse> mapToSubscriptions(AsyncResult<RowSet<Row>> handler) {
        if (handler.failed()) {
            return new ArrayList<>();
        }
        List<Row> rows = new ArrayList<>();
        handler.result().iterator().forEachRemaining(rows::add);
        Map<String, List<Row>> rowBySubscriptionId = rows.stream()
                .collect(Collectors.groupingBy(row -> row.getString(SUBSCRIPTION_ID)));

        return rowBySubscriptionId.entrySet().stream()
                .map(entry -> getSubscription(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }


    private SubscriptionResponse getSubscription(String subscriptionId, List<Row> rowsForId) {
        Row firstRow = rowsForId.get(0); //Few details are common for each row
        SubscriptionDetail subscriptionDetail = to(firstRow.getValue(DETAILS).toString(),
                SubscriptionDetail.class);

        return SubscriptionResponse.builder()
                .subscriptionId(UUID.fromString(subscriptionId))
                .patient(PatientDetail.builder().id(firstRow.getString(PATIENT_ID)).build())
                .purpose(subscriptionDetail.getPurpose())
                .status(RequestStatus.valueOf(firstRow.getString(REQUEST_STATUS)))
                .dateCreated(firstRow.getLocalDateTime(DATE_CREATED))
                .dateGranted(firstRow.getLocalDateTime(DATE_MODIFIED)) //should be stored separately
                .requester(getRequester(firstRow, subscriptionDetail))
                .sources(rowsForId.stream().map(this::fromRow).collect(Collectors.toList()))
                .build();
    }

    private SubscriptionResponse.SubscriptionSource fromRow(Row row) {
        return SubscriptionResponse.SubscriptionSource.builder()
                .hipDetail(HipDetail.builder().id(row.getString(HIP_ID)).build())
                .hiTypes(to(row.getValue(HI_TYPES).toString(), new TypeReference<>() {
                }))
                .categories(getCategories(row))
                .status(SubscriptionStatus.valueOf(row.getString(SUBSCRIPTION_STATUS)))
                .period(AccessPeriod.builder()
                        .fromDate(row.getLocalDateTime(PERIOD_FROM))
                        .toDate(row.getLocalDateTime(PERIOD_TO))
                        .build())
                .build();
    }

    private List<Category> getCategories(Row row) {
        ArrayList<Category> categories = new ArrayList<>();
        if (row.getBoolean(CATEGORY_LINK)){
            categories.add(Category.LINK);
        }
        if (row.getBoolean(CATEGORY_DATA)){
            categories.add(Category.DATA);
        }
        return categories;
    }

    private SubscriptionResponse.Requester getRequester(Row firstRow, SubscriptionDetail subscriptionDetail) {
        return SubscriptionResponse.Requester
                .builder()
                .id(subscriptionDetail.getHiu().getId())
                .name(subscriptionDetail.getHiu().getName())
                .type(RequesterType.valueOf(firstRow.getString(REQUESTER_TYPE)))
                .build();
    }
}
