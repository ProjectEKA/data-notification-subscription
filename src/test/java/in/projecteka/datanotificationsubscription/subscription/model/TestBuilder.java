package in.projecteka.datanotificationsubscription.subscription.model;

import in.projecteka.datanotificationsubscription.clients.model.Links;
import in.projecteka.datanotificationsubscription.clients.model.PatientLinks;
import in.projecteka.datanotificationsubscription.clients.model.PatientLinksResponse;
import in.projecteka.datanotificationsubscription.clients.model.User;
import in.projecteka.datanotificationsubscription.common.model.ServiceInfo;
import in.projecteka.datanotificationsubscription.common.model.PatientCareContext;
import in.projecteka.datanotificationsubscription.hipLink.NewCCLinkEvent;
import in.projecteka.datanotificationsubscription.subscription.Subscription;
import org.jeasy.random.EasyRandom;

public class TestBuilder {
    private static final EasyRandom easyRandom = new EasyRandom();

    public static SubscriptionRequest.SubscriptionRequestBuilder subscriptionRequest() {
        return easyRandom.nextObject(SubscriptionRequest.SubscriptionRequestBuilder.class);
    }

    public static SubscriptionDetail.SubscriptionDetailBuilder subscriptionDetail() {
        return easyRandom.nextObject(SubscriptionDetail.SubscriptionDetailBuilder.class);
    }

    public static ServiceInfo.ServiceInfoBuilder serviceInfo() {
        return easyRandom.nextObject(ServiceInfo.ServiceInfoBuilder.class);
    }

    public static PatientCareContext.PatientCareContextBuilder patientCareContext() {
        return easyRandom.nextObject(PatientCareContext.PatientCareContextBuilder.class);
    }

    public static NewCCLinkEvent.NewCCLinkEventBuilder newCCLinkEvent() {
        return easyRandom.nextObject(NewCCLinkEvent.NewCCLinkEventBuilder.class);
    }

    public static Subscription.SubscriptionBuilder subscription() {
        return easyRandom.nextObject(Subscription.SubscriptionBuilder.class);
    }

    public static SubscriptionOnInitRequest.SubscriptionOnInitRequestBuilder subscriptionOnInitRequest() {
        return easyRandom.nextObject(SubscriptionOnInitRequest.SubscriptionOnInitRequestBuilder.class);
    }

    public static PatientLinksResponse.PatientLinksResponseBuilder patientLinksResponse() {
        return easyRandom.nextObject(PatientLinksResponse.PatientLinksResponseBuilder.class);
    }

    public static PatientLinks.PatientLinksBuilder patientLinks() {
        return easyRandom.nextObject(PatientLinks.PatientLinksBuilder.class);
    }

    public static AccessPeriod.AccessPeriodBuilder accessPeriod() {
        return easyRandom.nextObject(AccessPeriod.AccessPeriodBuilder.class);
    }

    public static GrantedSubscription.GrantedSubscriptionBuilder grantedSubscription() {
        return easyRandom.nextObject(GrantedSubscription.GrantedSubscriptionBuilder.class);
    }

    public static SubscriptionRequestDetails.SubscriptionRequestDetailsBuilder subscriptionRequestDetails() {
        return easyRandom.nextObject(SubscriptionRequestDetails.SubscriptionRequestDetailsBuilder.class);
    }

    public static SubscriptionResponse.SubscriptionResponseBuilder subscriptionResponseBuilder() {
        return easyRandom.nextObject(SubscriptionResponse.SubscriptionResponseBuilder.class);
    }

    public static SubscriptionEditAndApprovalRequest.SubscriptionEditAndApprovalRequestBuilder subscriptionEditAndApprovalRequestBuilder() {
        return easyRandom.nextObject(SubscriptionEditAndApprovalRequest.SubscriptionEditAndApprovalRequestBuilder.class);
    }

    public static SubscriptionSource.SubscriptionSourceBuilder subscriptionSourceBuilder() {
        return easyRandom.nextObject(SubscriptionSource.SubscriptionSourceBuilder.class);
    }

    public static HIUSubscriptionRequestNotifyRequest.HIUSubscriptionRequestNotifyRequestBuilder hiuSubscriptionRequestNotifyRequestBuilder() {
        return easyRandom.nextObject(HIUSubscriptionRequestNotifyRequest.HIUSubscriptionRequestNotifyRequestBuilder.class);
    }

    public static Links.LinksBuilder links() {
        return easyRandom.nextObject(Links.LinksBuilder.class);
    }

    public static User.UserBuilder user() {
        return easyRandom.nextObject(User.UserBuilder.class);
    }


    public static String string() {
        return easyRandom.nextObject(String.class);
    }

}
