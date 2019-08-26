package net.lightbody.bmp.proxy;

import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.exception.NameResolutionException;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.http.ResponseInterceptor;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.java_bandwidthlimiter.StreamManager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Describes the legacy BrowserMob Proxy 2.0 interface. Clients <b>should not</b> implement or use this interface.
 * <p>
 * Use {@link BrowserMobProxy}.
 */
public interface LegacyProxyServer {
    void start();

    org.openqa.selenium.Proxy seleniumProxy() throws NameResolutionException;

    void cleanup();

    void stop();

    void abort();

    int getPort();

    void setPort(int port);

    InetAddress getLocalHost();

    InetAddress getConnectableLocalHost() throws UnknownHostException;

    void setLocalHost(InetAddress localHost);

    Har getHar();

    Har newHar(String initialPageRef);

    Har newHar(String initialPageRef, String initialPageTitle);

    Har newPage(String pageRef);

    Har newPage(String pageRef, String pageTitle);

    void endPage();

    void setRetryCount(int count);

    void remapHost(String source, String target);

    @Deprecated
    void addRequestInterceptor(HttpRequestInterceptor i);

    void addRequestInterceptor(RequestInterceptor interceptor);

    @Deprecated
    void addResponseInterceptor(HttpResponseInterceptor i);

    void addResponseInterceptor(ResponseInterceptor interceptor);

    StreamManager getStreamManager();

    //use getStreamManager().setDownstreamKbps instead
    @Deprecated
    void setDownstreamKbps(long downstreamKbps);

    //use getStreamManager().setUpstreamKbps instead
    @Deprecated
    void setUpstreamKbps(long upstreamKbps);

    //use getStreamManager().setLatency instead
    @Deprecated
    void setLatency(long latency);

    void setRequestTimeout(int requestTimeout);

    void setSocketOperationTimeout(int readTimeout);

    void setConnectionTimeout(int connectionTimeout);

    void autoBasicAuthorization(String domain, String username, String password);

    void rewriteUrl(String match, String replace);

    void clearRewriteRules();

    void blacklistRequests(String pattern, int responseCode);

    void blacklistRequests(String pattern, int responseCode, String method);

    @Deprecated
    List<BlacklistEntry> getBlacklistedRequests();

    Collection<BlacklistEntry> getBlacklistedUrls();

    boolean isWhitelistEnabled();

    @Deprecated
    List<Pattern> getWhitelistRequests();

    Collection<String> getWhitelistUrls();

    int getWhitelistResponseCode();

    void clearBlacklist();

    void whitelistRequests(String[] patterns, int responseCode);

    void enableEmptyWhitelist(int responseCode);

    void clearWhitelist();

    void addHeader(String name, String value);

    void headerFilterRegexp(String headerFilterRegexp);

    void setCaptureHeaders(boolean captureHeaders);

    void setCaptureContent(boolean captureContent);

    void setCaptureBinaryContent(boolean captureBinaryContent);

    void clearDNSCache();

    void setDNSCacheTimeout(int timeout);

    void waitForNetworkTrafficToStop(long quietPeriodInMs, long timeoutInMs);

    void setOptions(Map<String, String> options);
}
