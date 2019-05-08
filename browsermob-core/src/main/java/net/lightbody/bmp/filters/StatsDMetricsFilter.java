package net.lightbody.bmp.filters;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.net.URI;
import java.net.URISyntaxException;

public class StatsDMetricsFilter extends HttpsAwareFiltersAdapter {
    public StatsDMetricsFilter(HttpRequest originalRequest, ChannelHandlerContext ctx) {
        super(originalRequest, ctx);
    }

    @Override
    public HttpObject proxyToClientResponse(HttpObject httpObject) {
        if (FullHttpResponse.class.isAssignableFrom(httpObject.getClass())) {
            HttpResponse httpResponse = (FullHttpResponse) httpObject;
            prepareStatsDMetrics(httpResponse.status().code());
        }
        return super.serverToProxyResponse(httpObject);
    }

    private void prepareStatsDMetrics(int status) {
        if (status > 399 || status == 0) {
            String url = getFullUrl(originalRequest);
            String metric = getProxyPrefix().concat(
                    prepareMetric(url)).concat(String.format(".%s", status));
            StatsDClient client = new NonBlockingStatsDClient("automated_tests", getStatsDHost(), getStatsDPort());
            client.increment(metric);
            client.stop();
        }
    }


    protected static String getStatsDHost() {
        return StringUtils.isEmpty(System.getenv("STATSD_HOST")) ? "localhost" : System.getenv("STATSD_HOST");
    }

    protected static int getStatsDPort() {
        return StringUtils.isEmpty(System.getenv("STATSD_PORT")) ? 8125 : NumberUtils.toInt(System.getenv("STATSD_PORT"));
    }

    protected static String getProxyPrefix() {
        return "proxy.";
    }

    protected static String prepareMetric(String initialUrl) {
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
