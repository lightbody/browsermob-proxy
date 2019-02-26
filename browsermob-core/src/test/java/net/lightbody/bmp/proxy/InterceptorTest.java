package net.lightbody.bmp.proxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.filters.RequestFilter;
import net.lightbody.bmp.filters.RequestFilterAdapter;
import net.lightbody.bmp.filters.ResponseFilter;
import net.lightbody.bmp.filters.ResponseFilterAdapter;
import net.lightbody.bmp.proxy.test.util.MockServerTest;
import net.lightbody.bmp.proxy.test.util.NewProxyServerTestUtil;
import net.lightbody.bmp.util.HttpMessageContents;
import net.lightbody.bmp.util.HttpMessageInfo;
import net.lightbody.bmp.util.HttpObjectUtil;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.After;
import org.junit.Test;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.mockserver.matchers.Times;
import org.mockserver.model.Header;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class InterceptorTest extends MockServerTest {
    private BrowserMobProxy proxy;

    @After
    public void tearDown() {
        if (proxy != null && proxy.isStarted()) {
            proxy.abort();
        }
    }

    @Test
    public void testCanShortCircuitResponse() throws IOException {
        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/regular200"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withBody("success"));

        // this response should be "short-circuited" by the interceptor
        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/shortcircuit204"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.start();

        final AtomicBoolean interceptorFired = new AtomicBoolean(false);
        final AtomicBoolean shortCircuitFired= new AtomicBoolean(false);

        proxy.addFirstHttpFilterFactory(new HttpFiltersSourceAdapter() {
            @Override
            public HttpFilters filterRequest(HttpRequest originalRequest) {
                return new HttpFiltersAdapter(originalRequest) {
                    @Override
                    public HttpResponse clientToProxyRequest(HttpObject httpObject) {
                        if (httpObject instanceof HttpRequest) {
                            interceptorFired.set(true);

                            HttpRequest httpRequest = (HttpRequest) httpObject;

                            if (httpRequest.method().equals(HttpMethod.GET) && httpRequest.uri().contains("/shortcircuit204")) {
                                HttpResponse httpResponse = new DefaultHttpResponse(httpRequest.protocolVersion(), HttpResponseStatus.NO_CONTENT);

                                shortCircuitFired.set(true);

                                return httpResponse;
                            }
                        }

                        return super.clientToProxyRequest(httpObject);
                    }
                };
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            CloseableHttpResponse response = httpClient.execute(new HttpGet("http://localhost:" + mockServerPort + "/regular200"));
            String responseBody = NewProxyServerTestUtil.toStringAndClose(response.getEntity().getContent());

            assertTrue("Expected interceptor to fire", interceptorFired.get());
            assertFalse("Did not expected short circuit interceptor code to execute", shortCircuitFired.get());

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertEquals("Did not receive expected response from mock server", "success", responseBody);
        }

        interceptorFired.set(false);

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            CloseableHttpResponse response = httpClient.execute(new HttpGet("http://localhost:" + mockServerPort + "/shortcircuit204"));

            assertTrue("Expected interceptor to fire", interceptorFired.get());
            assertTrue("Expected interceptor to short-circuit response", shortCircuitFired.get());

            assertEquals("Expected interceptor to return a 204 (No Content)", 204, response.getStatusLine().getStatusCode());
            assertNull("Expected no entity attached to response", response.getEntity());
        }
    }

    @Test
    public void testCanModifyResponseBodyLarger() throws IOException {
        final String originalText = "The quick brown fox jumps over the lazy dog";
        final String newText = "The quick brown frog jumps over the lazy aardvark";

        testModifiedResponse(originalText, newText);
    }

    @Test
    public void testCanModifyResponseBodySmaller() throws IOException {
        final String originalText = "The quick brown fox jumps over the lazy dog";
        final String newText = "The quick brown fox jumped.";

        testModifiedResponse(originalText, newText);
    }

    @Test
    public void testCanModifyRequest() throws IOException {
        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/modifyrequest"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=utf-8"))
                        .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.start();

        proxy.addFirstHttpFilterFactory(new HttpFiltersSourceAdapter() {
            @Override
            public HttpFilters filterRequest(HttpRequest originalRequest) {
                return new HttpFiltersAdapter(originalRequest) {
                    @Override
                    public HttpResponse clientToProxyRequest(HttpObject httpObject) {
                        if (httpObject instanceof HttpRequest) {
                            HttpRequest httpRequest = (HttpRequest) httpObject;
                            httpRequest.setUri(httpRequest.getUri().replace("/originalrequest", "/modifyrequest"));
                        }

                        return super.clientToProxyRequest(httpObject);
                    }
                };
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            CloseableHttpResponse response = httpClient.execute(new HttpGet("http://localhost:" + mockServerPort + "/originalrequest"));
            String responseBody = NewProxyServerTestUtil.toStringAndClose(response.getEntity().getContent());

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertEquals("Did not receive expected response from mock server", "success", responseBody);
        }
    }

    @Test
    public void testRequestFilterCanModifyHttpRequestBody() throws IOException {
        final String originalText = "original body";
        final String newText = "modified body";

        mockServer.when(request()
                        .withMethod("PUT")
                        .withPath("/modifyrequest")
                        .withBody(newText),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.start();

        proxy.addRequestFilter(new RequestFilter() {
            @Override
            public HttpResponse filterRequest(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                if (contents.isText()) {
                    if (contents.getTextContents().equals(originalText)) {
                        contents.setTextContents(newText);
                    }
                }

                return null;
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            HttpPut request = new HttpPut("http://localhost:" + mockServerPort + "/modifyrequest");
            request.setEntity(new StringEntity(originalText));
            CloseableHttpResponse response = httpClient.execute(request);
            String responseBody = NewProxyServerTestUtil.toStringAndClose(response.getEntity().getContent());

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertEquals("Did not receive expected response from mock server", "success", responseBody);
        }
    }

    @Test
    public void testRequestFilterCanModifyHttpsRequestBody() throws IOException {
        final String originalText = "original body";
        final String newText = "modified body";

        mockServer.when(request()
                        .withMethod("PUT")
                        .withPath("/modifyrequest")
                        .withBody(newText),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.setTrustAllServers(true);
        proxy.start();

        proxy.addRequestFilter(new RequestFilter() {
            @Override
            public HttpResponse filterRequest(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                if (contents.isText()) {
                    if (contents.getTextContents().equals(originalText)) {
                        contents.setTextContents(newText);
                    }
                }

                return null;
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            HttpPut request = new HttpPut("https://localhost:" + mockServerPort + "/modifyrequest");
            request.setEntity(new StringEntity(originalText));
            CloseableHttpResponse response = httpClient.execute(request);
            String responseBody = NewProxyServerTestUtil.toStringAndClose(response.getEntity().getContent());

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertEquals("Did not receive expected response from mock server", "success", responseBody);
        }
    }

    @Test
    public void testResponseFilterCanModifyBinaryContents() throws IOException {
        final byte[] originalBytes = new byte[] {1, 2, 3, 4, 5};
        final byte[] newBytes = new byte[] {20, 30, 40, 50, 60};

        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/modifyresponse"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, "application/octet-stream"))
                        .withBody(originalBytes));

        proxy = new BrowserMobProxyServer();
        proxy.start();

        proxy.addResponseFilter(new ResponseFilter() {
            @Override
            public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                if (!contents.isText()) {
                    if (Arrays.equals(originalBytes, contents.getBinaryContents())) {
                        contents.setBinaryContents(newBytes);
                    }
                }
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            HttpGet request = new HttpGet("http://localhost:" + mockServerPort + "/modifyresponse");
            CloseableHttpResponse response = httpClient.execute(request);
            byte[] responseBytes = org.apache.commons.io.IOUtils.toByteArray(response.getEntity().getContent());

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertThat("Did not receive expected response from mock server", responseBytes, equalTo(newBytes));
        }
    }

    @Test
    public void testResponseFilterCanModifyHttpTextContents() throws IOException {
        final String originalText = "The quick brown fox jumps over the lazy dog";
        final String newText = "The quick brown fox jumped.";

        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/modifyresponse"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=utf-8"))
                        .withBody(originalText));

        proxy = new BrowserMobProxyServer();
        proxy.start();

        proxy.addResponseFilter(new ResponseFilter() {
            @Override
            public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                if (contents.isText()) {
                    if (contents.getTextContents().equals(originalText)) {
                        contents.setTextContents(newText);
                    }
                }
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            HttpGet request = new HttpGet("http://localhost:" + mockServerPort + "/modifyresponse");
            request.addHeader("Accept-Encoding", "gzip");
            CloseableHttpResponse response = httpClient.execute(request);
            String responseBody = NewProxyServerTestUtil.toStringAndClose(response.getEntity().getContent());

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertEquals("Did not receive expected response from mock server", newText, responseBody);
        }
    }

    @Test
    public void testResponseFilterCanModifyHttpsTextContents() throws IOException {
        final String originalText = "The quick brown fox jumps over the lazy dog";
        final String newText = "The quick brown fox jumped.";

        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/modifyresponse"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=utf-8"))
                        .withBody(originalText));

        proxy = new BrowserMobProxyServer();
        proxy.setTrustAllServers(true);
        proxy.start();

        proxy.addResponseFilter(new ResponseFilter() {
            @Override
            public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                if (contents.isText()) {
                    if (contents.getTextContents().equals(originalText)) {
                        contents.setTextContents(newText);
                    }
                }
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            HttpGet request = new HttpGet("https://localhost:" + mockServerPort + "/modifyresponse");
            request.addHeader("Accept-Encoding", "gzip");
            CloseableHttpResponse response = httpClient.execute(request);
            String responseBody = NewProxyServerTestUtil.toStringAndClose(response.getEntity().getContent());

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertEquals("Did not receive expected response from mock server", newText, responseBody);
        }
    }

    @Test
    public void testResponseInterceptorWithoutBody() throws IOException {
        mockServer.when(request()
                        .withMethod("HEAD")
                        .withPath("/interceptortest"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, "application/octet-stream")));

        proxy = new BrowserMobProxyServer();
        proxy.start();

        final AtomicReference<byte[]> responseContents = new AtomicReference<>();

        proxy.addResponseFilter(new ResponseFilter() {
            @Override
            public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                responseContents.set(contents.getBinaryContents());
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            CloseableHttpResponse response = httpClient.execute(new HttpHead("http://localhost:" + mockServerPort + "/interceptortest"));

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertEquals("Expected binary contents captured in interceptor to be empty", 0, responseContents.get().length);
        }
    }

    @Test
    public void testResponseFilterOriginalRequestNotModified() throws IOException {
        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/modifiedendpoint"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.start();

        proxy.addRequestFilter(new RequestFilter() {
            @Override
            public HttpResponse filterRequest(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                if (request.getUri().endsWith("/originalendpoint")) {
                    request.setUri(request.getUri().replaceAll("originalendpoint", "modifiedendpoint"));
                }

                return null;
            }
        });

        final AtomicReference<String> originalRequestUri = new AtomicReference<>();

        proxy.addResponseFilter(new ResponseFilter() {
            @Override
            public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                originalRequestUri.set(messageInfo.getOriginalRequest().getUri());
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            CloseableHttpResponse response = httpClient.execute(new HttpGet("http://localhost:" + mockServerPort + "/originalendpoint"));

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertThat("Expected URI on originalRequest to match actual URI of original HTTP request", originalRequestUri.get(), endsWith("/originalendpoint"));
        }
    }

    @Test
    public void testMessageContentsNotAvailableWithoutAggregation() throws IOException {
        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/endpoint"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.start();

        final AtomicBoolean requestContentsNull = new AtomicBoolean(false);
        final AtomicBoolean responseContentsNull = new AtomicBoolean(false);

        proxy.addFirstHttpFilterFactory(new RequestFilterAdapter.FilterSource(new RequestFilter() {
            @Override
            public HttpResponse filterRequest(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                if (contents == null) {
                    requestContentsNull.set(true);
                }

                return null;
            }
        }, 0));

        proxy.addFirstHttpFilterFactory(new ResponseFilterAdapter.FilterSource(new ResponseFilter() {
            @Override
            public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                if (contents == null) {
                    responseContentsNull.set(true);
                }
            }
        }, 0));

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            CloseableHttpResponse response = httpClient.execute(new HttpGet("http://localhost:" + mockServerPort + "/endpoint"));

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertTrue("Expected HttpMessageContents to be null in RequestFilter because HTTP message aggregation is disabled", requestContentsNull.get());
            assertTrue("Expected HttpMessageContents to be null in ResponseFilter because HTTP message aggregation is disabled", responseContentsNull.get());
        }
    }

    @Test
    public void testMitmDisabledHttpsRequestFilterNotAvailable() throws IOException {
        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/mitmdisabled"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.setMitmDisabled(true);

        proxy.start();

        final AtomicBoolean connectRequestFilterFired = new AtomicBoolean(false);
        final AtomicBoolean getRequestFilterFired = new AtomicBoolean(false);

        proxy.addRequestFilter(new RequestFilter() {
            @Override
            public HttpResponse filterRequest(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                if (request.getMethod().equals(HttpMethod.CONNECT)) {
                    connectRequestFilterFired.set(true);
                } else if (request.getMethod().equals(HttpMethod.GET)) {
                    getRequestFilterFired.set(true);
                }
                return null;
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            CloseableHttpResponse response = httpClient.execute(new HttpGet("https://localhost:" + mockServerPort + "/mitmdisabled"));

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());

            assertTrue("Expected request filter to fire on CONNECT", connectRequestFilterFired.get());
            assertFalse("Expected request filter to fail to fire on GET because MITM is disabled", getRequestFilterFired.get());
        }
    }

    @Test
    public void testMitmDisabledHttpsResponseFilterNotAvailable() throws IOException {
        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/mitmdisabled"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.setMitmDisabled(true);

        proxy.start();

        // unlike the request filter, the response filter doesn't fire when the 200 response to the CONNECT is sent to the client.
        // this is because the response filter is triggered when the serverToProxyResponse() filtering method is called, and
        // the "200 Connection established" is generated by the proxy itself.

        final AtomicBoolean responseFilterFired = new AtomicBoolean(false);

        proxy.addResponseFilter(new ResponseFilter() {
            @Override
            public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                responseFilterFired.set(true);
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            CloseableHttpResponse response = httpClient.execute(new HttpGet("https://localhost:" + mockServerPort + "/mitmdisabled"));

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertFalse("Expected response filter to fail to fire because MITM is disabled", responseFilterFired.get());
        }
    }

    /**
     * Helper method for executing response modification tests.
     */
    private void testModifiedResponse(final String originalText, final String newText) throws IOException {
        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/modifyresponse"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=utf-8"))
                        .withBody(originalText));

        proxy = new BrowserMobProxyServer();
        proxy.start();

        proxy.addFirstHttpFilterFactory(new HttpFiltersSourceAdapter() {
            @Override
            public HttpFilters filterRequest(HttpRequest originalRequest) {
                return new HttpFiltersAdapter(originalRequest) {
                    @Override
                    public HttpObject proxyToClientResponse(HttpObject httpObject) {
                        if (httpObject instanceof FullHttpResponse) {
                            FullHttpResponse httpResponseAndContent = (FullHttpResponse) httpObject;

                            String bodyContent = HttpObjectUtil.extractHttpEntityBody(httpResponseAndContent);

                            if (bodyContent.equals(originalText)) {
                                HttpObjectUtil.replaceTextHttpEntityBody(httpResponseAndContent, newText);
                            }
                        }

                        return super.proxyToClientResponse(httpObject);
                    }
                };
            }

            @Override
            public int getMaximumResponseBufferSizeInBytes() {
                return 10000;
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            CloseableHttpResponse response = httpClient.execute(new HttpGet("http://localhost:" + mockServerPort + "/modifyresponse"));
            String responseBody = NewProxyServerTestUtil.toStringAndClose(response.getEntity().getContent());

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertEquals("Did not receive expected response from mock server", newText, responseBody);
        }
    }

    @Test
    public void testCanBypassFilterForRequest() throws IOException, InterruptedException {
        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/bypassfilter"),
                Times.exactly(2))
                .respond(response()
                        .withStatusCode(200)
                        .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.start();

        final AtomicInteger filtersSourceHitCount = new AtomicInteger();
        final AtomicInteger filterHitCount = new AtomicInteger();

        proxy.addFirstHttpFilterFactory(new HttpFiltersSourceAdapter() {
            @Override
            public HttpFilters filterRequest(HttpRequest originalRequest) {
                if (filtersSourceHitCount.getAndIncrement() == 0) {
                    return null;
                } else {
                    return new HttpFiltersAdapter(originalRequest) {
                        @Override
                        public void serverToProxyResponseReceived() {
                            filterHitCount.incrementAndGet();
                        }
                    };
                }
            }
        });

        // during the first request, the filterRequest(...) method should return null, which will prevent the filter instance from
        // being added to the filter chain
        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            CloseableHttpResponse response = httpClient.execute(new HttpGet("http://localhost:" + mockServerPort + "/bypassfilter"));
            String responseBody = NewProxyServerTestUtil.toStringAndClose(response.getEntity().getContent());

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertEquals("Did not receive expected response from mock server", "success", responseBody);
        }

        Thread.sleep(500);

        assertEquals("Expected filters source to be invoked on first request", 1, filtersSourceHitCount.get());
        assertEquals("Expected filter instance to be bypassed on first request", 0, filterHitCount.get());

        // during the second request, the filterRequest(...) method will return a filter instance, which should be invoked during processing
        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            CloseableHttpResponse response = httpClient.execute(new HttpGet("http://localhost:" + mockServerPort + "/bypassfilter"));
            String responseBody = NewProxyServerTestUtil.toStringAndClose(response.getEntity().getContent());

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertEquals("Did not receive expected response from mock server", "success", responseBody);
        }

        Thread.sleep(500);

        assertEquals("Expected filters source to be invoked again on second request", 2, filtersSourceHitCount.get());
        assertEquals("Expected filter instance to be invoked on second request (only)", 1, filterHitCount.get());
    }

    @Test
    public void testHttpResponseFilterMessageInfoPopulated() throws IOException {
        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/httpmessageinfopopulated")
                        .withQueryStringParameter("param1", "value1"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.start();

        final AtomicReference<ChannelHandlerContext> requestCtx = new AtomicReference<>();
        final AtomicReference<HttpRequest> requestOriginalRequest = new AtomicReference<>();
        final AtomicBoolean requestIsHttps = new AtomicBoolean(false);
        final AtomicReference<String> requestFilterOriginalUrl = new AtomicReference<>();
        final AtomicReference<String> requestFilterUrl = new AtomicReference<>();

        proxy.addRequestFilter(new RequestFilter() {
            @Override
            public HttpResponse filterRequest(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                requestCtx.set(messageInfo.getChannelHandlerContext());
                requestOriginalRequest.set(messageInfo.getOriginalRequest());
                requestIsHttps.set(messageInfo.isHttps());
                requestFilterOriginalUrl.set(messageInfo.getOriginalUrl());
                requestFilterUrl.set(messageInfo.getUrl());
                return null;
            }
        });

        final AtomicReference<ChannelHandlerContext> responseCtx = new AtomicReference<>();
        final AtomicReference<HttpRequest> responseOriginalRequest = new AtomicReference<>();
        final AtomicBoolean responseIsHttps = new AtomicBoolean(false);
        final AtomicReference<String> responseFilterOriginalUrl = new AtomicReference<>();
        final AtomicReference<String> responseFilterUrl = new AtomicReference<>();

        proxy.addResponseFilter(new ResponseFilter() {
            @Override
            public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                responseCtx.set(messageInfo.getChannelHandlerContext());
                responseOriginalRequest.set(messageInfo.getOriginalRequest());
                responseIsHttps.set(messageInfo.isHttps());
                responseFilterOriginalUrl.set(messageInfo.getOriginalUrl());
                responseFilterUrl.set(messageInfo.getUrl());
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            String requestUrl = "http://localhost:" + mockServerPort + "/httpmessageinfopopulated?param1=value1";
            CloseableHttpResponse response = httpClient.execute(new HttpGet(requestUrl));

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertNotNull("Expected ChannelHandlerContext to be populated in request filter", requestCtx.get());
            assertNotNull("Expected originalRequest to be populated in request filter", requestOriginalRequest.get());
            assertFalse("Expected isHttps to return false in request filter", requestIsHttps.get());
            assertEquals("Expected originalUrl in request filter to match actual request URL", requestUrl, requestFilterOriginalUrl.get());
            assertEquals("Expected url in request filter to match actual request URL", requestUrl, requestFilterUrl.get());

            assertNotNull("Expected ChannelHandlerContext to be populated in response filter", responseCtx.get());
            assertNotNull("Expected originalRequest to be populated in response filter", responseOriginalRequest.get());
            assertFalse("Expected isHttps to return false in response filter", responseIsHttps.get());
            assertEquals("Expected originalUrl in response filter to match actual request URL", requestUrl, responseFilterOriginalUrl.get());
            assertEquals("Expected url in response filter to match actual request URL", requestUrl, responseFilterUrl.get());
        }
    }

    @Test
    public void testHttpResponseFilterUrlReflectsModifications() throws IOException {
        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/urlreflectsmodifications"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.start();

        final AtomicReference<String> requestFilterOriginalUrl = new AtomicReference<>();
        final AtomicReference<String> requestFilterUrl = new AtomicReference<>();

        proxy.addRequestFilter(new RequestFilter() {
            @Override
            public HttpResponse filterRequest(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                requestFilterOriginalUrl.set(messageInfo.getOriginalUrl());
                requestFilterUrl.set(messageInfo.getUrl());
                return null;
            }
        });

        // request filters get added to the beginning of the filter chain, so add this uri-modifying request filter after
        // adding the capturing request filter above.
        proxy.addRequestFilter(new RequestFilter() {
            @Override
            public HttpResponse filterRequest(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                if (request.getUri().endsWith("/originalurl")) {
                    String newUrl = request.getUri().replaceAll("originalurl", "urlreflectsmodifications");
                    request.setUri(newUrl);
                }
                return null;
            }
        });

        final AtomicReference<String> responseFilterOriginalUrl = new AtomicReference<>();
        final AtomicReference<String> responseFilterUrl = new AtomicReference<>();

        proxy.addResponseFilter(new ResponseFilter() {
            @Override
            public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                responseFilterOriginalUrl.set(messageInfo.getOriginalUrl());
                responseFilterUrl.set(messageInfo.getUrl());
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            String originalRequestUrl = "http://localhost:" + mockServerPort + "/originalurl";
            String modifiedRequestUrl = "http://localhost:" + mockServerPort + "/urlreflectsmodifications";
            CloseableHttpResponse response = httpClient.execute(new HttpGet(originalRequestUrl));

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertEquals("Expected originalUrl in request filter to match actual request URL", originalRequestUrl, requestFilterOriginalUrl.get());
            assertEquals("Expected url in request filter to match modified request URL", modifiedRequestUrl, requestFilterUrl.get());

            assertEquals("Expected originalUrl in response filter to match actual request URL", originalRequestUrl, responseFilterOriginalUrl.get());
            assertEquals("Expected url in response filter to match modified request URL", modifiedRequestUrl, responseFilterUrl.get());
        }
    }

    @Test
    public void testHttpsResponseFilterUrlReflectsModifications() throws IOException {
        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/urlreflectsmodifications"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.setTrustAllServers(true);
        proxy.start();

        final AtomicReference<String> requestFilterOriginalUrl = new AtomicReference<>();
        final AtomicReference<String> requestFilterUrl = new AtomicReference<>();

        proxy.addRequestFilter(new RequestFilter() {
            @Override
            public HttpResponse filterRequest(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                requestFilterOriginalUrl.set(messageInfo.getOriginalUrl());
                requestFilterUrl.set(messageInfo.getUrl());
                return null;
            }
        });

        // request filters get added to the beginning of the filter chain, so add this uri-modifying request filter after
        // adding the capturing request filter above.
        proxy.addRequestFilter(new RequestFilter() {
            @Override
            public HttpResponse filterRequest(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                if (request.getUri().endsWith("/originalurl")) {
                    String newUrl = request.getUri().replaceAll("originalurl", "urlreflectsmodifications");
                    request.setUri(newUrl);
                }
                return null;
            }
        });

        final AtomicReference<String> responseFilterOriginalUrl = new AtomicReference<>();
        final AtomicReference<String> responseFilterUrl = new AtomicReference<>();

        proxy.addResponseFilter(new ResponseFilter() {
            @Override
            public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                responseFilterOriginalUrl.set(messageInfo.getOriginalUrl());
                responseFilterUrl.set(messageInfo.getUrl());
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            String originalRequestUrl = "https://localhost:" + mockServerPort + "/originalurl";
            String modifiedRequestUrl = "https://localhost:" + mockServerPort + "/urlreflectsmodifications";
            CloseableHttpResponse response = httpClient.execute(new HttpGet(originalRequestUrl));

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());
            assertEquals("Expected originalUrl in request filter to match actual request URL", originalRequestUrl, requestFilterOriginalUrl.get());
            assertEquals("Expected url in request filter to match modified request URL", modifiedRequestUrl, requestFilterUrl.get());

            assertEquals("Expected originalUrl in response filter to match actual request URL", originalRequestUrl, responseFilterOriginalUrl.get());
            assertEquals("Expected url in response filter to match modified request URL", modifiedRequestUrl, responseFilterUrl.get());
        }
    }

    @Test
    public void testHttpsResponseFilterMessageInfoPopulated() throws IOException {
        mockServer.when(request()
                        .withMethod("GET")
                        .withPath("/httpmessageinfopopulated")
                        .withQueryStringParameter("param1", "value1"),
                Times.exactly(1))
                .respond(response()
                        .withStatusCode(200)
                        .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.setTrustAllServers(true);
        proxy.start();

        final AtomicReference<ChannelHandlerContext> requestCtx = new AtomicReference<>();
        final AtomicReference<HttpRequest> requestOriginalRequest = new AtomicReference<>();
        final AtomicBoolean requestIsHttps = new AtomicBoolean(false);
        final AtomicReference<String> requestOriginalUrl = new AtomicReference<>();

        proxy.addRequestFilter(new RequestFilter() {
            @Override
            public HttpResponse filterRequest(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                requestCtx.set(messageInfo.getChannelHandlerContext());
                requestOriginalRequest.set(messageInfo.getOriginalRequest());
                requestIsHttps.set(messageInfo.isHttps());
                requestOriginalUrl.set(messageInfo.getOriginalUrl());
                return null;
            }
        });

        final AtomicReference<ChannelHandlerContext> responseCtx = new AtomicReference<>();
        final AtomicReference<HttpRequest> responseOriginalRequest = new AtomicReference<>();
        final AtomicBoolean responseIsHttps = new AtomicBoolean(false);
        final AtomicReference<String> responseOriginalUrl = new AtomicReference<>();

        proxy.addResponseFilter(new ResponseFilter() {
            @Override
            public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                responseCtx.set(messageInfo.getChannelHandlerContext());
                responseOriginalRequest.set(messageInfo.getOriginalRequest());
                responseIsHttps.set(messageInfo.isHttps());
                responseOriginalUrl.set(messageInfo.getOriginalUrl());
            }
        });

        try (CloseableHttpClient httpClient = NewProxyServerTestUtil.getNewHttpClient(proxy.getPort())) {
            String requestUrl = "https://localhost:" + mockServerPort + "/httpmessageinfopopulated?param1=value1";
            CloseableHttpResponse response = httpClient.execute(new HttpGet(requestUrl));

            assertEquals("Expected server to return a 200", 200, response.getStatusLine().getStatusCode());

            assertNotNull("Expected ChannelHandlerContext to be populated in request filter", requestCtx.get());
            assertNotNull("Expected originalRequest to be populated in request filter", requestOriginalRequest.get());
            assertTrue("Expected isHttps to return true in request filter", requestIsHttps.get());
            assertEquals("Expected originalUrl in request filter to match actual request URL", requestUrl, requestOriginalUrl.get());

            assertNotNull("Expected ChannelHandlerContext to be populated in response filter", responseCtx.get());
            assertNotNull("Expected originalRequest to be populated in response filter", responseOriginalRequest.get());
            assertTrue("Expected isHttps to return true in response filter", responseIsHttps.get());
            assertEquals("Expected originalUrl in response filter to match actual request URL", requestUrl, responseOriginalUrl.get());
        }
    }
}
