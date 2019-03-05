package net.lightbody.bmp.proxy.test.util;

import org.apache.http.HttpHost;
import org.apache.http.client.CookieStore;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class NewProxyServerTestUtil {
    /**
     * Creates an all-trusting CloseableHttpClient (for tests ONLY!) that will connect to a proxy at 127.0.0.1:proxyPort,
     * with no cookie store.
     *
     * @param proxyPort port of the proxy running at 127.0.0.1
     * @return a new CloseableHttpClient
     */
    public static CloseableHttpClient getNewHttpClient(int proxyPort) {
        return getNewHttpClient(proxyPort, null);
    }

    /**
     * Creates an all-trusting CloseableHttpClient (for tests ONLY!) that will connect to a proxy at 127.0.0.1:proxyPort,
     * using the specified cookie store.
     *
     * @param proxyPort   port of the proxy running at 127.0.0.1
     * @param cookieStore CookieStore for HTTP cookies
     * @return a new CloseableHttpClient
     */
    public static CloseableHttpClient getNewHttpClient(int proxyPort, CookieStore cookieStore) {
        try {
            // Trust all certs -- under no circumstances should this ever be used outside of testing
            SSLContext sslcontext = org.apache.http.ssl.SSLContexts.custom()
                    .loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true)
                    .build();

            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                    sslcontext, NoopHostnameVerifier.INSTANCE);

            CloseableHttpClient httpclient = HttpClients.custom()
                    .setSSLSocketFactory(sslsf)
                    .setDefaultCookieStore(cookieStore)
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

    /**
     * Reads and closes the input stream, converting it to a String using the UTF-8 charset. The input stream is guaranteed to be closed, even
     * if the reading/conversion throws an exception.
     *
     * @param in UTF-8-encoded InputStream to read
     * @return String of InputStream's contents
     * @throws IOException if an error occurs reading from the stream
     */
    public static String toStringAndClose(InputStream in) throws IOException {
        try {
            return org.apache.commons.io.IOUtils.toString(in, StandardCharsets.UTF_8);
        } finally {
            org.apache.commons.io.IOUtils.closeQuietly(in);
        }
    }

    /**
     * Checks if the test is running on a Windows OS.
     *
     * @return true if running on Windows, otherwise false
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }
}
