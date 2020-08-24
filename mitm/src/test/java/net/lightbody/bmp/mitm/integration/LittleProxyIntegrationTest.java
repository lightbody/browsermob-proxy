package net.lightbody.bmp.mitm.integration;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.littleshoot.proxy.*;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests out-of-the-box integration with LittleProxy.
 */
public class LittleProxyIntegrationTest {
    @Test
    public void testLittleProxyMitm() throws IOException, InterruptedException {
        final AtomicBoolean interceptedGetRequest = new AtomicBoolean();
        final AtomicBoolean interceptedGetResponse = new AtomicBoolean();

        HttpFiltersSource filtersSource = new HttpFiltersSourceAdapter() {
            @Override
            public HttpFilters filterRequest(HttpRequest originalRequest) {
                return new HttpFiltersAdapter(originalRequest) {
                    @Override
                    public HttpResponse proxyToServerRequest(HttpObject httpObject) {
                        if (httpObject instanceof HttpRequest) {
                            HttpRequest httpRequest = (HttpRequest) httpObject;
                            if (httpRequest.method().equals(HttpMethod.GET)) {
                                interceptedGetRequest.set(true);
                            }
                        }

                        return super.proxyToServerRequest(httpObject);
                    }

                    @Override
                    public HttpObject serverToProxyResponse(HttpObject httpObject) {
                        if (httpObject instanceof HttpResponse) {
                            HttpResponse httpResponse = (HttpResponse) httpObject;
                            if (httpResponse.status().code() == 200) {
                                interceptedGetResponse.set(true);
                            }
                        }
                        return super.serverToProxyResponse(httpObject);
                    }
                };
            }
        };

        ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder().build();

        HttpProxyServer proxyServer = DefaultHttpProxyServer.bootstrap()
                .withPort(0)
                .withManInTheMiddle(mitmManager)
                .withFiltersSource(filtersSource)
                .start();

        try (CloseableHttpClient httpClient = getNewHttpClient(proxyServer.getListenAddress().getPort())) {
            try (CloseableHttpResponse response = httpClient.execute(new HttpGet("https://www.google.com"))) {
                assertEquals("Expected to receive an HTTP 200 from http://www.google.com", 200, response.getStatusLine().getStatusCode());

                EntityUtils.consume(response.getEntity());
            }
        }

        Thread.sleep(500);

        assertTrue("Expected HttpFilters to successfully intercept the HTTP GET request", interceptedGetRequest.get());
        assertTrue("Expected HttpFilters to successfully intercept the server's response to the HTTP GET", interceptedGetResponse.get());

        proxyServer.abort();
    }

    /**
     * Creates an HTTP client that trusts all upstream servers and uses a localhost proxy on the specified port.
     */
    private static CloseableHttpClient getNewHttpClient(int proxyPort) {
        try {
            // Trust all certs -- under no circumstances should this ever be used outside of testing
            SSLContext sslcontext = SSLContexts.custom().setProtocol("TLS")
                    .loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true)
                    .build();

            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                    sslcontext,
                    new DefaultHostnameVerifier());

            CloseableHttpClient httpclient = HttpClients.custom()
                    .setSSLSocketFactory(sslsf)
                    .setProxy(new HttpHost("127.0.0.1", proxyPort))
                    // disable decompressing content, since some tests want uncompressed content for testing purposes
                    .disableContentCompression()
                    .disableAutomaticRetries()
                    .build();

            return httpclient;
        } catch (Exception e) {
            throw new RuntimeException("Unable to create new HTTP client", e);
        }
    }
}
