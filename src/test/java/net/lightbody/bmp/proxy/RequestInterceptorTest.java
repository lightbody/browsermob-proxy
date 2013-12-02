package net.lightbody.bmp.proxy;

import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.proxy.http.BrowserMobHttpRequest;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.util.Log;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * Test of ProxyServer's RequestInterceptor behavior.
 *
 * @author v
 */
public class RequestInterceptorTest {
    private static final Log LOG = new Log();

    private static final int PROXY_SERVER_PORT = 8888;
    private static ProxyServer proxyServer;
    private BrowserMobHttpRequest interceptedRequest;
    private DefaultHttpClient client;
    private HttpHost target;
    private HttpGet requestMethod;

    @BeforeClass
    public static void beforeClass() throws Exception {
        proxyServer = new ProxyServer(PROXY_SERVER_PORT);
        proxyServer.start();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        proxyServer.stop();
    }

    @Before
    public void setupRequestInterceptor() {
        proxyServer.addRequestInterceptor(new RequestInterceptor() {
            @Override
            public void process(BrowserMobHttpRequest request, Har har) {
                LOG.info("intercepted request is: " + request);
                interceptedRequest = request;
            }
        });
    }

    @Test
    public void testInterceptGetOfUknownHost() {
        setupApacheHttpClient();
        executeClientRequest();

        assertTrue("The intercepted request was null.", interceptedRequest != null);
    }


    private void setupApacheHttpClient() {
        target = new HttpHost("unknown.host");
        requestMethod = new HttpGet("/");
        client = new DefaultHttpClient();

        HttpHost pxy = new HttpHost("localhost", PROXY_SERVER_PORT);
        client.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, pxy);
    }

    private HttpResponse executeClientRequest() {
        HttpResponse response = null;
        try {
            response = client.execute(target, requestMethod);
        } catch (IOException e) {
            LOG.info("IOException occurred: ", e);
        }
        return response;
    }

}
