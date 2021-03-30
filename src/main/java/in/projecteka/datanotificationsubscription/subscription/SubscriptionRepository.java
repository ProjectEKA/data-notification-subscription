package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.common.DbOperationError;
import in.projecteka.datanotificationsubscription.subscription.model.GrantedSubscription;
import in.projecteka.datanotificationsubscription.subscription.model.ListResult;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.json.JsonArray;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static in.projecteka.datanotificationsubscription.common.Serializer.from;

@AllArgsConstructor
public class SubscriptionRepository {
    private static final String GET_SUBSCRIPTIONS_FOR_PATIENT_BY_HIU_QUERY =
            "SELECT sr.subscription_id, sr.request_id, sr.patient_id, sr.status as request_status, sr.date_created, sr.date_modified," +
                    " sr.details, sr.requester_type, s.hip_id, s.category_link, s.category_data, s.hi_types, s.period_from," +
                    " s.period_to, s.status as subscription_status, s.excluded FROM hiu_subscription sr INNER JOIN" +
                    " subscription_source s ON sr.subscription_id=s.subscription_id WHERE sr.patient_id=$1 and" +
                    " sr.details -> 'hiu' ->> 'id'=$2 AND s.active = true ORDER BY date_modified DESC LIMIT $3 OFFSET $4";
    private static final String COUNT_SUBSCRIPTIONS_FOR_PATIENT_BY_HIU_QUERY =
            "SELECT count(sr.subscription_id) FROM hiu_subscription sr INNER JOIN subscription_source s " +
                    "ON sr.subscription_id=s.subscription_id WHERE sr.patient_id=$1 and " +
                    "sr.details -> 'hiu' ->> 'id'=$2 AND s.active = true";
    private static final String GET_SUBSCRIPTION_DETAILS_QUERY =
            "SELECT sr.subscription_id, sr.request_id, sr.patient_id, sr.status as request_status, sr.date_created, sr.date_modified," +
                    " sr.details, sr.requester_type, s.hip_id, s.category_link, s.category_data, s.hi_types, s.period_from," +
                    " s.period_to, s.status as subscription_status, s.excluded FROM hiu_subscription sr INNER JOIN" +
                    " subscription_source s ON sr.subscription_id=s.subscription_id WHERE sr.subscription_id=$1 AND (s.active = true OR $2 = false)";

    private static final String DEACTIVATE_SUBSCRIPTION_SOURCES = "UPDATE subscription_source SET active = false" +
            " WHERE (hip_id IN ( %s ) OR hip_id is NULL) AND subscription_id = $1";

    private static final String UPSERT_SUBSCRIPTION_SOURCE = "INSERT INTO subscription_source " +
            "(subscription_id, status, category_link, category_data, hip_id, excluded, period_from, period_to, hi_types) " +
            "VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9) " +
            "ON CONFLICT (COALESCE(hip_id, ''), subscription_id) " +
            "DO UPDATE SET active = true, hi_types = EXCLUDED.hi_types, category_link = EXCLUDED.category_link, " +
            "category_data = EXCLUDED.category_data, excluded = EXCLUDED.excluded, period_from = EXCLUDED.period_from, " +
            "period_to = EXCLUDED.period_to";

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRequestService.class);

    public static final String SUBSCRIPTION_ID = "subscription_id";
    public static final String SUBSCRIPTION_REQUEST_ID = "request_id";
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

    public static final String FAILED_TO_UPDATE_SUBSCRIPTION_SOURCES = "Failed to update subscription sources";

    private final PgPool readOnlyClient;
    private final PgPool readWriteClient;
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

    public Mono<SubscriptionResponse> getSubscriptionDetailsForID(String subscriptionId, boolean onlyActive) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(GET_SUBSCRIPTION_DETAILS_QUERY)
                .execute(Tuple.of(subscriptionId, onlyActive), handler -> {
                    if (handler.failed()) {
                        logger.error(handler.cause().getMessage(), handler.cause());
                        monoSink.error(new DbOperationError());
                        return;
                    }

                    if(handler.result().rowCount() == 0){
                        monoSink.success();
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

    public Mono<Void> editSubscriptionNotApplicableForAllHIPs(String subscriptionId,
                                                              List<GrantedSubscription> includedSources,
                                                              Set<String> hipsToBeDeactivated) {
        var batch = includedSources.stream().map(source -> toTuple(source, subscriptionId, false))
                .collect(Collectors.toList());
        return editSubscriptionSourcesInBatch(subscriptionId, hipsToBeDeactivated, batch);
    }

    public Mono<Void> editSubscriptionApplicableForAllHIPs(String subscriptionId,
                                                           Set<String> hipsToBeDeactivated,
                                                           GrantedSubscription includedSource,
                                                           List<GrantedSubscription> excludedSources) {
        var batch = excludedSources.stream().map(source -> toTuple(source, subscriptionId, true))
                .collect(Collectors.toList());

        batch.add(toTuple(includedSource, subscriptionId, false));

        return editSubscriptionSourcesInBatch(subscriptionId, hipsToBeDeactivated, batch);
    }

    private Mono<Void> editSubscriptionSourcesInBatch(String subscriptionId, Set<String> hipsToBeDeactivated, List<Tuple> batch) {
        String hipsInClause = joinByComma(hipsToBeDeactivated);

        if(hipsInClause.isEmpty()) {
            hipsInClause = null;
        }

        var queryToDeactivateSources = String.format(DEACTIVATE_SUBSCRIPTION_SOURCES, hipsInClause);

        return Mono.create(monoSink ->
                readWriteClient.withTransaction(client -> client
                        .preparedQuery(queryToDeactivateSources)
                        .execute(Tuple.of(subscriptionId))
                        .flatMap(discard -> client
                                .preparedQuery(UPSERT_SUBSCRIPTION_SOURCE)
                                .executeBatch(batch)
                        ))
                        .onSuccess(result -> {
                            monoSink.success();
                        })
                        .onFailure(err -> {
                            logger.error(FAILED_TO_UPDATE_SUBSCRIPTION_SOURCES + " ---> {}", err.getMessage());
                            monoSink.error(new Exception(FAILED_TO_UPDATE_SUBSCRIPTION_SOURCES));
                        })
        );
    }

    private Tuple toTuple(GrantedSubscription source, String subscriptionId, boolean isExcluded){
        return Tuple.of(subscriptionId,
                SubscriptionStatus.GRANTED.toString(),
                source.isLinkCategory(),
                source.isDataCategory(),
                getHIPId(source),
                isExcluded,
                source.getPeriod().getFromDate(),
                source.getPeriod().getToDate(),
                new JsonArray(from(source.getHiTypes())));
    }

    private String joinByComma(Collection<String> list) {
        return list.stream().map(e -> String.format("'%s'", e)).collect(Collectors.joining(", "));
    }

    private String getHIPId(GrantedSubscription grantedSubscription) {
        if (grantedSubscription.getHip() == null || StringUtils.isEmpty(grantedSubscription.getHip().getId())) {
            return null;
        }
        return grantedSubscription.getHip().getId();
    }
}
