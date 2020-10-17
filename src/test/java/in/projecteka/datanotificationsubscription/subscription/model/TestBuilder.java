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
