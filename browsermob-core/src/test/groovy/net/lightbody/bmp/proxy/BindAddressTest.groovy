package net.lightbody.bmp.proxy


import net.lightbody.bmp.BrowserMobProxy
import net.lightbody.bmp.BrowserMobProxyServer
import net.lightbody.bmp.proxy.test.util.MockServerTest
import net.lightbody.bmp.proxy.test.util.NewProxyServerTestUtil
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockserver.matchers.Times

import static org.junit.Assert.assertEquals
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response

class BindAddressTest extends MockServerTest {
    private BrowserMobProxy proxy

    @Before
    void setUp() {
    }

    @After
    void tearDown() {
        if (proxy?.started) {
            proxy.abort()
        }
    }

    @Test
    void testClientBindAddress() {
        mockServer.when(
                request().withMethod("GET")
                        .withPath("/clientbind"),
                Times.unlimited()
        ).respond(response().withStatusCode(200))

        // bind to loopback. ProxyServerTest.getNewHtpClient creates an HTTP client that connects to a proxy at 127.0.0.1
        proxy = new BrowserMobProxyServer()
        proxy.start(0, InetAddress.getLoopbackAddress())

        NewProxyServerTestUtil.getNewHttpClient(proxy.getPort()).withCloseable {
            def response = it.execute(new HttpGet("http://127.0.0.1:${mockServerPort}/clientbind"))
            assertEquals(200, response.statusLine.statusCode)
        }
    }

    @Test(expected = HttpHostConnectException.class)
    void testClientBindAddressCannotConnect() {
        mockServer.when(
                request().withMethod("GET")
                        .withPath("/clientbind"),
                Times.unlimited()
        ).respond(response().withStatusCode(200))

        // find the local host address to bind to that isn't loopback. since ProxyServerTest.getNewHtpClient creates an HTTP client that
        // connects to a proxy at 127.0.0.1, the HTTP client should *not* be able to connect to the proxy
        Socket socket = new Socket()
        socket.connect(new InetSocketAddress("google.com", 80))
        socket.getLocalAddress()

        proxy = new BrowserMobProxyServer()
        proxy.start(0, new InetAddress().getByName(socket.getLocalAddress().getHostAddress()))


        CloseableHttpClient httpclient = HttpClients.createDefault();
        CloseableHttpResponse response = httpclient.execute(new HttpGet("http://127.0.0.1:${mockServerPort}/clientbind"));

        NewProxyServerTestUtil.getNewHttpClient(proxy.getPort()).withCloseable {
            it.execute(new HttpGet("http://127.0.0.1:${mockServerPort}/clientbind"))
        }
    }

    @Test
    void testServerBindAddress() {
        mockServer.when(
                request().withMethod("GET")
                        .withPath("/serverbind"),
                Times.unlimited()
        ).respond(response().withStatusCode(200))

        // bind outgoing traffic to loopback. since the mockserver is running on localhost with a wildcard address, this should succeed.
        proxy = new BrowserMobProxyServer()
        proxy.start(0, null, InetAddress.getLoopbackAddress())

        NewProxyServerTestUtil.getNewHttpClient(proxy.getPort()).withCloseable {
            def response = it.execute(new HttpGet("http://127.0.0.1:${mockServerPort}/serverbind"))
            assertEquals(200, response.statusLine.statusCode)
        }
    }

    @Test
    void testServerBindAddressCannotConnect() {
        // bind outgoing traffic to loopback. since loopback cannot reach external addresses, this should fail.
        proxy = new BrowserMobProxyServer()
        proxy.start(0, null, InetAddress.getLoopbackAddress())

        NewProxyServerTestUtil.getNewHttpClient(proxy.getPort()).withCloseable {
            def response = it.execute(new HttpGet("http://www.google.com"))
            assertEquals("Expected a 502 Bad Gateway when connecting to an external address after binding to loopback", 502, response.statusLine.statusCode)
        }
    }
}
