package com.indivaragroup.bpi.vybe.awsgateway.util;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import java.io.ByteArrayOutputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.MultipartStream;
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
import org.apache.commons.codec.binary.Base64;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
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

                    //===============================upload to s3===================================//
                    log.info("this is image : " + requestBody);
                    String contentType = "";
                    //Change these values to fit your region and bucket name
                    Regions clientRegion = Regions.AP_SOUTHEAST_1;
                    String bucketName = "bpi-woi-revamp-sit";
                    String fileObjKeyName = "image.jpg";

                    byte[] binaryContent = Base64.decodeBase64(requestBody.getBytes());
                    if (headers != null) {
                        contentType = headers.get("Content-Type");
                    }
                    //Extract the boundary
                    String[] boundaryArray = contentType.split("=");
                    //Transform the boundary to a byte array
                    byte[] boundary = boundaryArray[1].getBytes();

                    //Log the extraction for verification purposes
                    log.info(new String(binaryContent, "UTF-8") + "\n");

                    //Create a ByteArrayInputStream
                    ByteArrayInputStream content = new ByteArrayInputStream(binaryContent);
                    //Create a MultipartStream to process the form-data
                    MultipartStream multipartStream = new MultipartStream(content, boundary, binaryContent.length, null);
                    //Create a ByteArrayOutputStream
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    //Find first boundary in the MultipartStream
                    boolean nextPart = multipartStream.skipPreamble();

                    //Loop through each segment
                    while (nextPart)
                    {
                        String header = multipartStream.readHeaders();

                        //Log header for debugging
                        log.info("Headers:");
                        log.info(header);

                        //Write out the file to our ByteArrayOutputStream
                        multipartStream.readBodyData(out);

                        //Get the next part, if any
                        nextPart = multipartStream.readBoundary();

                    }
                    //Log completion of MultipartStream processing
                    log.info("Data written to ByteStream");
                    //Prepare an InputStream from the ByteArrayOutputStream
                    InputStream fis = new ByteArrayInputStream(out.toByteArray());

                    AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                            .withRegion(clientRegion)
                            .build();

                    //Configure the file metadata
                    ObjectMetadata metadata = new ObjectMetadata();
                    metadata.setContentLength(out.toByteArray().length);
                    metadata.setContentType("image/jpeg");
                    metadata.setCacheControl("public, max-age=31536000");

                    //Put file into S3
                    s3Client.putObject(bucketName, fileObjKeyName, fis, metadata);
                    //==================================================================//

                    log.info("this is image");
                    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
                    // Add the entire original multipart content to the new builder
                    builder.addBinaryBody("file", fis.readAllBytes());

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
        } catch (Exception e) {
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
