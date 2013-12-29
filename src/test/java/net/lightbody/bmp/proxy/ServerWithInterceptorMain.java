package net.lightbody.bmp.proxy;

import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.proxy.http.BrowserMobHttpRequest;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.util.Log;

/**
 * Just a class with a main() method to start the server in embedded mode and a request interceptor.
 * Useful for putting in debug.
 *
 * @author v
 */
public class ServerWithInterceptorMain {

    public static void main(String... args) throws Exception {

        final Log LOG = new Log();

        final int PROXY_SERVER_PORT = 8888;

        ProxyServer proxyServer = new ProxyServer(PROXY_SERVER_PORT);
        proxyServer.start();

        proxyServer.addRequestInterceptor(new RequestInterceptor() {
            @Override
            public void process(BrowserMobHttpRequest request, Har har) {
                LOG.info("intercepted request is: " + request);
                LOG.info("request line: " + request.getProxyRequest().getRequestLine());
            }
        });
    }
}
