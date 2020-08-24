package net.lightbody.bmp.filters;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Map;

/**
 * Adds the headers specified in the constructor to this request. The filter does not make a defensive copy of the map, so there is no guarantee
 * that the map at the time of construction will contain the same values when the filter is actually invoked, if the map is modified concurrently.
 */
public class AddHeadersFilter extends HttpsAwareFiltersAdapter {
    private final Map<String, String> additionalHeaders;
    private static String headersSpecificFilter;

    public AddHeadersFilter(HttpRequest originalRequest, ChannelHandlerContext ctx, Map<String, String> additionalHeaders, String headersSpecificFilter) {
        super(originalRequest, ctx);

        if (additionalHeaders != null) {
            this.additionalHeaders = additionalHeaders;
        } else {
            this.additionalHeaders = Collections.emptyMap();
        }
        if (StringUtils.isNotEmpty(headersSpecificFilter)) {
            setHeadersSpecificFilter(headersSpecificFilter);
        }
    }

    @Override
    public HttpResponse clientToProxyRequest(HttpObject httpObject) {
        if (httpObject instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) httpObject;
            if (StringUtils.isNotEmpty(headersSpecificFilter)) {
                if (getFullUrl(httpRequest).matches(headersSpecificFilter)) {
                    for (Map.Entry<String, String> header : additionalHeaders.entrySet()) {
                        httpRequest.headers().add(header.getKey(), header.getValue());
                    }
                }
            } else {
                for (Map.Entry<String, String> header : additionalHeaders.entrySet()) {
                    httpRequest.headers().add(header.getKey(), header.getValue());
                }
            }
        }

        return null;
    }

    public String getHeadersSpecificFilter() {
        return headersSpecificFilter;
    }

    private synchronized void setHeadersSpecificFilter(String headersSpecificFilter) {
        this.headersSpecificFilter = headersSpecificFilter;
    }
}
