package in.projecteka.datanotificationsubscription.subscription;

import in.projecteka.datanotificationsubscription.ConceptValidator;
import in.projecteka.datanotificationsubscription.clients.LinkServiceClient;
import in.projecteka.datanotificationsubscription.clients.UserServiceClient;
import in.projecteka.datanotificationsubscription.clients.model.Links;
import in.projecteka.datanotificationsubscription.clients.model.PatientLinks;
import in.projecteka.datanotificationsubscription.clients.model.PatientLinksResponse;
import in.projecteka.datanotificationsubscription.clients.model.User;
import in.projecteka.datanotificationsubscription.common.ClientError;
import in.projecteka.datanotificationsubscription.common.GatewayServiceClient;
import in.projecteka.datanotificationsubscription.common.model.HIType;
import in.projecteka.datanotificationsubscription.common.model.RequesterType;
import in.projecteka.datanotificationsubscription.common.model.ServiceInfo;
import in.projecteka.datanotificationsubscription.subscription.model.AccessPeriod;
import in.projecteka.datanotificationsubscription.subscription.model.GrantedSubscription;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionRequestNotifyRequest;
import in.projecteka.datanotificationsubscription.subscription.model.HipDetail;
import in.projecteka.datanotificationsubscription.subscription.model.HiuDetail;
import in.projecteka.datanotificationsubscription.subscription.model.PatientDetail;
import in.projecteka.datanotificationsubscription.subscription.model.RequestStatus;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionApprovalResponse;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionDetail;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionOnInitRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionProperties;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionRequestDetails;
import in.projecteka.datanotificationsubscription.subscription.model.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static in.projecteka.datanotificationsubscription.common.ClientError.userNotFound;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.accessPeriod;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.grantedSubscription;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.links;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.patientLinks;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.patientLinksResponse;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.serviceInfo;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.subscriptionDetail;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.subscriptionRequestDetails;
import static in.projecteka.datanotificationsubscription.subscription.model.TestBuilder.user;
import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static reactor.core.publisher.Mono.error;

class SubscriptionRequestServiceTest {
    @Mock
    private SubscriptionRequestRepository subscriptionRequestRepository;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private GatewayServiceClient gatewayServiceClient;
    @Mock
    private LinkServiceClient linkServiceClient;
    @Mock
    private ConceptValidator conceptValidator;
    @Mock
    private SubscriptionProperties subscriptionProperties;

    private SubscriptionRequestService subscriptionRequestService;

    @BeforeEach
    void setUp() {
        initMocks(this);
        subscriptionRequestService = new SubscriptionRequestService(subscriptionRequestRepository, userServiceClient,
                gatewayServiceClient, linkServiceClient, conceptValidator, subscriptionProperties);
    }

    @Test
    void shouldSaveSubscriptionRequestAndNotifySuccess() {
        ArgumentCaptor<SubscriptionOnInitRequest> captor = ArgumentCaptor.forClass(SubscriptionOnInitRequest.class);

        String healthId = "test@ncg";
        SubscriptionDetail subscriptionDetail = subscriptionDetail()
                .patient(PatientDetail.builder().id(healthId).build())
                .hiu(HiuDetail.builder().id("hiu-id").build())
                .build();
        UUID gatewayRequestId = UUID.randomUUID();


        User user = user().identifier(healthId).healthIdNumber("124").build();
        when(userServiceClient.userOf(anyString())).thenReturn(Mono.just(user));
        when(subscriptionRequestRepository.insert(any(SubscriptionDetail.class), any(UUID.class), any(), anyString())).thenReturn(Mono.empty());
        when(gatewayServiceClient.subscriptionRequestOnInit(any(SubscriptionOnInitRequest.class), anyString())).thenReturn(Mono.empty());
        when(gatewayServiceClient.getServiceInfo(anyString())).thenReturn(Mono.just(serviceInfo().type(RequesterType.HEALTH_LOCKER).build()));

        Mono<Void> result = subscriptionRequestService.subscriptionRequest(subscriptionDetail, gatewayRequestId);
        StepVerifier.create(result).expectComplete().verify();

        verify(userServiceClient, times(1)).userOf(healthId);
        verify(subscriptionRequestRepository, times(1)).insert(eq(subscriptionDetail), any(UUID.class), eq(RequesterType.HEALTH_LOCKER), eq(user.getHealthIdNumber()));
        verify(gatewayServiceClient, times(1)).subscriptionRequestOnInit(captor.capture(), eq("hiu-id"));

        SubscriptionOnInitRequest captorValue = captor.getValue();
        assertThat(captorValue.getError()).isNull();
        assertThat(captorValue.getSubscriptionRequest().getId()).isNotNull();
    }

    @Test
    void shouldUseUserIdAsPatientIdIfHealthNumberIsNotPresent() {
        ArgumentCaptor<SubscriptionOnInitRequest> captor = ArgumentCaptor.forClass(SubscriptionOnInitRequest.class);

        String healthId = "test@ncg";
        SubscriptionDetail subscriptionDetail = subscriptionDetail()
                .patient(PatientDetail.builder().id(healthId).build())
                .hiu(HiuDetail.builder().id("hiu-id").build())
                .build();
        UUID gatewayRequestId = UUID.randomUUID();

        User user = user().identifier(healthId).healthIdNumber(null).build();
        when(userServiceClient.userOf(anyString())).thenReturn(Mono.just(user));
        when(subscriptionRequestRepository.insert(any(SubscriptionDetail.class), any(UUID.class), any(), anyString())).thenReturn(Mono.empty());
        when(gatewayServiceClient.subscriptionRequestOnInit(any(SubscriptionOnInitRequest.class), anyString())).thenReturn(Mono.empty());
        when(gatewayServiceClient.getServiceInfo(anyString())).thenReturn(Mono.just(serviceInfo().type(RequesterType.HEALTH_LOCKER).build()));

        Mono<Void> result = subscriptionRequestService.subscriptionRequest(subscriptionDetail, gatewayRequestId);
        StepVerifier.create(result).expectComplete().verify();

        verify(userServiceClient, times(1)).userOf(healthId);
        verify(subscriptionRequestRepository, times(1)).insert(eq(subscriptionDetail), any(UUID.class), eq(RequesterType.HEALTH_LOCKER), eq(healthId));
        verify(gatewayServiceClient, times(1)).subscriptionRequestOnInit(captor.capture(), eq("hiu-id"));

        SubscriptionOnInitRequest captorValue = captor.getValue();
        assertThat(captorValue.getError()).isNull();
        assertThat(captorValue.getSubscriptionRequest().getId()).isNotNull();
    }

    @Test
    void shouldNotSaveAndNotifyWhenPatientIsNotFound() {
        String healthId = "test@ncg";
        SubscriptionDetail subscriptionDetail = subscriptionDetail()
                .patient(PatientDetail.builder().id(healthId).build())
                .hiu(HiuDetail.builder().id("hiu-id").build())
                .build();
        UUID gatewayRequestId = UUID.randomUUID();

        when(userServiceClient.userOf(anyString())).thenReturn(error(userNotFound()));
        when(subscriptionRequestRepository.insert(any(SubscriptionDetail.class), any(UUID.class), any(), anyString())).thenReturn(Mono.empty());
        when(gatewayServiceClient.subscriptionRequestOnInit(any(SubscriptionOnInitRequest.class), anyString())).thenReturn(Mono.empty());

        Mono<Void> result = subscriptionRequestService.subscriptionRequest(subscriptionDetail, gatewayRequestId);
        StepVerifier.create(result)
                .expectErrorMatches(e -> (e instanceof ClientError) && ((ClientError) e).getHttpStatus() == BAD_REQUEST)
                .verify();

        verify(userServiceClient, times(1)).userOf(healthId);
        verify(subscriptionRequestRepository, never()).insert(any(), any(), eq(RequesterType.HEALTH_LOCKER), anyString());
        verify(gatewayServiceClient, never()).subscriptionRequestOnInit(any(), any());
    }

    @Test
    void shouldAutoPopulateListOfHipsFromPatientLinksIfNotProvided() {
        ArgumentCaptor<SubscriptionDetail> captor = ArgumentCaptor.forClass(SubscriptionDetail.class);

        String healthId = "test@ncg";
        SubscriptionDetail subscriptionDetail = subscriptionDetail()
                .patient(PatientDetail.builder().id(healthId).build())
                .hips(new ArrayList<>())
                .hiu(HiuDetail.builder().id("hiu-id").build())
                .build();

        UUID gatewayRequestId = UUID.randomUUID();
        Links firstLink = links().build();
        Links secondLink = links().build();
        PatientLinks patientLinks = patientLinks().links(asList(firstLink, secondLink)).build();
        PatientLinksResponse patientLinksResponse = patientLinksResponse().patient(patientLinks).build();

        User user = user().build();
        when(userServiceClient.userOf(anyString())).thenReturn(Mono.just(user));
        when(linkServiceClient.getUserLinks(anyString())).thenReturn(Mono.just(patientLinksResponse));
        when(subscriptionRequestRepository.insert(any(SubscriptionDetail.class), any(UUID.class), any(), anyString())).thenReturn(Mono.empty());
        when(gatewayServiceClient.subscriptionRequestOnInit(any(SubscriptionOnInitRequest.class), anyString())).thenReturn(Mono.empty());
        when(gatewayServiceClient.getServiceInfo(anyString())).thenReturn(Mono.just(serviceInfo().type(RequesterType.HEALTH_LOCKER).build()));

        Mono<Void> result = subscriptionRequestService.subscriptionRequest(subscriptionDetail, gatewayRequestId);
        StepVerifier.create(result).expectComplete().verify();

        verify(linkServiceClient, times(1)).getUserLinks(healthId);
        verify(subscriptionRequestRepository, times(1)).insert(captor.capture(), any(UUID.class), eq(RequesterType.HEALTH_LOCKER), eq(user.getHealthIdNumber()));

        List<HipDetail> hips = captor.getValue().getHips();
        assertThat(hips.size()).isEqualTo(2);
        assertThat(hips.get(0)).isEqualTo(firstLink.getHip());
        assertThat(hips.get(1)).isEqualTo(secondLink.getHip());
    }

    @Test
    void shouldFetchRequesterDetailsFromGatewayProfile() {
        ArgumentCaptor<SubscriptionDetail> captor = ArgumentCaptor.forClass(SubscriptionDetail.class);

        UUID gatewayRequestId = UUID.randomUUID();
        String healthId = "test@ncg";
        SubscriptionDetail subscriptionDetail = subscriptionDetail()
                .patient(PatientDetail.builder().id(healthId).build())
                .hiu(HiuDetail.builder().id("hiu-id").name(null).build())
                .build();

        ServiceInfo serviceInfo = serviceInfo()
                .id("hiu-id")
                .name("hiu-name")
                .type(RequesterType.HEALTH_LOCKER)
                .build();

        when(userServiceClient.userOf(anyString())).thenReturn(Mono.just(user().build()));
        when(subscriptionRequestRepository.insert(any(SubscriptionDetail.class), any(UUID.class), eq(RequesterType.HEALTH_LOCKER), anyString())).thenReturn(Mono.empty());
        when(gatewayServiceClient.subscriptionRequestOnInit(any(SubscriptionOnInitRequest.class), anyString())).thenReturn(Mono.empty());
        when(gatewayServiceClient.getServiceInfo(anyString())).thenReturn(Mono.just(serviceInfo));

        Mono<Void> result = subscriptionRequestService.subscriptionRequest(subscriptionDetail, gatewayRequestId);
        StepVerifier.create(result).expectComplete().verify();

        verify(gatewayServiceClient, times(1)).getServiceInfo("hiu-id");
        verify(subscriptionRequestRepository, times(1)).insert(captor.capture(), any(UUID.class), eq(RequesterType.HEALTH_LOCKER), anyString());

        HiuDetail hiu = captor.getValue().getHiu();
        assertThat(hiu.getName()).isEqualTo("hiu-name");
    }

    @Test
    void shouldApproveSubscriptionApprovalAndNotifyHIU() {
        ArgumentCaptor<HIUSubscriptionRequestNotifyRequest> requestCaptor = ArgumentCaptor.forClass(HIUSubscriptionRequestNotifyRequest.class);
        ArgumentCaptor<GrantedSubscription> grantedSubscriptionCaptor = ArgumentCaptor.forClass(GrantedSubscription.class);
        ArgumentCaptor<String> hiuIdCaptor = ArgumentCaptor.forClass(String.class);

        String username = "test@ncg";
        String requestId = UUID.randomUUID().toString();

        AccessPeriod accessPeriod = accessPeriod().fromDate(now().minusYears(1)).toDate(now().plusYears(1)).build();
        GrantedSubscription subscription1 = grantedSubscription().period(accessPeriod).build();
        GrantedSubscription subscription2 = grantedSubscription().period(accessPeriod).build();
        SubscriptionRequestDetails subscriptionRequestDetails = subscriptionRequestDetails()
                .createdAt(now())
                .build();

        List<GrantedSubscription> grantedSubscriptions = asList(subscription1, subscription2);

        Mono<User> userMono = Mono.just(user().healthIdNumber(null).build());
        when(userServiceClient.userOf(anyString())).thenReturn(userMono);
        when(conceptValidator.validateHITypes(anyList())).thenReturn(Mono.just(true));
        when(subscriptionRequestRepository.requestOf(anyString(), anyString(), anyString())).thenReturn(Mono.just(subscriptionRequestDetails));
        when(subscriptionRequestRepository.updateHIUSubscription(anyString(), anyString(), anyString())).thenReturn(Mono.empty());
        when(subscriptionRequestRepository.insertIntoSubscriptionSource(anyString(), any(GrantedSubscription.class))).thenReturn(Mono.empty());
        when(gatewayServiceClient.subscriptionRequestNotify(any(HIUSubscriptionRequestNotifyRequest.class), anyString())).thenReturn(Mono.empty());
        when(subscriptionProperties.getSubscriptionRequestExpiry()).thenReturn(20);

        Mono<SubscriptionApprovalResponse> approval = subscriptionRequestService.approveSubscription(username, requestId, grantedSubscriptions);
        StepVerifier.create(approval)
                .assertNext(subscriptionApprovalResponse -> assertThat(subscriptionApprovalResponse.getSubscriptionId()).isNotNull())
                .expectComplete().verify();

        verify(conceptValidator, times(1)).validateHITypes(anyList());
        verify(subscriptionRequestRepository, times(1)).requestOf(requestId, RequestStatus.REQUESTED.name(), username);
        verify(subscriptionRequestRepository, times(1)).updateHIUSubscription(eq(requestId), anyString(), eq(RequestStatus.GRANTED.name()));
        verify(subscriptionRequestRepository, times(2)).insertIntoSubscriptionSource(any(String.class), grantedSubscriptionCaptor.capture());
        verify(gatewayServiceClient, times(1)).subscriptionRequestNotify(requestCaptor.capture(), hiuIdCaptor.capture());

        List<GrantedSubscription> subscriptions = grantedSubscriptionCaptor.getAllValues();
        HIUSubscriptionRequestNotifyRequest request = requestCaptor.getValue();
        String hiuId = hiuIdCaptor.getValue();

        assertThat(subscriptions.get(0)).isEqualTo(subscription1);
        assertThat(subscriptions.get(1)).isEqualTo(subscription2);
        assertThat(hiuId).isEqualTo(subscriptionRequestDetails.getHiu().getId());

        assertThat(request.getNotification().getSubscriptionRequestId()).isEqualTo(subscriptionRequestDetails.getId());
        assertThat(request.getNotification().getStatus()).isEqualTo(RequestStatus.GRANTED.name());
        assertThat(request.getNotification().getSubscription().getPatient()).isEqualTo(subscriptionRequestDetails.getPatient());
        assertThat(request.getNotification().getSubscription().getSources().get(0).getHip()).isEqualTo(subscription1.getHip());
        assertThat(request.getNotification().getSubscription().getSources().get(1).getHip()).isEqualTo(subscription2.getHip());
    }
}