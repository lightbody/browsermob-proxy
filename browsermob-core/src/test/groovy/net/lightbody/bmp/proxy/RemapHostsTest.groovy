package net.lightbody.bmp.proxy

import net.lightbody.bmp.BrowserMobProxy
import net.lightbody.bmp.BrowserMobProxyServer
import net.lightbody.bmp.proxy.test.util.MockServerTest
import net.lightbody.bmp.proxy.test.util.NewProxyServerTestUtil
import org.apache.http.client.methods.HttpGet
import org.junit.After
import org.junit.Test
import org.mockserver.matchers.Times

import static org.junit.Assert.assertEquals
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response

/**
 * Tests host remapping using the {@link net.lightbody.bmp.proxy.dns.AdvancedHostResolver#remapHost(java.lang.String, java.lang.String)}
 * and related methods exposes by {@link BrowserMobProxy#getHostNameResolver()}.
 */
class RemapHostsTest extends MockServerTest {
    private BrowserMobProxy proxy

    @After
    void tearDown() {
        if (proxy?.started) {
            proxy.abort()
        }
    }

    @Test
    void testRemapHttpHost() {
        // mock up a response to serve
        mockServer.when(request()
                .withMethod("GET")
                .withPath("/remapHttpHost"),
                Times.exactly(1))
                .respond(response()
                .withStatusCode(200)
                .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.setTrustAllServers(true)

        proxy.getHostNameResolver().remapHost("www.someaddress.notreal", "localhost")

        proxy.start();

        int proxyPort = proxy.getPort();

        NewProxyServerTestUtil.getNewHttpClient(proxyPort).withCloseable {
            String responseBody = NewProxyServerTestUtil.toStringAndClose(it.execute(new HttpGet("http://www.someaddress.notreal:${mockServerPort}/remapHttpHost")).getEntity().getContent());
            assertEquals("Did not receive expected response from mock server", "success", responseBody);
        };
    }

    @Test
    void testRemapHttpsHost() {
        // mock up a response to serve
        mockServer.when(request()
                .withMethod("GET")
                .withPath("/remapHttpsHost"),
                Times.exactly(1))
                .respond(response()
                .withStatusCode(200)
                .withBody("success"));

        proxy = new BrowserMobProxyServer();
        proxy.setTrustAllServers(true)

        proxy.getHostNameResolver().remapHost("www.someaddress.notreal", "localhost")

        proxy.start();

        int proxyPort = proxy.getPort();

        NewProxyServerTestUtil.getNewHttpClient(proxyPort).withCloseable {
            String responseBody = NewProxyServerTestUtil.toStringAndClose(it.execute(new HttpGet("https://www.someaddress.notreal:${mockServerPort}/remapHttpsHost")).getEntity().getContent());
            assertEquals("Did not receive expected response from mock server", "success", responseBody);
        };
    }
}
