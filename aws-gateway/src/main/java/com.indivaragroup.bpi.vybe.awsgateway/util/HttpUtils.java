package com.indivaragroup.bpi.vybe.awsgateway.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.http.client.methods.HttpPost;

import static com.indivaragroup.bpi.vybe.awsgateway.error.ErrorUtils.createErrorResponse;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HttpUtils {

    public static APIGatewayProxyResponseEvent makeHttpGetRequest(String apiUrl, Map<String, String> headers,
                                                                  Map<String, String> queryParams) {
        APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Extract query parameters from the URI
            if (queryParams != null && !queryParams.isEmpty()) {
                // Build the query string from the queryParams map
                StringBuilder queryString = new StringBuilder();
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (queryString.length() > 0) {
                        queryString.append("&");
                    }
                    queryString.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                    queryString.append("=");
                    queryString.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                }
                apiUrl += "?" + queryString.toString(); // Append the query string to the base URL
            }
            HttpGet httpGet = new HttpGet(apiUrl);

            // Add headers from the event to the HTTP request
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                httpGet.setHeader(entry.getKey(), entry.getValue());
            }
            CloseableHttpResponse response = httpClient.execute(httpGet);

            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());

            responseEvent.setStatusCode(statusCode);
            responseEvent.setBody(responseBody);
        } catch (IOException e) {
            // Handle exceptions here
            log.error("Error while making HTTP GET request: {}", e.getMessage());
            return createErrorResponse(500, "500", e.getMessage());
        }

        return responseEvent;
    }

    public static APIGatewayProxyResponseEvent makeHttpPostRequest(String apiUrl, Map<String, String> headers,
                                                                   String requestBody, Map<String, String> queryParams,
                                                                   boolean isImageFile) {
        APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Extract query parameters from the URI
            if (queryParams != null && !queryParams.isEmpty()) {
                // Build the query string from the queryParams map
                StringBuilder queryString = new StringBuilder();
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (queryString.length() > 0) {
                        queryString.append("&");
                    }
                    queryString.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.toString()));
                    queryString.append("=");
                    queryString.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString()));
                }
                apiUrl += "?" + queryString.toString(); // Append the query string to the base URL
                log.info("queryString :{}", queryString);
            }
            HttpPost httpPost = new HttpPost(apiUrl);

            // Add headers from the event to the HTTP request
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                httpPost.setHeader(entry.getKey(), entry.getValue());
            }

            // Set the request body
            if (requestBody != null) {
                if (!isImageFile) {
                    log.info("this is not image");
                    StringEntity entity = new StringEntity(requestBody);
                    httpPost.setEntity(entity);

                    log.info("entity :{}", entity);
                } else {
                    log.info("this is image");
                    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                    byte[] binaryContent = requestBody.getBytes();
                    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
                    builder.addPart("file", new ByteArrayBody(binaryContent, ContentType.create("image/jpeg"), "file"));
                    //builder.addBinaryBody("file", requestBody.getBytes());

                    // Build the new multipart entity
                    HttpEntity multipartEntity = builder.build();
                    httpPost.setEntity(multipartEntity);
                    log.info("multipartEntity :{}", multipartEntity);
                }
            }
            log.info("httpPost : {}", httpPost);
            HttpResponse response = httpClient.execute(httpPost);

            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());

            responseEvent.setStatusCode(statusCode);
            responseEvent.setBody(responseBody);
        } catch (IOException e) {
            // Handle exceptions here
            return createErrorResponse(500, "500", e.getMessage());
        }

        return responseEvent;
    }
    public static APIGatewayProxyResponseEvent makeHttpRequest(String apiUrl, Map<String, String> headers,
                                                               String requestBody, HttpRequestBase requestBase) {
        APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            requestBase.setURI(new URI(apiUrl));

            // Add headers from the event to the HTTP request
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBase.setHeader(entry.getKey(), entry.getValue());
            }

            // Set the request body
            if (requestBody != null && !requestBody.isEmpty()) {
                StringEntity entity = new StringEntity(requestBody);
                ((HttpEntityEnclosingRequestBase) requestBase).setEntity(entity);
            }

            HttpResponse response = httpClient.execute(requestBase);
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());

            responseEvent.setStatusCode(statusCode);
            responseEvent.setBody(responseBody);
        } catch (IOException | URISyntaxException e) {
            // Handle exceptions here
            return createErrorResponse(500, "500", e.getMessage());
        }

        return responseEvent;
    }
}
