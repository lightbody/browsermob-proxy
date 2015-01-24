package net.lightbody.bmp.proxy;

import net.lightbody.bmp.proxy.util.TestSSLSocketFactory;

import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

public abstract class ProxyServerTest {
    static {
        Main.configureJdkLogging();
    }

    protected ProxyServer proxy = new ProxyServer(8081);
    protected DefaultHttpClient client = getNewHttpClient();

    @Before
    public void startServer() throws Exception {
        proxy.start();
    }

    public static DefaultHttpClient getNewHttpClient() {
    	return getNewHttpClient(8081);
    }
    
    public static DefaultHttpClient getNewHttpClient(int proxyPort) {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            SSLSocketFactory sf = new TestSSLSocketFactory(trustStore);
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpParams params = new BasicHttpParams();
            params.setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost("127.0.0.1", proxyPort, "http"));
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", sf, 443));

            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

            return new DefaultHttpClient(ccm, params);
        } catch (Exception e) {
            throw new RuntimeException("Unable to get HTTP client", e);
        }
    }

	@Test
	public void testHttpProxyOption() throws Exception {
		ProxyServer instance = new ProxyServer(8082);
		Map<String, String> options = new HashMap<String, String>();
		options.put("httpProxy", "localhost:8080");
		instance.setOptions(options);
		instance.start();
		try {
			DefaultHttpClient client = getNewHttpClient(8082);
			try {
				client.execute(new HttpGet("https://www.google.com"));
			} finally {
				client.close();
			}
		} finally {
			instance.stop();
		}
	}

    @After
    public void stopServer() throws Exception {
        proxy.stop();
    }
}
