package com.github.redouane59.twitter.helpers;

import com.github.redouane59.twitter.TwitterClient;
import com.github.redouane59.twitter.dto.others.RequestTokenDTO;
import com.github.redouane59.twitter.signature.Oauth1SigningInterceptor;
import com.github.redouane59.twitter.signature.TwitterCredentials;
import jdk.jfr.ContentType;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import okhttp3.*;
import okhttp3.internal.http2.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@NoArgsConstructor
@CustomLog
public class RequestHelper extends AbstractRequestHelper {

    private int sleepTime = 5;

    public <T> Optional<T> executeGetRequest(String url, Class<T> classType) {
        T result = null;
        try {
            Response response = this.getHttpClient(url)
                    .newCall(this.getSignedRequest(this.getRequest(url)))
                    .execute();
            String stringResponse = response.body().string();
            if(response.code()==200){
                result = TwitterClient.OBJECT_MAPPER.readValue(stringResponse, classType);
            } else if (response.code()==429){
                this.wait(sleepTime, response, url);
                return this.executeGetRequest(url, classType);
            } else{
                logGetError(url, stringResponse);
            }
        } catch(Exception e){
            LOGGER.severe("exception in executeGetRequest " + e.getMessage());
        }
        return Optional.ofNullable(result);
    }

    public <T> Optional<T> executeGetRequestWithParameters(String url, Map<String, String> parameters, Class<T> classType) {
        T result = null;
        try {
            HttpUrl.Builder httpBuilder = HttpUrl.parse(url).newBuilder();
            if (parameters != null) {
                for(Map.Entry<String, String> param : parameters.entrySet()) {
                    httpBuilder.addQueryParameter(param.getKey(),param.getValue());
                }
            }
            String newUrl = httpBuilder.build().url().toString();
            Request requesthttp = this.getSignedRequest(this.getRequest(httpBuilder));

            Response response = this.getHttpClient(newUrl)
                    .newCall(requesthttp)
                    .execute();
            String stringResponse = response.body().string();
            if(response.code()==200){
                result = TwitterClient.OBJECT_MAPPER.readValue(stringResponse, classType);
            } else if (response.code()==429){
                this.wait(sleepTime, response, url);
                return this.executeGetRequest(url, classType);
            } else{
                logGetError(url, stringResponse);
            }
        } catch(Exception e){
            LOGGER.severe("exception in executeGetRequest " + e.getMessage());
        }
        return Optional.ofNullable(result);
    }

    public <T> Optional<T> executeGetRequestV2(String url, Class<T> classType) {
        T result = null;
        try {
            Response response = this.getHttpClient(url)
                    .newCall(this.getSignedRequest(this.getRequest(url))).execute();
            String stringResponse = response.body().string();
            if(response.code()==200){
                response.close();
                result = TwitterClient.OBJECT_MAPPER.readValue(stringResponse, classType);
            } else if (response.code()==429){
                this.wait(sleepTime, response, url);
                return this.executeGetRequestV2(url, classType);
            } else{
                logGetError(url, stringResponse);
            }
        } catch(Exception e){
            LOGGER.severe(e.getMessage());
        }
        return Optional.ofNullable(result);
    }

    public <T> Optional<T> executePostRequest(String url, Map<String, String> parameters, Class<T> classType) {
        T result = null;
        try {
            String json = TwitterClient.OBJECT_MAPPER.writeValueAsString(parameters);
            RequestBody requestBody = RequestBody.create(null, json);
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();
            Request signedRequest = this.getSignedRequest(request);
            Response response = this.getHttpClient(url)
                    .newCall(signedRequest).execute();
            if(response.code()!=200){
                LOGGER.severe(()->"(POST) ! not 200 calling " + url + " " + response.message() + " - " + response.code());
                if(response.code()==429){
                    LOGGER.severe(()->"Reset your token");
                }
            }
            String stringResponse = response.body().string();
            if(classType.equals(String.class)){ // dirty, to manage token oauth1
                result = (T)stringResponse;
            } else{
                result = TwitterClient.OBJECT_MAPPER.readValue(stringResponse, classType);
            }
        } catch(Exception e){
            LOGGER.severe(e.getMessage());
        }
        return Optional.ofNullable(result);
    }

    public <T> Optional<T> executePostRequestWithHeader(String url, Map<String, String> headersMap, String body, Class<T> classType) {
        T result = null;
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .method("POST",RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), body))
                    .headers(Headers.of(headersMap))
                    .build();
           // Request signedRequest = this.getSignedRequest(request);
            Response response = this.getHttpClient(url)
                    .newCall(request).execute();
            if(response.code()!=200){
                LOGGER.severe(()->"(POST) ! not 200 calling " + url + " " + response.message() + " - " + response.code());
            }
            String stringResponse = response.body().string();
            result = TwitterClient.OBJECT_MAPPER.readValue(stringResponse, classType);
        } catch(Exception e){
            LOGGER.severe(e.getMessage());
        }
        return Optional.ofNullable(result);
    }

    @Deprecated
    public <T> Optional<T> executeGetRequestReturningArray(String url, Class<T> classType) {
        T result = null;
        try {
            Response response = this.getHttpClient(url)
                    .newCall(this.getSignedRequest(this.getRequest(url))).execute();
            String stringResponse = response.body().string();
            if(response.code()==200){
                result = TwitterClient.OBJECT_MAPPER.readValue(stringResponse, classType);
            } else if (response.code() == 401){
                response.close();
                LOGGER.info(()->"user private, not authorized");
            } else if (response.code()==429){
                this.wait(sleepTime, response, url);
                return this.executeGetRequestReturningArray(url, classType);
            } else{
                LOGGER.severe(()->"not 200 calling " + url + " " + response.message() + " - " + response.code());
            }
        } catch(Exception e){
            LOGGER.severe(e.getMessage());
        }
        return Optional.ofNullable(result);
    }

    private Request getSignedRequest(Request request){
        Oauth1SigningInterceptor oauth = new Oauth1SigningInterceptor.Builder()
                .consumerKey(TWITTER_CREDENTIALS.getApiKey())
                .consumerSecret(TWITTER_CREDENTIALS.getApiSecretKey())
                .accessToken(TWITTER_CREDENTIALS.getAccessToken())
                .accessSecret(TWITTER_CREDENTIALS.getAccessTokenSecret())
                .build();
        return oauth.signRequest(request);
    }

    private Request getRequest(String url){
        return new Request.Builder()
                .url(url)
                .get()
                .build();
    }

    private Request getRequest(HttpUrl.Builder httpBuilder){
        return new Request.Builder().get().url(httpBuilder.build()).build();
    }

    private OkHttpClient getHttpClient(String url){
        long cacheSize = 1024L * 1024 * 1024; // 1go
        String path = "../okhttpCache";
        File file = new File(path);
        return new OkHttpClient.Builder()
                .addNetworkInterceptor(new CacheInterceptor(this.getCacheTimeoutFromUrl(url)))
                .cache(new Cache(file, cacheSize))
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void wait(int sleepTime, Response response, String url){
        LOGGER.info(()->"\n" + response +"\nWaiting ... " + url); // do a wait and return this function recursively
        try {
            TimeUnit.MINUTES.sleep(sleepTime);
        } catch (InterruptedException e) {
            LOGGER.severe(e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private int getCacheTimeoutFromUrl(String url){
        int defaultCache = 48;
        URL cacheUrl = this.getClass().getClassLoader().getResource("cache-config.json");
        if(cacheUrl==null){
            LOGGER.severe("cache-config.json file not found in src/main/resources");
            return defaultCache;
        }
        try {
            Map<String, Integer> map = TwitterClient.OBJECT_MAPPER.readValue(cacheUrl, Map.class);
            for(Map.Entry<String, Integer> e : map.entrySet()){
                if(url.contains(e.getKey())){
                    return e.getValue();
                }
            }
        } catch (IOException e) {
            LOGGER.severe(e.getMessage());
        }
        return defaultCache;
    }

    private void logGetError(String url, String response){
        LOGGER.severe(()->" Error calling " + url + " : " + response);
    }

}