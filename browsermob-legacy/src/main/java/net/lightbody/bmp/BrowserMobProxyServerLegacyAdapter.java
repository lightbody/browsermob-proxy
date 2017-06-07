package net.lightbody.bmp;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.exception.NameResolutionException;
import net.lightbody.bmp.proxy.BlacklistEntry;
import net.lightbody.bmp.proxy.CaptureType;
import net.lightbody.bmp.proxy.LegacyProxyServer;
import net.lightbody.bmp.proxy.auth.AuthType;
import net.lightbody.bmp.proxy.dns.AdvancedHostResolver;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.http.ResponseInterceptor;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.java_bandwidthlimiter.StreamManager;
import org.openqa.selenium.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Allows legacy code using the {@link LegacyProxyServer} interface to use the modern littleproxy-based implementation.
 *
 * @deprecated Use {@link BrowserMobProxyServer}. This adapter class will be removed in a future release.
 */
@Deprecated
public class BrowserMobProxyServerLegacyAdapter extends BrowserMobProxyServer implements LegacyProxyServer {
    private static final Logger log = LoggerFactory.getLogger(BrowserMobProxyServerLegacyAdapter.class);

    /**
     * When true, throw an UnsupportedOperationException instead of logging a warning when an operation is not supported by the LittleProxy-based
     * implementation of the BrowserMobProxy interface. Once all operations are implemented and the legacy interface is retired, this will be removed.
     */
    private volatile boolean errorOnUnsupportedOperation = false;

    /**
     * The port to start the proxy on, if set using {@link #setPort(int)}.
     */
    private volatile int port;

    /**
     * The address to listen on, if set using {@link #setLocalHost(InetAddress)}.
     */
    private volatile InetAddress clientBindAddress;

    /**
     * When true, this implementation of BrowserMobProxy will throw an UnsupportedOperationException when a method is not supported. This
     * helps identify functionality that is not supported by the LittleProxy-based implementation. By default, this implementation will
     * log a warning rather than throw an UnsupportedOperationException.
     *
     * @param errorOnUnsupportedOperation when true, throws an exception when an operation is not supported
     */
    public void setErrorOnUnsupportedOperation(boolean errorOnUnsupportedOperation) {
        this.errorOnUnsupportedOperation = errorOnUnsupportedOperation;
    }

    /**
     * @deprecated use {@link net.lightbody.bmp.client.ClientUtil#createSeleniumProxy(BrowserMobProxy)}
     */
    @Override
    @Deprecated
    public Proxy seleniumProxy() throws NameResolutionException {
        return ClientUtil.createSeleniumProxy(this);
    }

    /**
     * @deprecated specify the port using {@link #start(int)} or other start() methods with port parameters
     */
    @Override
    @Deprecated
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @deprecated use {@link #getClientBindAddress()}
     */
    @Override
    @Deprecated
    public InetAddress getLocalHost() {
        return this.getClientBindAddress();
    }

    /**
     * @deprecated use {@link net.lightbody.bmp.client.ClientUtil#getConnectableAddress()}
     */
    @Override
    @Deprecated
    public InetAddress getConnectableLocalHost() throws UnknownHostException {
        return ClientUtil.getConnectableAddress();
    }

    /**
     * @deprecated use {@link #start(int, java.net.InetAddress)} or {@link #start(int, java.net.InetAddress, java.net.InetAddress)}
     */
    @Override
    @Deprecated
    public void setLocalHost(InetAddress localHost) {
        this.clientBindAddress = localHost;
    }

    @Override
    public void start() {
        super.start(port, clientBindAddress);
    }

    /**
     * Adapter to enable clients to switch to a LittleProxy implementation of BrowserMobProxy but maintain compatibility with
     * the 2.0.0 interface.
     */
    @Deprecated
    private class StreamManagerLegacyAdapter extends StreamManager {
        private StreamManagerLegacyAdapter() {
            super(0);
        }

        @Override
        public void setDownstreamKbps(long downstreamKbps) {
            BrowserMobProxyServerLegacyAdapter.this.setDownstreamKbps(downstreamKbps);
        }

        @Override
        public void setUpstreamKbps(long upstreamKbps) {
            BrowserMobProxyServerLegacyAdapter.this.setUpstreamKbps(upstreamKbps);
        }

        @Override
        public void setLatency(long latency) {
            BrowserMobProxyServerLegacyAdapter.this.setLatency(latency);
        }

        @Override
        public void setDownstreamMaxKB(long downstreamMaxKB) {
            BrowserMobProxyServerLegacyAdapter.this.setWriteLimitKbps(downstreamMaxKB);
        }

        @Override
        public void setUpstreamMaxKB(long upstreamMaxKB) {
            BrowserMobProxyServerLegacyAdapter.this.setReadLimitKbps(upstreamMaxKB);
        }

        @Override
        public long getMaxUpstreamKB() {
            return BrowserMobProxyServerLegacyAdapter.this.getReadBandwidthLimit() / 1024L;
        }

        /**
         * @deprecated This method is no longer supported and does not return correct values.
         */
        @Override
        public long getRemainingUpstreamKB() {
            return super.getRemainingUpstreamKB();
        }

        @Override
        public long getMaxDownstreamKB() {
            return BrowserMobProxyServerLegacyAdapter.this.getWriteBandwidthLimit() / 1024L;
        }

        /**
         * @deprecated This method is no longer supported and does not return correct values.
         */
        @Override
        public long getRemainingDownstreamKB() {
            return super.getRemainingDownstreamKB();
        }
    }

    @Override
    public void setRetryCount(int count) {
        if (errorOnUnsupportedOperation) {
            throw new UnsupportedOperationException("No LittleProxy implementation for operation: " + new Throwable().getStackTrace()[0].getMethodName());
        } else {
            log.warn("No LittleProxy implementation for operation: " + new Throwable().getStackTrace()[0].getMethodName());
        }
    }

    /**
     * StreamManagerLegacyAdapter bound to this BrowserMob Proxy instance, to route the bandwidth control calls to the appropriate
     * LittleProxy-compatible methods.
     */
    private final StreamManagerLegacyAdapter streamManagerAdapter = new StreamManagerLegacyAdapter();

    /**
     * This method returns a "fake" StreamManager whose setter methods will wrap LittleProxy-compatible bandwidth control methods. No other methods
     * in the returned StreamManager should be used; they will have no effect.
     *
     * @return fake StreamManager object that wraps LitteProxy-compatible bandwidth control methods
     * @deprecated use bandwidth control methods from the {@link BrowserMobProxy}
     */
    @Deprecated
    public StreamManager getStreamManager() {
        return streamManagerAdapter;
    }

    /**
     * @deprecated use {@link #setWriteBandwidthLimit(long)}
     */
    @Deprecated
    public void setDownstreamKbps(long downstreamKbps) {
        this.setWriteBandwidthLimit(downstreamKbps * 1024);
    }

    /**
     * @deprecated use {@link #setReadBandwidthLimit(long)}
     */
    @Deprecated
    public void setUpstreamKbps(long upstreamKbps) {
        this.setReadBandwidthLimit(upstreamKbps * 1024);
    }

    /**
     * @deprecated use {@link #setLatency(long, java.util.concurrent.TimeUnit)}
     */
    @Deprecated
    public void setLatency(long latencyMs) {
        setLatency(latencyMs, TimeUnit.MILLISECONDS);
    }

    /**
     * @deprecated use {@link #setReadBandwidthLimit(long)}
     */
    @Deprecated
    public void setReadLimitKbps(long readLimitKbps) {
        setReadBandwidthLimit(readLimitKbps * 1024);
    }

    /**
     * @deprecated use {@link #setWriteBandwidthLimit(long)}
     */
    @Deprecated
    public void setWriteLimitKbps(long writeLimitKbps) {
        setWriteBandwidthLimit(writeLimitKbps * 1024);
    }

    /**
     * @deprecated
     */
    @Override
    @Deprecated
    public void addRequestInterceptor(HttpRequestInterceptor i) {
        if (errorOnUnsupportedOperation) {
            throw new UnsupportedOperationException("No LittleProxy implementation for operation: " + new Throwable().getStackTrace()[0].getMethodName());
        } else {
            log.warn("No LittleProxy implementation for operation: " + new Throwable().getStackTrace()[0].getMethodName());
        }
    }

    /**
     * @deprecated
     */
    @Override
    @Deprecated
    public void addRequestInterceptor(RequestInterceptor interceptor) {
        if (errorOnUnsupportedOperation) {
            throw new UnsupportedOperationException("No LittleProxy implementation for operation: " + new Throwable().getStackTrace()[0].getMethodName());
        } else {
            log.warn("No LittleProxy implementation for operation: " + new Throwable().getStackTrace()[0].getMethodName());
        }
    }

    /**
     * @deprecated
     */
    @Override
    @Deprecated
    public void addResponseInterceptor(HttpResponseInterceptor i) {
        if (errorOnUnsupportedOperation) {
            throw new UnsupportedOperationException("No LittleProxy implementation for operation: " + new Throwable().getStackTrace()[0].getMethodName());
        } else {
            log.warn("No LittleProxy implementation for operation: " + new Throwable().getStackTrace()[0].getMethodName());
        }
    }

    /**
     * @deprecated
     */
    @Override
    @Deprecated
    public void addResponseInterceptor(ResponseInterceptor interceptor) {
        if (errorOnUnsupportedOperation) {
            throw new UnsupportedOperationException("No LittleProxy implementation for operation: " + new Throwable().getStackTrace()[0].getMethodName());
        } else {
            log.warn("No LittleProxy implementation for operation: " + new Throwable().getStackTrace()[0].getMethodName());
        }
    }

    /**
     * @deprecated use getBlacklist()
     */
    @Deprecated
    public List<BlacklistEntry> getBlacklistedRequests() {
        return ImmutableList.copyOf(getBlacklist());
    }

    /**
     * @deprecated use {@link #getBlacklist()}
     */
    @Override
    @Deprecated
    public Collection<BlacklistEntry> getBlacklistedUrls() {
        return getBlacklist();
    }

    /**
     * @deprecated use {@link #getWhitelistStatusCode()}
     */
    @Override
    @Deprecated
    public int getWhitelistResponseCode() {
        return getWhitelistStatusCode();
    }

    /**
     * @deprecated use {@link #disableWhitelist()}
     */
    @Override
    @Deprecated
    public void clearWhitelist() {
        this.disableWhitelist();
    }

    /**
     * @deprecated use {@link #getWhitelistUrls()}
     */
    @Deprecated
    public List<Pattern> getWhitelistRequests() {
        ImmutableList.Builder<Pattern> builder = ImmutableList.builder();

        for (String patternString : getWhitelistUrls()) {
            Pattern pattern = Pattern.compile(patternString);
            builder.add(pattern);
        }

        return builder.build();
    }

    /**
     * @deprecated use {@link #waitForQuiescence(long, long, java.util.concurrent.TimeUnit)}
     */
    @Override
    @Deprecated
    public void waitForNetworkTrafficToStop(long quietPeriodInMs, long timeoutInMs) {
        waitForQuiescence(quietPeriodInMs, timeoutInMs, TimeUnit.MILLISECONDS);
    }

    /**
     * @deprecated use {@link #getHarCaptureTypes()} to check for relevant {@link net.lightbody.bmp.proxy.CaptureType}
     */
    @Deprecated
    public boolean isCaptureHeaders() {
        return getHarCaptureTypes().containsAll(CaptureType.getHeaderCaptureTypes());
    }

    /**
     * @deprecated use {@link #setHarCaptureTypes(java.util.Set)} to set the appropriate {@link net.lightbody.bmp.proxy.CaptureType}
     */
    @Deprecated
    public void setCaptureHeaders(boolean captureHeaders) {
        if (captureHeaders) {
            enableHarCaptureTypes(CaptureType.getHeaderCaptureTypes());
        } else {
            disableHarCaptureTypes(CaptureType.getHeaderCaptureTypes());
        }
    }

    /**
     * @deprecated use {@link #getHarCaptureTypes()} to check for relevant {@link net.lightbody.bmp.proxy.CaptureType}
     */
    @Deprecated
    public boolean isCaptureContent() {
        return getHarCaptureTypes().containsAll(CaptureType.getNonBinaryContentCaptureTypes());
    }

    /**
     * @deprecated use {@link #setHarCaptureTypes(java.util.Set)} to set the appropriate {@link net.lightbody.bmp.proxy.CaptureType}
     */
    @Deprecated
    public void setCaptureContent(boolean captureContent) {
        if (captureContent) {
            enableHarCaptureTypes(CaptureType.getAllContentCaptureTypes());
        } else {
            disableHarCaptureTypes(CaptureType.getAllContentCaptureTypes());
        }
    }

    /**
     * @deprecated use {@link #getHarCaptureTypes()} to check for relevant {@link net.lightbody.bmp.proxy.CaptureType}
     */
    @Deprecated
    public boolean isCaptureBinaryContent() {
        return getHarCaptureTypes().containsAll(CaptureType.getBinaryContentCaptureTypes());
    }

    /**
     * @deprecated use {@link #setHarCaptureTypes(java.util.Set)} to set the appropriate {@link net.lightbody.bmp.proxy.CaptureType}
     */
    @Deprecated
    public void setCaptureBinaryContent(boolean captureBinaryContent) {
        if (captureBinaryContent) {
            enableHarCaptureTypes(CaptureType.getBinaryContentCaptureTypes());
        } else {
            disableHarCaptureTypes(CaptureType.getBinaryContentCaptureTypes());
        }
    }

    /**
     * @deprecated this method has no effect and will be removed from a future version
     */
    @Deprecated
    public void cleanup() {
    }

    /**
     * @deprecated Remap hosts directly using {@link net.lightbody.bmp.proxy.dns.AdvancedHostResolver#remapHost(String, String)}.
     * See {@link #getHostNameResolver()} and the default implementation in {@link net.lightbody.bmp.proxy.dns.ChainedHostResolver}.
     */
    @Override
    @Deprecated
    public void remapHost(String source, String target) {
        getHostNameResolver().remapHost(source, target);
    }

    /**
     * @deprecated Manipulate the DNS cache directly using {@link net.lightbody.bmp.proxy.dns.AdvancedHostResolver#clearDNSCache()}.
     * See {@link #getHostNameResolver()} and the default implementation in {@link net.lightbody.bmp.proxy.dns.ChainedHostResolver}.
     */
    @Override
    @Deprecated
    public void clearDNSCache() {
        getHostNameResolver().clearDNSCache();
    }

    /**
     * @deprecated Manipulate the DNS cache directly using {@link net.lightbody.bmp.proxy.dns.AdvancedHostResolver#setNegativeDNSCacheTimeout(int, java.util.concurrent.TimeUnit)}
     * and {@link net.lightbody.bmp.proxy.dns.AdvancedHostResolver#setPositiveDNSCacheTimeout(int, java.util.concurrent.TimeUnit)}.
     * See {@link #getHostNameResolver()} and the default implementation in {@link net.lightbody.bmp.proxy.dns.ChainedHostResolver}.
     */
    @Override
    @Deprecated
    public void setDNSCacheTimeout(int timeout) {
        AdvancedHostResolver resolver = getHostNameResolver();
        resolver.setPositiveDNSCacheTimeout(timeout, TimeUnit.SECONDS);
        resolver.setNegativeDNSCacheTimeout(timeout, TimeUnit.SECONDS);
    }


    /**
     * @deprecated use {@link #setConnectTimeout(int, java.util.concurrent.TimeUnit)}
     */
    @Deprecated
    @Override
    public void setConnectionTimeout(int connectionTimeoutMs) {
        setConnectTimeout(connectionTimeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * @deprecated use {@link #setIdleConnectionTimeout(int, java.util.concurrent.TimeUnit)}
     */
    @Deprecated
    @Override
    public void setSocketOperationTimeout(int readTimeoutMs) {
        setIdleConnectionTimeout(readTimeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * @deprecated use {@link #setRequestTimeout(int, java.util.concurrent.TimeUnit)}
     */
    @Deprecated
    @Override
    public void setRequestTimeout(int requestTimeoutMs) {
        setRequestTimeout(requestTimeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * @deprecated use {@link #autoAuthorization(String, String, String, net.lightbody.bmp.proxy.auth.AuthType)}
     */
    @Deprecated
    @Override
    public void autoBasicAuthorization(String domain, String username, String password) {
        autoAuthorization(domain, username, password, AuthType.BASIC);
    }

    /**
     * @deprecated use {@link #setChainedProxy(java.net.InetSocketAddress)} to set an upstream proxy
     */
    @Deprecated
    public void setOptions(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return;
        }

        String httpProxy = options.get("httpProxy");
        if (httpProxy != null) {
            log.warn("Chained proxy support through setOptions is deprecated. Use setUpstreamProxy() to enable chained proxy support.");

            HostAndPort hostAndPort = HostAndPort.fromString(httpProxy);
            this.setChainedProxy(new InetSocketAddress(hostAndPort.getHost(), hostAndPort.getPortOrDefault(80)));
        } else {
            if (errorOnUnsupportedOperation) {
                throw new UnsupportedOperationException("The LittleProxy-based implementation of BrowserMob Proxy does not support the setOptions method. Use the methods defined in the BrowserMobProxy interface to set connection parameters.");
            } else {
                log.warn("The LittleProxy-based implementation of BrowserMob Proxy does not support the setOptions method. Use the methods defined in the BrowserMobProxy interface to set connection parameters.");
            }
        }
    }
}
