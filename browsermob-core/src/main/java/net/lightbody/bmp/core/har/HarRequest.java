package net.lightbody.bmp.core.har;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HarRequest {
    private volatile String method;
    private volatile String url;
    private volatile String httpVersion;
    private final List<HarCookie> cookies = new CopyOnWriteArrayList<>();
    private final List<HarNameValuePair> headers = new CopyOnWriteArrayList<>();
    private final List<HarNameValuePair> queryString = new CopyOnWriteArrayList<>();
    private volatile HarPostData postData;
    private volatile long headersSize; // Odd grammar in spec
    private volatile long bodySize;
    private volatile String comment = "";

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

    public List<HarNameValuePair> getHeaders() {
        return headers;
    }

    public List<HarNameValuePair> getQueryString() {
        return queryString;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        HarRequest that = (HarRequest) o;

        return new EqualsBuilder()
                .append(headersSize, that.headersSize)
                .append(bodySize, that.bodySize)
                .append(method, that.method)
                .append(url, that.url)
                .append(httpVersion, that.httpVersion)
                .append(cookies, that.cookies)
                .append(headers, that.headers)
                .append(queryString, that.queryString)
                .append(postData, that.postData)
                .append(comment, that.comment)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(method)
                .append(url)
                .append(httpVersion)
                .append(cookies)
                .append(headers)
                .append(queryString)
                .append(postData)
                .append(headersSize)
                .append(bodySize)
                .append(comment)
                .toHashCode();
    }
}
