package web;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class Flow {
    private final Log logger = LogFactory.getLog(getClass());

    private final StatusWorker rf;
    public Flow() throws CouldNotAcquireLockException {
        try {
            rf = new StatusWorker();
        } catch (CouldNotAcquireLockException e) {
            ConfigurableApplicationContext context = new AnnotationConfigApplicationContext("web");
            context.close();
            throw e;
        }

    }


    @Async
    public  void post(
            int[] ids,
            int BatchSize,
            byte[] url,
            byte[] body,
            byte[] auth,
            byte[] member,
            byte[] proxyUrl,
            byte[] proxyPort,
            byte[] jsonStart,
            byte[] elok,
            int timeJson)
    {
        int count;
        boolean completed;
        String memberStr = new String(member);
        String eLokStr = new String(elok).trim();
        count = 0;
        completed = false;
        while (!completed && !rf.StopRequested(eLokStr) ) {
            int returnValue = doRestCall(ids, BatchSize, url, body, auth, member, proxyUrl, proxyPort, jsonStart, elok, timeJson);
            if (returnValue == 0) {
                try {
                    logger.warn("Waiting 30 seconds before retry. Tread: " + Thread.currentThread() + " Attempt: " + count);
                    Thread.sleep(1000*30);
                    count++;
                    if (count >= 3) {
                        logger.warn("Cancelling retry after 2 minutes, resetting batch to status 2");
                        for (int i = 0; i < BatchSize; i++) {
                            rf.updatePFNEI(memberStr, ids[i], 2, BatchSize);
                        }
                        completed = true;
                    }
                } catch (InterruptedException e) {
                    logger.warn("Thread interrupt after Thread.sleep.");
                    Thread.currentThread().interrupt();
                    count++;
                }
            } else {
                completed = true;
            }
        }
    }


private int doRestCall(
        int[] ids,
        int BatchSize,
        byte[] url,
        byte[] body,
        byte[] auth,
        byte[] member,
        byte[] proxyUrl,
        byte[] proxyPort,
        byte[] jsonStart,
        byte[] elok,
        int timeJson)
{

    String urlStr = new String(url).trim();
    String bodyStr = new String(body);
    String authStr = new String(auth).trim();
    String memberStr = new String(member);
    String proxyUrlStr = new String(proxyUrl).trim();
    String proxyPortStr = new String(proxyPort).trim();
    String eLokStr = new String(elok).trim();


    String httpStatusText = "";
    int httpStatus = 0;
    boolean error = false;
    boolean failed = false;
    HttpStatus status;
    Date date = new Date();
    Timestamp httpStart = new Timestamp(date.getTime());
    String httpMethod = System.getProperty("http.Method", eLokStr.equals("PDS") ? "POST" : "PUT");

    RestTemplate restTemplate;
    if (proxyUrlStr.equals("")) {
        restTemplate = new RestTemplate();
    } else {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Proxy proxy = new Proxy(Type.HTTP, new InetSocketAddress(proxyUrlStr, Integer.parseInt(proxyPortStr)));
        requestFactory.setProxy(proxy);
        restTemplate = new RestTemplate(requestFactory);
    }


    //	restTemplate.getInterceptors().add(new GZipInterceptor());
    restTemplate.getInterceptors().add(new ContentTypeInterceptor());
    if (!authStr.equals("")) {
        restTemplate.getInterceptors().add(new AuthorizationInterceptor(authStr));
    }

    // Make the HTTP request, marshaling the request to JSON, and the response to a String
    // Use POST or PUT based on environment variable http.Method
    try {
        ResponseEntity<String> entity;

        if (eLokStr.equals("PDS")) {
            entity = restTemplate.postForEntity(urlStr, bodyStr.getBytes("UTF-8"), String.class);
            status = entity.getStatusCode();
        } else {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");

            HttpEntity<String> requestEntity = new HttpEntity<String>(bodyStr, headers);
            restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(Charset.forName("UTF-8")));
            entity = restTemplate.exchange(urlStr, HttpMethod.valueOf(httpMethod), requestEntity, String.class);
            status = entity.getStatusCode();
        }

        httpStatusText = status.getReasonPhrase();
        httpStatus = status.value();

        if (status == HttpStatus.NO_CONTENT) {
            logger.debug("Status changed to 200, original status : " + httpStatus + " " + httpStatusText);
            httpStatusText = "";
            httpStatus = 200;
        }

    } catch (HttpStatusCodeException e2) { /* Normal exception like 4xx and 5xx errors */
        httpStatusText = e2.getMessage();
        httpStatus = e2.getStatusCode().value();
        if (e2 instanceof HttpServerErrorException) {
            if (httpStatus == 500){
                failed = true;
            } else {
                error = true;
            }
        } else {
            failed = true;
        }
        logger.warn("Following body received: \"" + e2.getResponseBodyAsString() + "\"");

    } catch (Exception e) { /* IO Error */
        httpStatus = -1;
        httpStatusText = e.getMessage(); //.substring(e.getMessage().length()-50);
        logger.warn("Following body received: \"" + httpStatusText + "\"");
        error = true;

    } finally {
        date = new Date();
        Timestamp httpEnd = new Timestamp(date.getTime());
        logger.debug("HTTP response code: " + httpStatus + " " + httpStatusText);

        rf.writePFNBL(memberStr, ids, BatchSize, bodyStr.length(), httpStatus, httpStatusText, jsonStart, httpStart, httpEnd, elok, timeJson);

        if (!error && !failed) {
            /* All Oke, set all messages to processed */
            for (int i = 0; i < BatchSize; i++) {
                rf.updatePFNEI(memberStr, ids[i], 99, BatchSize);
            }
        } else {
            if (failed) {
                if (BatchSize == 1) {
                    /* In case BatchSize = 1 and 4xx (failed) than a retry will be done by NRE161 after x minutes defined in PFNSE */
                    logger.warn("Failure(4xx) with BatchSize = 1, Retry after after x minutes defined in PFNSE");
                    for (int i = 0; i < BatchSize; i++) {
                        rf.updatePFNEI(memberStr, ids[i], -81, BatchSize);
                    }
                } else {
                    logger.warn("Failure(4xx) with BatchSize <> 1, in smaller batchsize");
                    for (int i = 0; i < BatchSize; i++) {
                        rf.updatePFNEI(memberStr, ids[i], 5, BatchSize);
                    }
                }
            }
        }
    }
    if(error){
        return 0;}
    else if(failed){
        return 1;}
    else{
        return 2;}
}

    public void setTrustManager() {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };

        // Activate the new trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
