package com.indivaragroup.bpi.vybe.awsgateway.serverless;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.indivaragroup.bpi.vybe.awsgateway.util.HttpUtils;
import com.indivaragroup.bpi.vybe.awsgateway.validation.ValidatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.indivaragroup.bpi.vybe.awsgateway.ApiPath.Idp.*;
import static com.indivaragroup.bpi.vybe.awsgateway.ApiPath.Notification.NOTIF_OTP_SEND;
import static com.indivaragroup.bpi.vybe.awsgateway.ApiPath.Notification.NOTIF_OTP_VERIFY;
import static com.indivaragroup.bpi.vybe.awsgateway.ApiPath.Transactions.ADDMONEY_OTHER_BANK;
import static com.indivaragroup.bpi.vybe.awsgateway.ApiPath.Transactions.ADDMONEY_VIA_NG;
import static com.indivaragroup.bpi.vybe.awsgateway.constant.HttpMethod.*;
import static com.indivaragroup.bpi.vybe.awsgateway.error.ErrorUtils.createErrorResponse;

@Component
@Slf4j
@RequiredArgsConstructor
public class AwsGatewayMain implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ValidatorService validatorService;
    @Value("${outbound.baseUrl}")
    private String baseUrl;

    @Override
    public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent eventFromGateway) {
        log.info("event : {}", eventFromGateway);

        String apiUrl = baseUrl + eventFromGateway.getPath();

        if (isAddMoneyViaNgAndOtherBankTransaction(eventFromGateway.getPath())) {
            log.info("Transaction NG or Add Money From Other Bank");
            return handleWithSignatureKeyValidation(eventFromGateway, apiUrl);

        } else if (isPathNoNeedValidation(eventFromGateway.getPath())) {
            log.info("APIs That don't need any validation");
            return handleWithoutAnyValidation(eventFromGateway, apiUrl);

        } else {
            log.info("hit endpoint to : {}", apiUrl);
            return handleWithTokenValidation(eventFromGateway, apiUrl);
        }
    }
    private APIGatewayProxyResponseEvent handleWithoutAnyValidation(APIGatewayProxyRequestEvent eventFromGateway,
                                                                    String url) {
        return hitToUrl(eventFromGateway, url);
    }
    private boolean isAddMoneyViaNgAndOtherBankTransaction(String eventPath) {
        return eventPath.contains(ADDMONEY_VIA_NG) ||
                eventPath.contains(ADDMONEY_OTHER_BANK);
    }
    private boolean isPathNoNeedValidation(String eventPath) {
        return  eventPath.equals(AUTH_LOGIN) || eventPath.equals(NOTIF_IDP) ||
                eventPath.equals(MEMBER_FORGOT_PASSWORD) || eventPath.equals(MEMBER_FORGOT_PASSWORD_OTP) ||
                eventPath.equals(MEMBER_REGISTER_VYBE_PRO) || eventPath.equals(MEMBER_SET_PIN_VYBE_PRO) ||
                eventPath.equals(MEMBER_REGISTER_VYBE_LITE) || eventPath.equals(MEMBER_SET_MPIN_VYBE_LITE) ||
                eventPath.equals(NOTIF_OTP_SEND) || eventPath.equals(NOTIF_OTP_VERIFY) ||
                eventPath.equals(CHECK_VYBE_MEMBER) || eventPath.equals(NOTIF_VERIFY_IDP);
    }
    private APIGatewayProxyResponseEvent handleWithSignatureKeyValidation(APIGatewayProxyRequestEvent eventFromGateway,
                                                                                    String url) {
        APIGatewayProxyResponseEvent eventFromSignatureValidation;
        try {
            log.info("Signature Validation");
            eventFromSignatureValidation = validatorService.validateSignature(eventFromGateway.getBody(), eventFromGateway.getPath());
        } catch (Exception e) {
            return createErrorResponse(500, "500", e.getMessage());
        }

        if (eventFromSignatureValidation.getStatusCode() == 200) {
            eventFromSignatureValidation = hitToUrl(eventFromGateway, url);
        }
        return eventFromSignatureValidation;
    }
    private APIGatewayProxyResponseEvent handleWithTokenValidation(APIGatewayProxyRequestEvent eventFromGateway, String url) {
        APIGatewayProxyResponseEvent eventFromService;

        try {
            log.info("Token Validation");
            eventFromService = validatorService.validateResponseToken(eventFromGateway);
            eventFromGateway.setHeaders(eventFromService.getHeaders());
        } catch (JsonProcessingException e) {
            return createErrorResponse(500, "500", e.getMessage());
        }

        log.info("Header from gateway : {}", eventFromGateway.getHeaders());
        if (eventFromService.getStatusCode() == 200) {
            eventFromService = hitToUrl(eventFromGateway, url);
        }
        return eventFromService;
    }
    private APIGatewayProxyResponseEvent hitToUrl(APIGatewayProxyRequestEvent eventFromGateway, String url) {
        if (eventFromGateway.getHttpMethod().equals(GET)) {
            log.info("START GET METHOD : {}", url);
            return HttpUtils.makeHttpGetRequest(url, resetHeaders(eventFromGateway.getHeaders()),
                                                eventFromGateway.getQueryStringParameters());
        } else if (eventFromGateway.getHttpMethod().equals(POST)) {
            log.info("START POST METHOD : {}", url);
            if (eventFromGateway.getHeaders().get("Content-Type").contains("multipart/form-data")) {
                return HttpUtils.makeHttpPostRequest(url, resetHeaders(eventFromGateway.getHeaders()),
                        eventFromGateway.getBody(), eventFromGateway.getQueryStringParameters(), true);
            } else {
                return HttpUtils.makeHttpPostRequest(url, resetHeaders(eventFromGateway.getHeaders()),
                        eventFromGateway.getBody(), eventFromGateway.getQueryStringParameters(), false);
            }

        } else if (eventFromGateway.getHttpMethod().equals(PUT)) {
            log.info("START PUT METHOD : {}", url);
            return HttpUtils.makeHttpRequest(url, resetHeaders(eventFromGateway.getHeaders()),
                                             eventFromGateway.getBody(), new HttpPut());
        } else if (eventFromGateway.getHttpMethod().equals(OPTIONS)) {
            log.info("START OPTIONS METHOD : {}", url);
            return HttpUtils.makeHttpRequest(url, resetHeaders(eventFromGateway.getHeaders()),
                                             eventFromGateway.getBody(), new HttpOptions());
        } else if (eventFromGateway.getHttpMethod().equals(HEAD)) {
            log.info("START HEAD METHOD : {}", url);
            return HttpUtils.makeHttpRequest(url, resetHeaders(eventFromGateway.getHeaders()),
                                             eventFromGateway.getBody(), new HttpHead());
        } else if (eventFromGateway.getHttpMethod().equals(PATCH)) {
            log.info("START PATCH METHOD : {}", url);
            return HttpUtils.makeHttpRequest(url, resetHeaders(eventFromGateway.getHeaders()),
                                             eventFromGateway.getBody(), new HttpPatch());
        } else if (eventFromGateway.getHttpMethod().equals(DELETE)) {
            log.info("START DELETE METHOD : {}", url);
            return HttpUtils.makeHttpRequest(url, resetHeaders(eventFromGateway.getHeaders()),
                                             eventFromGateway.getBody(), new HttpDelete());
        } else {
            return createErrorResponse(405, "405", "Http Method is invalid");
        }
    }
    private Map<String, String> resetHeaders(Map<String, String> headers) {
        // Create a new map to store filtered headers
        Map<String, String> filteredHeaders = new HashMap<>();
        log.info("RESET HEADER : {}", headers);
        // Create a set of headers to exclude
        String[] keysToExclude = {"Accept-Encoding", "CloudFront-Forwarded-Proto", "CloudFront-Is-Desktop-Viewer",
                                  "CloudFront-Is-Mobile-Viewer", "CloudFront-Is-SmartTV-Viewer", "CloudFront-Is-Tablet-Viewer",
                                  "CloudFront-Viewer-ASN", "CloudFront-Viewer-Country", "Host", "Postman-Token", "User-Agent",
                                  "Via", "X-Amz-Cf-Id", "X-Amzn-Trace-Id", "X-Forwarded-For", "X-Forwarded-Port",
                                  "X-Forwarded-Proto"};

        log.info("headers.entrySet() : {}", headers.entrySet());
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Check if the key should be excluded
            if (!shouldExcludeKey(key, keysToExclude)) {
                filteredHeaders.put(key, value);
            }
        }

        log.info("filteredHeaders : {}", filteredHeaders);
        return filteredHeaders;
    }
    private static boolean shouldExcludeKey(String key, String[] keysToExclude) {
        for (String excludedKey : keysToExclude) {
            if (key.equals(excludedKey)) {
                return true;
            }
        }
        return false;
    }
}
