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
import java.util.ArrayDeque;

public class StatsDMetricsFilter extends HttpsAwareFiltersAdapter {
    private StatsDClient client;
    private static ArrayDeque<HttpRequest> HTTP_REQUEST_STORAGE = new ArrayDeque<>();
    private static final Logger log = LoggerFactory.getLogger(StatsDMetricsFilter.class);


    public StatsDMetricsFilter(HttpRequest originalRequest, ChannelHandlerContext ctx) {
        super(originalRequest, ctx);
        this.client = new NonBlockingStatsDClient("automated_tests", getStatsDHost(), getStatsDPort());
    }

    @Override
    public HttpResponse clientToProxyRequest(HttpObject httpObject) {
        if (httpObject instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) httpObject;
            HTTP_REQUEST_STORAGE.push(httpRequest);
        }
        return null;
    }


    @Override
    public HttpObject serverToProxyResponse(HttpObject httpObject) {
        if (httpObject instanceof HttpResponse) {
            HttpResponse httpResponse = (HttpResponse) httpObject;
            int status = httpResponse.status().code();
            if (status > 399 || status == 0) {
                String metric;
                HttpRequest request = HTTP_REQUEST_STORAGE.pop();
                String url = getFullUrl(request);
                if (status >= 500) {
                    MDC.put("caller", "mobproxy");
                    MDC.put("http_response_code", String.valueOf(status));
                    MDC.put("http_host", url);
                    MDC.put("request_details", request.toString());
                    MDC.put("method", request.method().name());
                    log.error("received bad status code {}", status);
                }
                metric = getProxyPrefix().concat(
                        prepareMetric(url)).concat(String.format(".%s", status));
                client.increment(metric);
                HTTP_REQUEST_STORAGE.clear();
            }
        }
        return super.serverToProxyResponse(httpObject);
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
