package net.lightbody.bmp.filters;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.URI;
import java.net.URISyntaxException;

public class StatsDMetricsFilter extends HttpsAwareFiltersAdapter {
    private StatsDClient client;
    private static InheritableThreadLocal<HttpRequest> HTTP_REQUEST_STORAGE = new InheritableThreadLocal<>();
    private static final Logger log = LoggerFactory.getLogger(StatsDMetricsFilter.class);


    public StatsDMetricsFilter(HttpRequest originalRequest, ChannelHandlerContext ctx) {
        super(originalRequest, ctx);
        this.client = new NonBlockingStatsDClient("automated_tests", getStatsDHost(), getStatsDPort());
    }

    @Override
    public HttpResponse clientToProxyRequest(HttpObject httpObject) {
        if (httpObject instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) httpObject;
            HTTP_REQUEST_STORAGE.set(httpRequest);
        }
        return null;
    }


    @Override
    public HttpObject serverToProxyResponse(HttpObject httpObject) {
        if (HttpResponse.class.isAssignableFrom(httpObject.getClass())) {
            HttpResponse httpResponse = (HttpResponse) httpObject;
            int status = httpResponse.status().code();
            prepareStatsDMetrics(status, (HttpResponse) httpObject);
        }
        return super.serverToProxyResponse(httpObject);
    }

    private void prepareStatsDMetrics(int status, HttpResponse httpResponse) {
        if (status > 399 || status == 0) {
            String metric;
            HttpRequest request = HTTP_REQUEST_STORAGE.get();
            String url = getFullUrl(request);
            logFailedRequestIfRequired(status, httpResponse, request, url);
            metric = getProxyPrefix().concat(
                    prepareMetric(url)).concat(String.format(".%s", status));
            client.increment(metric);
            HTTP_REQUEST_STORAGE.remove();
        }
    }

    private void logFailedRequestIfRequired(int status, HttpResponse response, HttpRequest request, String url) {
        if (status >= 500) {
            MDC.put("caller", "mobproxy");
            MDC.put("http_response_code", String.valueOf(status));
            MDC.put("http_host", url);
            MDC.put("request_details", request.toString());
            MDC.put("method", request.method().name());
            MDC.put("response", response.toString());
            log.error("received bad status code");
        }
    }

    public static String getStatsDHost() {
        return StringUtils.isEmpty(System.getenv("STATSD_HOST")) ? "graphite000.tools.hellofresh.io" : System.getenv("STATSD_HOST");
    }

    public static int getStatsDPort() {
        return StringUtils.isEmpty(System.getenv("STATSD_PORT")) ? 8125 : NumberUtils.toInt(System.getenv("STATSD_PORT"));
    }

    public static String getProxyPrefix() {
        return "proxy.";
    }

    public static String prepareMetric(String initialUrl) {
        URI uri = null;
        try {
            uri = new URI(initialUrl);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return uri.getHost().concat(uri.getPath()).replaceAll("/", "_")
                .replaceAll("\\.", "_");
    }

}
