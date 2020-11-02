package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.common.model.HIType;
import in.projecteka.datanotificationsubscription.common.model.RequesterType;
import in.projecteka.datanotificationsubscription.subscription.model.Category;
import in.projecteka.datanotificationsubscription.subscription.model.RequestStatus;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionStatus;
import in.projecteka.datanotificationsubscription.subscription.model.TestBuilder;
import io.vertx.sqlclient.Row;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static in.projecteka.datanotificationsubscription.common.Serializer.from;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.CATEGORY_DATA;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.CATEGORY_LINK;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.DETAILS;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.HIP_ID;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.HI_TYPES;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.PATIENT_ID;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.REQUESTER_TYPE;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.REQUEST_STATUS;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.SUBSCRIPTION_ID;
import static in.projecteka.datanotificationsubscription.subscription.SubscriptionRepository.SUBSCRIPTION_STATUS;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionResponseMapperTest {

    @Test
    void shouldMapRowsToSubscriptionResponse() {
        Row row1 = mock(Row.class);
        Row row2 = mock(Row.class);
        String subscriptionId = UUID.randomUUID().toString();
        SubscriptionDetail detail = TestBuilder.subscriptionDetail().build();

        when(row1.getString(SUBSCRIPTION_ID)).thenReturn(subscriptionId);
        when(row2.getString(SUBSCRIPTION_ID)).thenReturn(subscriptionId);
        when(row1.getValue(DETAILS)).thenReturn(from(detail));
        when(row2.getValue(DETAILS)).thenReturn(from(detail));
        when(row1.getString(REQUEST_STATUS)).thenReturn(RequestStatus.GRANTED.name());
        when(row2.getString(REQUEST_STATUS)).thenReturn(RequestStatus.GRANTED.name());
        when(row1.getString(REQUESTER_TYPE)).thenReturn(RequesterType.HIU.name());
        when(row2.getString(REQUESTER_TYPE)).thenReturn(RequesterType.HIU.name());
        when(row1.getString(PATIENT_ID)).thenReturn("test@ncg");
        when(row2.getString(PATIENT_ID)).thenReturn("test@ncg");

        when(row1.getString(SUBSCRIPTION_STATUS)).thenReturn(SubscriptionStatus.GRANTED.name());
        when(row2.getString(SUBSCRIPTION_STATUS)).thenReturn(SubscriptionStatus.GRANTED.name());
        when(row1.getString(HIP_ID)).thenReturn("10001");
        when(row2.getString(HIP_ID)).thenReturn("10002");
        when(row1.getBoolean(CATEGORY_DATA)).thenReturn(false);
        when(row1.getBoolean(CATEGORY_LINK)).thenReturn(true);
        when(row2.getBoolean(CATEGORY_DATA)).thenReturn(true);
        when(row2.getBoolean(CATEGORY_LINK)).thenReturn(true);
        when(row1.getValue(HI_TYPES)).thenReturn(from(asList(HIType.DIAGNOSTIC_REPORT)));
        when(row2.getValue(HI_TYPES)).thenReturn(from(asList(HIType.PRESCRIPTION)));

        SubscriptionResponseMapper subscriptionResponseMapper = new SubscriptionResponseMapper();
        List<SubscriptionResponse> responses = subscriptionResponseMapper.mapRowsToSubscriptionResponses(asList(row1, row2));

        assertThat(responses.size()).isEqualTo(1);
        assertThat(responses.get(0).getSubscriptionId().toString()).isEqualTo(subscriptionId);
        assertThat(responses.get(0).getPatient().getId()).isEqualTo("test@ncg");
        assertThat(responses.get(0).getRequester().getId()).isEqualTo(detail.getHiu().getId());
        assertThat(responses.get(0).getRequester().getType()).isEqualTo(RequesterType.HIU);
        assertThat(responses.get(0).getStatus()).isEqualTo(RequestStatus.GRANTED);

        List<SubscriptionResponse.SubscriptionSource> sources = responses.get(0).getSources();
        assertThat(sources.get(0).getCategories()).isEqualTo(asList(Category.LINK));
        assertThat(sources.get(1).getCategories()).isEqualTo(asList(Category.LINK, Category.DATA));
        assertThat(sources.get(0).getHipDetail().getId()).isEqualTo("10001");
        assertThat(sources.get(1).getHipDetail().getId()).isEqualTo("10002");
        assertThat(sources.get(0).getStatus()).isEqualTo(SubscriptionStatus.GRANTED);
        assertThat(sources.get(1).getStatus()).isEqualTo(SubscriptionStatus.GRANTED);
        assertThat(sources.get(0).getHiTypes()).isEqualTo(asList(HIType.DIAGNOSTIC_REPORT));
        assertThat(sources.get(1).getHiTypes()).isEqualTo(asList(HIType.PRESCRIPTION));
    }

    @Test
    void shouldCreatesDifferentSubscriptionResponseForEachSubscriptionId() {
        Row row1 = mock(Row.class);
        Row row2 = mock(Row.class);
        String subscriptionId1 = UUID.randomUUID().toString();
        String subscriptionId2 = UUID.randomUUID().toString();
        SubscriptionDetail detail = TestBuilder.subscriptionDetail().build();

        when(row1.getString(SUBSCRIPTION_ID)).thenReturn(subscriptionId1);
        when(row2.getString(SUBSCRIPTION_ID)).thenReturn(subscriptionId2);
        when(row1.getValue(DETAILS)).thenReturn(from(detail));
        when(row2.getValue(DETAILS)).thenReturn(from(detail));
        when(row1.getString(REQUEST_STATUS)).thenReturn(RequestStatus.GRANTED.name());
        when(row2.getString(REQUEST_STATUS)).thenReturn(RequestStatus.GRANTED.name());
        when(row1.getString(REQUESTER_TYPE)).thenReturn(RequesterType.HIU.name());
        when(row2.getString(REQUESTER_TYPE)).thenReturn(RequesterType.HIU.name());
        when(row1.getString(PATIENT_ID)).thenReturn("test@ncg");
        when(row2.getString(PATIENT_ID)).thenReturn("test@ncg");

        when(row1.getString(SUBSCRIPTION_STATUS)).thenReturn(SubscriptionStatus.GRANTED.name());
        when(row2.getString(SUBSCRIPTION_STATUS)).thenReturn(SubscriptionStatus.GRANTED.name());
        when(row1.getString(HIP_ID)).thenReturn("10001");
        when(row2.getString(HIP_ID)).thenReturn("10002");
        when(row1.getBoolean(CATEGORY_DATA)).thenReturn(false);
        when(row1.getBoolean(CATEGORY_LINK)).thenReturn(true);
        when(row2.getBoolean(CATEGORY_DATA)).thenReturn(true);
        when(row2.getBoolean(CATEGORY_LINK)).thenReturn(true);
        when(row1.getValue(HI_TYPES)).thenReturn(from(asList(HIType.DIAGNOSTIC_REPORT)));
        when(row2.getValue(HI_TYPES)).thenReturn(from(asList(HIType.PRESCRIPTION)));

        SubscriptionResponseMapper subscriptionResponseMapper = new SubscriptionResponseMapper();
        List<SubscriptionResponse> responses = subscriptionResponseMapper.mapRowsToSubscriptionResponses(asList(row1, row2));

        assertThat(responses.size()).isEqualTo(2);
        assertThat(responses.get(0).getSubscriptionId().toString()).isIn(subscriptionId1, subscriptionId2);
        assertThat(responses.get(1).getSubscriptionId().toString()).isIn(subscriptionId1, subscriptionId2);
    }
}