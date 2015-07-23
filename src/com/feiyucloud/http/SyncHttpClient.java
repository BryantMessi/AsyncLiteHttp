package com.feiyucloud.http;

import com.feiyucloud.http.Request.Method;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SyncHttpClient {
    private int connectionTimeout = 20000;
    private int dataRetrievalTimeout = 20000;
    private boolean followRedirects = true;

    private final Map<String, String> headers;
    public static final String DEFAULT_USER_AGENT = "AsyncLiteHttp/1.0";

    public SyncHttpClient() {
        headers = Collections.synchronizedMap(new LinkedHashMap<String, String>());
        setUserAgent(DEFAULT_USER_AGENT);
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getDataRetrievalTimeout() {
        return dataRetrievalTimeout;
    }

    public void setDataRetrievalTimeout(int dataRetrievalTimeout) {
        this.dataRetrievalTimeout = dataRetrievalTimeout;
    }

    public boolean getFollowRedirects() {
        return followRedirects;
    }

    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    public void setHeader(String name, String value) {
        headers.put(name, value);
    }

    public void removeHeader(String name) {
        headers.remove(name);
    }

    public String getUserAgent() {
        return headers.get("User-Agent");
    }

    public void setUserAgent(String userAgent) {
        headers.put("User-Agent", userAgent);
    }

    public void doRequest(final Request request, final ResponseHandler handler) {
        HttpURLConnection connection = null;
        Method method = request.getMethod();
        String url = request.getUrl();

        try {
            URL resourceUrl = new URL(url);
            connection = (HttpURLConnection) resourceUrl.openConnection();

            // Settings
            connection.setConnectTimeout(connectionTimeout);
            connection.setReadTimeout(dataRetrievalTimeout);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(followRedirects);
            connection.setRequestMethod(method.toString());
            connection.setDoInput(true);

            // Headers
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            // Request start
            handler.onStart();

            // Request Body
            // POST and PUT expect an output body.
            if (method == Method.POST) {
                connection.setDoOutput(true);
                if (request.hasFiles()) {
                    // Use multipart/form-data to send fields and files
                    // 32kb at a time
                    connection.setChunkedStreamingMode(32 * 1024);
                    MultipartWriter.write(connection, request);
                } else {
                    // Send content as form-urlencoded
                    byte[] content = request.encodeParameters();
                    connection.setRequestProperty("Content-Type",
                            "application/x-www-form-urlencoded;charset=" + Request.UTF8);
                    connection.setRequestProperty("Content-Length", Long.toString(content.length));
                    // Stream the data so we don't run out of memory
                    connection.setFixedLengthStreamingMode(content.length);
                    OutputStream os = connection.getOutputStream();
                    os.write(content);
                    os.flush();
                    os.close();
                }
            }
            // Process the response in the handler because it can be done in
            // different ways
            handler.processResponse(connection);
            // Request finished
            handler.onFinish();

        } catch (IOException e) {
            handler.onFailure(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
                connection = null;
            }
        }

    }

}
