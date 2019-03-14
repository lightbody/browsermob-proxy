package net.lightbody.bmp.filters

import com.google.common.collect.ImmutableList
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpRequest
import io.netty.util.Attribute
import io.netty.util.AttributeKey
import net.lightbody.bmp.BrowserMobProxy
import net.lightbody.bmp.BrowserMobProxyServer
import net.lightbody.bmp.proxy.RewriteRule
import net.lightbody.bmp.proxy.test.util.MockServerTest
import net.lightbody.bmp.proxy.test.util.NewProxyServerTestUtil
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.junit.After
import org.junit.Test
import org.mockserver.matchers.Times

import static org.junit.Assert.assertEquals
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response

class RewriteUrlFilterTest extends MockServerTest {
    BrowserMobProxy proxy

    @After
    void tearDown() {
        if (proxy?.started) {
            proxy.abort()
        }
    }

    @Test
    void testRewriteWithCaptureGroups() {
        HttpHeaders mockHeaders = mock(HttpHeaders.class)
        when(mockHeaders.contains(HttpHeaders.Names.HOST)).thenReturn(false)

        HttpRequest request = mock(HttpRequest.class);
        when(request.getUri()).thenReturn('http://www.yahoo.com?param=someValue');
        when(request.headers()).thenReturn(mockHeaders)

        Collection<RewriteRule> rewriteRules = ImmutableList.of(new RewriteRule('http://www\\.(yahoo|bing)\\.com\\?(\\w+)=(\\w+)', 'http://www.google.com?originalDomain=$1&$2=$3'));

        // mock out the netty ChannelHandlerContext for the isHttps() call in the filter
        Attribute<Boolean> mockIsHttpsAttribute = mock(Attribute)
        when(mockIsHttpsAttribute.get()).thenReturn(Boolean.FALSE)

        ChannelHandlerContext mockCtx = mock(ChannelHandlerContext)
        when(mockCtx.attr(AttributeKey.<Boolean> valueOf(HttpsAwareFiltersAdapter.IS_HTTPS_ATTRIBUTE_NAME)))
                .thenReturn(mockIsHttpsAttribute)

        RewriteUrlFilter filter = new RewriteUrlFilter(request, mockCtx, rewriteRules);
        filter.clientToProxyRequest(request);

        verify(request).setUri('http://www.google.com?originalDomain=yahoo&param=someValue');
    }

    @Test
    void testRewriteMultipleMatches() {
        HttpHeaders mockHeaders = mock(HttpHeaders.class)
        when(mockHeaders.contains(HttpHeaderNames.HOST)).thenReturn(false)

        HttpRequest request = mock(HttpRequest.class);
        when(request.uri()).thenReturn('http://www.yahoo.com?param=someValue');
        when(request.headers()).thenReturn(mockHeaders)

        Collection<RewriteRule> rewriteRules = ImmutableList.of(
                new RewriteRule('http://www\\.yahoo\\.com\\?(\\w+)=(\\w+)', 'http://www.bing.com?new$1=new$2'),
                new RewriteRule('http://www\\.(yahoo|bing)\\.com\\?(\\w+)=(\\w+)', 'http://www.google.com?originalDomain=$1&$2=$3')
        );

        // mock out the netty ChannelHandlerContext for the isHttps() call in the filter
        Attribute<Boolean> mockIsHttpsAttribute = mock(Attribute)
        when(mockIsHttpsAttribute.get()).thenReturn(Boolean.FALSE)

        ChannelHandlerContext mockCtx = mock(ChannelHandlerContext)
        when(mockCtx.attr(AttributeKey.<Boolean> valueOf(HttpsAwareFiltersAdapter.IS_HTTPS_ATTRIBUTE_NAME)))
                .thenReturn(mockIsHttpsAttribute)

        RewriteUrlFilter filter = new RewriteUrlFilter(request, mockCtx, rewriteRules);
        filter.clientToProxyRequest(request);

        verify(request).setUri("http://www.google.com?originalDomain=bing&newparam=newsomeValue");
    }

    @Test
    void testRewriteHttpHost() {
        mockServer.when(request()
                .withMethod("GET")
                .withPath("/testRewriteHttpHost")
                .withHeader("Host", "localhost:${mockServerPort}"),
                Times.exactly(2))
                .respond(response()
                .withStatusCode(200)
                .withBody("success"))

        proxy = new BrowserMobProxyServer()
        proxy.rewriteUrl('http://www\\.someotherhost\\.com:(\\d+)/(\\w+)', 'http://localhost:$1/$2')

        proxy.start()

        String url = "http://www.someotherhost.com:${mockServerPort}/testRewriteHttpHost"
        NewProxyServerTestUtil.getNewHttpClient(proxy.port).withCloseable {
            CloseableHttpResponse firstResponse = it.execute(new HttpGet(url))
            assertEquals("Did not receive HTTP 200 from mock server", 200, firstResponse.getStatusLine().getStatusCode())

            String firstResponseBody = NewProxyServerTestUtil.toStringAndClose(firstResponse.getEntity().getContent());
            assertEquals("Did not receive expected response from mock server", "success", firstResponseBody);

            CloseableHttpResponse secondResponse = it.execute(new HttpGet(url))
            assertEquals("Did not receive HTTP 200 from mock server", 200, secondResponse.getStatusLine().getStatusCode())

            String secondResponseBody = NewProxyServerTestUtil.toStringAndClose(secondResponse.getEntity().getContent());
            assertEquals("Did not receive expected response from mock server", "success", secondResponseBody);
        };
    }

    @Test
    void testRewriteHttpResource() {
        mockServer.when(request()
                .withMethod("GET")
                .withPath("/rewrittenresource"),
                Times.exactly(2))
                .respond(response()
                .withStatusCode(200)
                .withBody("success"))

        proxy = new BrowserMobProxyServer()
        proxy.rewriteUrl('http://badhost:(\\d+)/badresource', 'http://localhost:$1/rewrittenresource')

        proxy.start()

        String url = "http://badhost:${mockServerPort}/badresource"
        NewProxyServerTestUtil.getNewHttpClient(proxy.port).withCloseable {
            String firstResponseBody = NewProxyServerTestUtil.toStringAndClose(it.execute(new HttpGet(url)).getEntity().getContent());
            assertEquals("Did not receive expected response from mock server", "success", firstResponseBody);

            String secondResponseBody = NewProxyServerTestUtil.toStringAndClose(it.execute(new HttpGet(url)).getEntity().getContent());
            assertEquals("Did not receive expected response from mock server", "success", secondResponseBody);
        };
    }

    @Test
    void testRewriteHttpsResource() {
        mockServer.when(request()
                .withMethod("GET")
                .withPath("/rewrittenresource"),
                Times.exactly(2))
                .respond(response()
                .withStatusCode(200)
                .withBody("success"))

        proxy = new BrowserMobProxyServer()
        proxy.setTrustAllServers(true)
        proxy.rewriteUrl('https://localhost:(\\d+)/badresource', 'https://localhost:$1/rewrittenresource')

        proxy.start()

        String url = "https://localhost:${mockServerPort}/badresource"
        NewProxyServerTestUtil.getNewHttpClient(proxy.port).withCloseable {
            String firstResponseBody = NewProxyServerTestUtil.toStringAndClose(it.execute(new HttpGet(url)).getEntity().getContent());
            assertEquals("Did not receive expected response from mock server", "success", firstResponseBody);

            String secondResponseBody = NewProxyServerTestUtil.toStringAndClose(it.execute(new HttpGet(url)).getEntity().getContent());
            assertEquals("Did not receive expected response from mock server", "success", secondResponseBody);
        };
    }
}
