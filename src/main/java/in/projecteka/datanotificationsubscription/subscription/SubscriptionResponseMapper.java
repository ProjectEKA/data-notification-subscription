package in.projecteka.datanotificationsubscription.subscription;

import com.fasterxml.jackson.core.type.TypeReference;
import in.projecteka.datanotificationsubscription.common.model.RequesterType;
import in.projecteka.datanotificationsubscription.subscription.model.AccessPeriod;
import in.projecteka.datanotificationsubscription.subscription.model.Category;
import in.projecteka.datanotificationsubscription.subscription.model.HipDetail;
import in.projecteka.datanotificationsubscription.subscription.model.PatientDetail;
import in.projecteka.datanotificationsubscription.subscription.model.RequestStatus;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionStatus;
import io.vertx.sqlclient.Row;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static in.projecteka.datanotificationsubscription.common.Serializer.to;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.CATEGORY_DATA;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.CATEGORY_LINK;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.DATE_CREATED;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.DATE_MODIFIED;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.DETAILS;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.HIP_ID;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.HI_TYPES;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.PATIENT_ID;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.PERIOD_FROM;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.PERIOD_TO;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.REQUESTER_TYPE;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.REQUEST_STATUS;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.SUBSCRIPTION_ID;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.SUBSCRIPTION_STATUS;

public class SubscriptionResponseMapper {
    public List<SubscriptionResponse> mapRowsToSubscriptionResponses(List<Row> rows) {
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
                .hipDetail(getHip(row))
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

    private HipDetail getHip(Row row) {
        //Check for ALL once that is merged
        return HipDetail.builder().id(row.getString(HIP_ID)).build();
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
