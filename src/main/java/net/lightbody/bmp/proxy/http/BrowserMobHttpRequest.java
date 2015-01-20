package net.lightbody.bmp.proxy.http;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;

import net.lightbody.bmp.proxy.jetty.http.HttpRequest;
import net.lightbody.bmp.proxy.util.Base64;
import net.lightbody.bmp.proxy.util.ClonedInputStream;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrowserMobHttpRequest {
    private static final Logger LOG = LoggerFactory.getLogger(BrowserMobHttpRequest.class);

    private HttpRequestBase method;
    private BrowserMobHttpClient client;
    private int expectedStatusCode;
    private List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    private StringEntity stringEntity;
    private ByteArrayEntity byteArrayEntity;
    private RepeatableInputStreamRequestEntity inputStreamEntity;
    private MultipartEntity multipartEntity;
    private OutputStream outputStream;
    private RequestCallback requestCallback;
    private boolean collectAdditionalInfo;
    private HttpRequest proxyRequest;
    private ByteArrayOutputStream copy;
    private String expectedLocation;
    private boolean multiPart;

    protected BrowserMobHttpRequest(HttpRequestBase method, BrowserMobHttpClient client, int expectedStatusCode,
                                    boolean collectAdditionalInfo, HttpRequest proxyRequest) {
        this.method = method;
        this.client = client;
        this.expectedStatusCode = expectedStatusCode;
        this.collectAdditionalInfo = collectAdditionalInfo;
        this.proxyRequest = proxyRequest;
    }

    public RepeatableInputStreamRequestEntity getInputStreamEntity(){
        return inputStreamEntity;
    }

    public String getExpectedLocation() {
        return expectedLocation;
    }

    public void setExpectedLocation(String location) {
        this.expectedLocation = location;
    }

    public void addRequestHeader(String key, String value) {
        method.addHeader(key, value);
    }

    public void addRequestParameter(String key, String value) {
        nvps.add(new BasicNameValuePair(key, value));
    }

    public void setRequestBody(String body, String contentType, String charSet) {
        try {
        	stringEntity = new StringEntity(body, ContentType.create(contentType, charSet));
        } catch (UnsupportedCharsetException e) {
            stringEntity = new StringEntity(body, ContentType.create(contentType, (String) null));
        }
    }

    public void setRequestBody(String body) {
        setRequestBody(body, null, "UTF-8");
    }

    public void setRequestBodyAsBase64EncodedBytes(String bodyBase64Encoded) {
        byteArrayEntity = new ByteArrayEntity(Base64.base64ToByteArray(bodyBase64Encoded));
    }

    public void setRequestInputStream(InputStream is, long length) {
        if (collectAdditionalInfo) {
            ClonedInputStream cis = new ClonedInputStream(is);
            is = cis;
            copy = cis.getOutput();
        }

        inputStreamEntity = new RepeatableInputStreamRequestEntity(is, length);
    }


    public HttpRequestBase getMethod() {
        return method;
    }

    public HttpRequest getProxyRequest() {
        return proxyRequest;
    }

    public void makeMultiPart() {
        if (!multiPart) {
            multiPart = true;
            multipartEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
        }
    }

    public BrowserMobHttpResponse execute() {
        // deal with PUT/POST requests
        if (method instanceof HttpEntityEnclosingRequestBase) {
            HttpEntityEnclosingRequestBase enclodingRequest = (HttpEntityEnclosingRequestBase) method;

            if (!nvps.isEmpty()) {
                try {
                    if (!multiPart) {
                        enclodingRequest.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));
                    } else {
                        for (NameValuePair nvp : nvps) {
                            multipartEntity.addPart(nvp.getName(), new StringBody(nvp.getValue()));
                        }
                        enclodingRequest.setEntity(multipartEntity);
                    }
                } catch (UnsupportedEncodingException e) {
                    LOG.error("Could not find UTF-8 encoding, something is really wrong", e);
                }
            } else if (multipartEntity != null) {
                enclodingRequest.setEntity(multipartEntity);
            } else if (byteArrayEntity != null) {
                enclodingRequest.setEntity(byteArrayEntity);
            } else if (stringEntity != null) {
                enclodingRequest.setEntity(stringEntity);
            } else if (inputStreamEntity != null) {
                enclodingRequest.setEntity(inputStreamEntity);
            }
        }

        return client.execute(this);
    }

    public int getExpectedStatusCode() {
        return expectedStatusCode;
    }

    public void setExpectedStatusCode(int expectedStatusCode) {
        this.expectedStatusCode = expectedStatusCode;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void setOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public RequestCallback getRequestCallback() {
        return requestCallback;
    }

    public void setRequestCallback(RequestCallback requestCallback) {
        this.requestCallback = requestCallback;
    }

    public ByteArrayOutputStream getCopy() {
        return copy;
    }
}
