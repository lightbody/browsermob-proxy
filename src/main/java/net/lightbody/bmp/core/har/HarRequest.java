package net.lightbody.bmp.core.har;

import org.codehaus.jackson.map.annotate.JsonSerialize;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class HarRequest {
    private String method;
    private String url;
    private String httpVersion;
    private List<HarCookie> cookies = new CopyOnWriteArrayList<HarCookie>();
    private List<HarNameValuePair> headers = new CopyOnWriteArrayList<HarNameValuePair>();
    private List<HarNameValuePair> queryString = new CopyOnWriteArrayList<HarNameValuePair>();
    private HarPostData postData;
    private long headersSize; // Odd grammar in spec
    private long bodySize;
    private String comment;

    public HarRequest() {
    }

    public HarRequest(String method, String url, String httpVersion) {
        this.method = method;
        this.url = url;
        this.httpVersion = httpVersion;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }

    public List<HarCookie> getCookies() {
        return cookies;
    }

    public void setCookies(List<HarCookie> cookies) {
        this.cookies = cookies;
    }

    public List<HarNameValuePair> getHeaders() {
        return headers;
    }

    public void setHeaders(List<HarNameValuePair> headers) {
        this.headers = headers;
    }

    public List<HarNameValuePair> getQueryString() {
        return queryString;
    }

    public void setQueryString(List<HarNameValuePair> queryString) {
        this.queryString = queryString;
    }

    public HarPostData getPostData() {
        return postData;
    }

    public void setPostData(HarPostData postData) {
        this.postData = postData;
    }

    public long getHeadersSize() {
        return headersSize;
    }

    public void setHeadersSize(long headersSize) {
        this.headersSize = headersSize;
    }

    public long getBodySize() {
        return bodySize;
    }

    public void setBodySize(long bodySize) {
        this.bodySize = bodySize;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
