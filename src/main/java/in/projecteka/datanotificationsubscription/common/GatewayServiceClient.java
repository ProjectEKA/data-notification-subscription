package in.projecteka.datanotificationsubscription.common;

import in.projecteka.datanotificationsubscription.GatewayServiceProperties;
import in.projecteka.datanotificationsubscription.common.model.ServiceInfo;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionNotificationRequest;
import in.projecteka.datanotificationsubscription.subscription.model.SubscriptionOnInitRequest;
import in.projecteka.datanotificationsubscription.subscription.model.HIUSubscriptionRequestNotifyRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

import static in.projecteka.datanotificationsubscription.common.ClientError.networkServiceCallFailed;
import static in.projecteka.datanotificationsubscription.common.ClientError.unAuthorized;
import static in.projecteka.datanotificationsubscription.common.ClientError.unprocessableEntity;
import static in.projecteka.datanotificationsubscription.common.Constants.AUTHORIZATION;
import static in.projecteka.datanotificationsubscription.common.Constants.CORRELATION_ID;
import static in.projecteka.datanotificationsubscription.common.Constants.GET_SERVICE_INFO;
import static in.projecteka.datanotificationsubscription.common.Constants.HDR_HIU_ID;
import static in.projecteka.datanotificationsubscription.common.Constants.SUBSCRIPTION_HIU_NOTIFY;
import static in.projecteka.datanotificationsubscription.common.Constants.SUBSCRIPTION_REQUEST_HIU_NOTIFY;
import static in.projecteka.datanotificationsubscription.common.Constants.SUBSCRIPTION_REQUEST_INIT_URL_PATH;
import static java.time.Duration.ofMillis;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static reactor.core.publisher.Mono.error;

public class GatewayServiceClient {
    private final ServiceAuthentication serviceAuthentication;
    private final GatewayServiceProperties gatewayServiceProperties;
    private final WebClient webClient;

    private final Logger logger = LoggerFactory.getLogger(GatewayServiceClient.class);

    public GatewayServiceClient(WebClient.Builder builder, GatewayServiceProperties gatewayServiceProperties, ServiceAuthentication serviceAuthentication) {
        this.webClient = builder.build();
        this.serviceAuthentication = serviceAuthentication;
        this.gatewayServiceProperties = gatewayServiceProperties;
    }

    public Mono<Void> subscriptionRequestOnInit(SubscriptionOnInitRequest onInitRequest, String hiuId) {
        return serviceAuthentication.authenticate()
                .flatMap(token -> webClient
                        .post()
                        .uri(gatewayServiceProperties.getBaseUrl() + SUBSCRIPTION_REQUEST_INIT_URL_PATH)
                        .contentType(APPLICATION_JSON)
                        .header(AUTHORIZATION, token)
                        .header(HDR_HIU_ID, hiuId)
                        .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                        .bodyValue(onInitRequest)
                        .retrieve()
                        .onStatus(httpStatus -> httpStatus.value() == 400,
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .doOnNext(logger::error)
                                        .then(error(unprocessableEntity())))
                        .onStatus(httpStatus -> httpStatus.value() == 401,
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .doOnNext(logger::error)
                                        .then(error(unAuthorized())))
                        .onStatus(HttpStatus::is5xxServerError,
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .doOnNext(logger::error)
                                        .then(error(networkServiceCallFailed())))
                        .toBodilessEntity()
                        .publishOn(Schedulers.elastic())
                        .timeout(ofMillis(gatewayServiceProperties.getRequestTimeout())))
                .doOnSubscribe(subscription -> logger.info("About to notify HIU {} subscription request created for the request {}" +
                                "with subscription request-id: {}",
                        hiuId,
                        onInitRequest.getResp().getRequestId(),
                        onInitRequest.getSubscriptionRequest().getId()))
                .then();
    }

    public Mono<ServiceInfo> getServiceInfo(String serviceId) {
        //TODO: cache
        return serviceAuthentication.authenticate()
                .flatMap(authToken ->
                        webClient
                                .get()
                                .uri(gatewayServiceProperties.getBaseUrl() + String.format(GET_SERVICE_INFO, serviceId))
                                .header(AUTHORIZATION, authToken)
                                .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                                .retrieve()
                                .onStatus(httpStatus -> httpStatus.value() == 401,
                                        clientResponse -> clientResponse.bodyToMono(String.class)
                                                .doOnNext(logger::error)
                                                .then(error(ClientError.unAuthorized())))
                                .onStatus(HttpStatus::is5xxServerError,
                                        clientResponse -> clientResponse.bodyToMono(String.class)
                                                .doOnNext(logger::error)
                                                .then(error(ClientError.networkServiceCallFailed())))
                                .bodyToMono(ServiceInfo.class)
                                .publishOn(Schedulers.elastic())
                                .timeout(Duration.ofMillis(gatewayServiceProperties.getRequestTimeout())))
                .doOnSubscribe(subscription -> logger.info("About to call Gateway to get service info for service-id: {}",
                        serviceId));
    }

    public Mono<Void> notifyForSubscription(HIUSubscriptionNotificationRequest notificationRequest, String hiuId) {
        return serviceAuthentication.authenticate()
                .flatMap(token -> webClient
                        .post()
                        .uri(gatewayServiceProperties.getBaseUrl() + SUBSCRIPTION_HIU_NOTIFY)
                        .contentType(APPLICATION_JSON)
                        .header(AUTHORIZATION, token)
                        .header(HDR_HIU_ID, hiuId)
                        .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                        .bodyValue(notificationRequest)
                        .retrieve()
                        .onStatus(httpStatus -> httpStatus.value() == 400,
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .doOnNext(logger::error)
                                        .then(error(unprocessableEntity())))
                        .onStatus(httpStatus -> httpStatus.value() == 401,
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .doOnNext(logger::error)
                                        .then(error(unAuthorized())))
                        .onStatus(HttpStatus::is5xxServerError,
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .doOnNext(logger::error)
                                        .then(error(networkServiceCallFailed())))
                        .toBodilessEntity()
                        .publishOn(Schedulers.elastic())
                        .timeout(ofMillis(gatewayServiceProperties.getRequestTimeout())))
                .doOnSubscribe(subscription -> logger.info("About to call HIU {} for subscription notification", hiuId))
                .then();
    }

    public Mono<Void> subscriptionRequestNotify(HIUSubscriptionRequestNotifyRequest request, String hiuId) {
        return serviceAuthentication.authenticate()
                .flatMap(token -> webClient
                        .post()
                        .uri(gatewayServiceProperties.getBaseUrl() + SUBSCRIPTION_REQUEST_HIU_NOTIFY)
                        .contentType(APPLICATION_JSON)
                        .header(AUTHORIZATION, token)
                        .header(HDR_HIU_ID, hiuId)
                        .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                        .bodyValue(request)
                        .retrieve()
                        .onStatus(httpStatus -> httpStatus.value() == 400,
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .doOnNext(logger::error)
                                        .then(error(unprocessableEntity())))
                        .onStatus(httpStatus -> httpStatus.value() == 401,
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .doOnNext(logger::error)
                                        .then(error(unAuthorized())))
                        .onStatus(HttpStatus::is5xxServerError,
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .doOnNext(logger::error)
                                        .then(error(networkServiceCallFailed())))
                        .toBodilessEntity()
                        .publishOn(Schedulers.elastic())
                        .timeout(ofMillis(gatewayServiceProperties.getRequestTimeout())))
                .doOnSubscribe(subscription ->
                        logger.info("About to call HIU {} for subscription request notification with gateway id {}", hiuId, request.getRequestId())
                )
                .then();
    }
}
