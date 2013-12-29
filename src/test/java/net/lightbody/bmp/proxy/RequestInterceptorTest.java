package net.lightbody.bmp.proxy;

import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.proxy.http.BrowserMobHttpRequest;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.util.Log;
import net.lightbody.bmp.proxy.util.TestSSLSocketFactory;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyStore;

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
    private HttpRequestBase requestMethod;

    enum Method {GET, POST}

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
    public void testInterceptGetOfUnknownHost() {
        setupApacheHttpClient(Method.GET, "unknown.host");
        executeClientRequest();

        LOG.info("getRequestLine: " + interceptedRequest.getProxyRequest().getRequestLine());

        assertTrue("The intercepted request was null.", interceptedRequest != null);
    }

    @Test
    public void testInterceptGetOfGoogleCaAllowAllHosts() {
        setupApacheHttpClient(Method.GET, "www.google.ca", true, true);
        executeClientRequest();

        LOG.info("getRequestLine: " + interceptedRequest.getProxyRequest().getRequestLine());

        assertTrue("The intercepted request was null.", interceptedRequest != null);
    }

    @Test
    public void testInterceptGetOfGoogleCaNoHostNameVerifier() {
        setupApacheHttpClient(Method.GET, "www.google.ca", true, false);
        executeClientRequest();

        //LOG.info("getRequestLine: " + interceptedRequest.getProxyRequest().getRequestLine());

        assertTrue("The intercepted request was null.", interceptedRequest != null);
    }


    private void setupApacheHttpClient(Method method, String host) {
        setupApacheHttpClient(method, host, false, false);
    }

    private void setupApacheHttpClient(Method method, String host, boolean useSsl, boolean trustAllHosts) {

        if (useSsl) {
            target = new HttpHost(host, 443, "HTTPS");
        } else {
            target = new HttpHost(host);
        }

        if (trustAllHosts) {
            client = createTrustingHttpClient();
        } else {
            client = new DefaultHttpClient();
        }

        switch (method) {
            case POST:
                requestMethod = new HttpPost("/");
                StringEntity entity = null;
                try {
                    entity = new StringEntity("blah");
                } catch (UnsupportedEncodingException e) {
                    LOG.info("Error occurred setting string entity of apache HttpPost: ", e);
                }
                ((HttpPost) requestMethod).setEntity(entity);
                break;
            case GET:
                requestMethod = new HttpGet("/");
        }

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

    // modified from ProxyServerTest
    private DefaultHttpClient createTrustingHttpClient() {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            SSLSocketFactory sf = new TestSSLSocketFactory(trustStore);
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpParams params = new BasicHttpParams();
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", sf, 443));

            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

            return new DefaultHttpClient(ccm, params);
        } catch (Exception e) {
            LOG.info("Exception setting ALLOW_ALL_HOSTNAME_VERIFIER", e);
            return new DefaultHttpClient();
        }
    }

}
