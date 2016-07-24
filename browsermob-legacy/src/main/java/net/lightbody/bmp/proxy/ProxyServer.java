package net.lightbody.bmp.proxy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarLog;
import net.lightbody.bmp.core.har.HarNameVersion;
import net.lightbody.bmp.core.har.HarPage;
import net.lightbody.bmp.core.util.ThreadUtils;
import net.lightbody.bmp.exception.JettyException;
import net.lightbody.bmp.exception.NameResolutionException;
import net.lightbody.bmp.filters.RequestFilter;
import net.lightbody.bmp.filters.ResponseFilter;
import net.lightbody.bmp.mitm.TrustSource;
import net.lightbody.bmp.proxy.auth.AuthType;
import net.lightbody.bmp.proxy.dns.AdvancedHostResolver;
import net.lightbody.bmp.proxy.http.BrowserMobHttpClient;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.http.ResponseInterceptor;
import net.lightbody.bmp.proxy.jetty.http.HttpContext;
import net.lightbody.bmp.proxy.jetty.http.HttpListener;
import net.lightbody.bmp.proxy.jetty.http.SocketListener;
import net.lightbody.bmp.proxy.jetty.jetty.Server;
import net.lightbody.bmp.proxy.jetty.util.InetAddrPort;
import net.lightbody.bmp.util.BrowserMobProxyUtil;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.java_bandwidthlimiter.BandwidthLimiter;
import org.java_bandwidthlimiter.StreamManager;
import org.littleshoot.proxy.HttpFiltersSource;
import org.littleshoot.proxy.MitmManager;
import org.openqa.selenium.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * The legacy, Jetty 5-based implementation of BrowserMobProxy. This class implements the {@link net.lightbody.bmp.proxy.LegacyProxyServer}
 * interface that defines the BMP 2.0 contact, as well as the 2.1+ {@link BrowserMobProxy} interface. <b>Important:</b> if
 * you are implementing new code, use the {@link BrowserMobProxy} interface. The
 * {@link net.lightbody.bmp.proxy.LegacyProxyServer} interface is deprecated and will be removed in a future release.
 * <h1>Unsupported operations</h1>
 * The following {@link BrowserMobProxy} operations are not supported and will be ignored:
 * <ul>
 *     <li>{@link BrowserMobProxy#getServerBindAddress()} and {@link #start(int, java.net.InetAddress, java.net.InetAddress)} - server bind addresses are not supported</li>
 *     <li>{@link BrowserMobProxy#stopAutoAuthorization(String)}</li>
 * </ul>
 *
 * @deprecated Use the {@link BrowserMobProxy} interface to preserve compatibility with future BrowserMob Proxy versions.
 */
@Deprecated
public class ProxyServer implements LegacyProxyServer, BrowserMobProxy {
    private static final HarNameVersion CREATOR = new HarNameVersion("BrowserMob Proxy", BrowserMobProxyUtil.getVersionString() + "-legacy");
    private static final Logger LOG = LoggerFactory.getLogger(ProxyServer.class);

    /**
     * System property to allow fallback to the native Java hostname lookup mechanism when dnsjava (xbill) cannot resolve the hostname. Native fallback
     * is enabled by default and will be disabled only if the value of this property is explicitly set to false.
     */
    public static final String ALLOW_NATIVE_DNS_FALLBACK = "bmp.allowNativeDnsFallback";

    /*
     * The Jetty HttpServer use in BrowserMobProxyHandler
     */
    private Server server;

    /*
     * Proxy port. Defaults to 0 (JVM-assigned).
     */
    private int port = 0;
    private InetAddress localHost;
    private BrowserMobHttpClient client;
    private StreamManager streamManager;
    private HarPage currentPage;
    private BrowserMobProxyHandler handler;
    private AtomicInteger pageCount = new AtomicInteger(1);
    private AtomicInteger requestCounter = new AtomicInteger(0);

    private boolean started;

    private InetSocketAddress chainedProxyAddress;

    public ProxyServer() {
    }

    public ProxyServer(int port) {
        this.port = port;
    }

    @Override
    public void start() {
        //create a stream manager that will be capped to 100 Megabits
        //remember that by default it is disabled!
        streamManager = new StreamManager( 100 * BandwidthLimiter.OneMbps );

        server = new Server();
        HttpListener listener = new SocketListener(new InetAddrPort(getLocalHost(), getPort()));
        server.addListener(listener);
        HttpContext context = new HttpContext();
        context.setContextPath("/");
        server.addContext(context);

        handler = new BrowserMobProxyHandler();
        handler.setJettyServer(server);
        handler.setShutdownLock(new Object());
        client = new BrowserMobHttpClient(streamManager, requestCounter);

        // if native DNS fallback is explicitly disabled, replace the default resolver with a dnsjava-only resolver
        if ("false".equalsIgnoreCase(System.getProperty(ALLOW_NATIVE_DNS_FALLBACK))) {
            client.setResolver(ClientUtil.createDnsJavaResolver());
        }

        client.prepareForBrowser();
        handler.setHttpClient(client);

        context.addHandler(handler);
       	try {
			server.start();
		} catch (Exception e) {
			// wrapping unhelpful Jetty Exception into a RuntimeException
			throw new JettyException("Exception occurred when starting the server", e);
		}

        setPort(listener.getPort());

        started = true;
    }

    @Override
    public void start(int port) {
        this.port = port;
        start();
    }

    @Override
    public void start(int port, InetAddress bindAddress) {
        setLocalHost(bindAddress);
        start(port);
    }

    @Override
    public void start(int port, InetAddress clientBindAddress, InetAddress serverBindAddress) {
        LOG.warn("The legacy ProxyServer implementation does not support a server bind address");
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public org.openqa.selenium.Proxy seleniumProxy() throws NameResolutionException {
        Proxy proxy = new Proxy();
        proxy.setProxyType(Proxy.ProxyType.MANUAL);
        InetAddress connectableLocalHost;
		try {
			connectableLocalHost = getConnectableLocalHost();
		} catch (UnknownHostException e) {
			// InetAddress cannot resolve a local host. since seleniumProxy() is designed to be called within a Selenium test,
			// this is most likely a fatal error that does not need to be a checked exception.
			throw new NameResolutionException("Error getting local host when creating seleniumProxy", e);
		}
        String proxyStr = String.format("%s:%d", connectableLocalHost.getCanonicalHostName(), getPort());
        proxy.setHttpProxy(proxyStr);
        proxy.setSslProxy(proxyStr);

        return proxy;
    }

    @Override
    public void cleanup() {
        if (handler != null) {
            handler.cleanup();
        }
    }

    @Override
    public void stop() {
        cleanup();
        if (client != null) {
            client.shutdown();
        }
        try {
            if (server != null) {
                server.stop();
            }
		} catch (InterruptedException e) {
			// the try/catch block in server.stop() is manufacturing a phantom InterruptedException, so this should not occur 
			throw new JettyException("Exception occurred when stopping the server", e);
		}
    }

    @Override
    public void abort() {
        stop();
    }

    @Override
    public InetAddress getClientBindAddress() {
        return getLocalHost();
    }

    public int getPort() {
        return port;
    }

    @Override
    public InetAddress getServerBindAddress() {
        LOG.warn("LegacyProxyServer does not support a server bind address");
        return null;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Get the the InetAddress that the Proxy server binds to when it starts.
     * 
     * If not otherwise set via {@link #setLocalHost(InetAddress)}, defaults to
     * 0.0.0.0 (i.e. bind to any interface).
     * 
     * Note - just because we bound to the address, doesn't mean that it can be
     * reached. E.g. trying to connect to 0.0.0.0 is going to fail. Use
     * {@link #getConnectableLocalHost()} if you're looking for a host that can be
     * connected to.
     */
    @Override
    public InetAddress getLocalHost() {
        if (localHost == null) {
        	try {
        		localHost = InetAddress.getByName("0.0.0.0");
        	} catch (UnknownHostException e) {
        		// InetAddress.getByName javadocs state: "If a literal IP address is supplied, only the validity of the address format is checked."
        		// Since the format of 0.0.0.0 is valid, getByName should never throw UnknownHostException
        		throw new RuntimeException("InetAddress.getByName failed to look up 0.0.0.0", e);
        	}
        }
        return localHost;
    }
    
    /**
     * Return a plausible {@link InetAddress} that other processes can use to
     * contact the proxy.
     * 
     * In essence, this is the same as {@link #getLocalHost()}, but avoids
     * returning 0.0.0.0. as no-one can connect to that. If no other host has
     * been set via {@link #setLocalHost(InetAddress)}, will return
     * {@link InetAddress#getLocalHost()}
     * 
     * No attempt is made to check the address for reachability before it is
     * returned.
     */
    @Override
    public InetAddress getConnectableLocalHost() throws UnknownHostException {
        
    	if (getLocalHost().equals(InetAddress.getByName("0.0.0.0"))) {
            return InetAddress.getLocalHost();
        } else {
            return getLocalHost();
        }
    }

    @Override
    public void setLocalHost(InetAddress localHost) {
        if (localHost.isAnyLocalAddress() ||
            localHost.isLoopbackAddress()) {
        	this.localHost = localHost;
        } else {
        	// address is not a local/loopback address, but might still be bound to a local network interface
        	NetworkInterface localInterface;
			try {
				localInterface = NetworkInterface.getByInetAddress(localHost);
			} catch (SocketException e) {
				throw new IllegalArgumentException("localHost address must be address of a local adapter (attempted to use: " + localHost + ")", e);
			}
        	if (localInterface != null) {
        		this.localHost = localHost;	
        	} else {
                throw new IllegalArgumentException("localHost address must be address of a local adapter (attempted to use: " + localHost + ")");
            }
        }
        
    }

    @Override
    public Har getHar() {
        // Wait up to 5 seconds for all active requests to cease before returning the HAR.
        // This helps with race conditions but won't cause deadlocks should a request hang
        // or error out in an unexpected way (which of course would be a bug!)
        boolean success = ThreadUtils.pollForCondition(new ThreadUtils.WaitCondition() {
            @Override
            public boolean checkCondition() {
                return requestCounter.get() == 0;
            }
        }, 5, TimeUnit.SECONDS);

        if (!success) {
            LOG.warn("Waited 5 seconds for requests to cease before returning HAR; giving up!");
        }

        return client.getHar();
    }

    @Override
    public Har newHar() {
        return newHar(null);
    }

    public Har newHar(String initialPageRef) {
        return newHar(initialPageRef, null);
    }

    @Override
    public Har newHar(String initialPageRef, String initialPageTitle) {
        pageCount.set(0); // this will be automatically incremented by newPage() below

        Har oldHar = getHar();

        Har har = new Har(new HarLog(CREATOR));
        client.setHar(har);
        newPage(initialPageRef, initialPageTitle);

        return oldHar;
    }

    @Override
    public void setHarCaptureTypes(Set<CaptureType> captureTypes) {
        setCaptureBinaryContent(captureTypes.contains(CaptureType.REQUEST_BINARY_CONTENT) || captureTypes.contains(CaptureType.RESPONSE_BINARY_CONTENT));
        setCaptureContent(captureTypes.contains(CaptureType.REQUEST_CONTENT) || captureTypes.contains(CaptureType.RESPONSE_CONTENT));
        setCaptureHeaders(captureTypes.contains(CaptureType.REQUEST_HEADERS) || captureTypes.contains(CaptureType.RESPONSE_HEADERS));
    }

    @Override
    public void setHarCaptureTypes(CaptureType... captureTypes) {
        if (captureTypes == null) {
            setHarCaptureTypes(EnumSet.noneOf(CaptureType.class));
        } else {
            setHarCaptureTypes(EnumSet.copyOf(Arrays.asList(captureTypes)));
        }
    }

    @Override
    public EnumSet<CaptureType> getHarCaptureTypes() {
        // cookie capture types are always enabled in the legacy ProxyServer
        EnumSet<CaptureType> captureTypes = CaptureType.getCookieCaptureTypes();

        if (client.isCaptureBinaryContent()) {
            captureTypes.addAll(CaptureType.getBinaryContentCaptureTypes());
        }

        if (client.isCaptureContent()) {
            captureTypes.addAll(CaptureType.getNonBinaryContentCaptureTypes());
        }

        if (client.isCaptureHeaders()) {
            captureTypes.addAll(CaptureType.getHeaderCaptureTypes());
        }

        return captureTypes;
    }

    @Override
    public void enableHarCaptureTypes(Set<CaptureType> captureTypes) {
        if (captureTypes.contains(CaptureType.REQUEST_BINARY_CONTENT) || captureTypes.contains(CaptureType.RESPONSE_BINARY_CONTENT)) {
            setCaptureBinaryContent(true);
        }

        if (captureTypes.contains(CaptureType.REQUEST_CONTENT) || captureTypes.contains(CaptureType.RESPONSE_CONTENT)) {
            setCaptureContent(true);
        }

        if (captureTypes.contains(CaptureType.REQUEST_HEADERS) || captureTypes.contains(CaptureType.RESPONSE_HEADERS)) {
            setCaptureHeaders(true);
        }
    }

    @Override
    public void enableHarCaptureTypes(CaptureType... captureTypes) {
        if (captureTypes == null) {
            enableHarCaptureTypes(EnumSet.noneOf(CaptureType.class));
        } else {
            enableHarCaptureTypes(EnumSet.copyOf(Arrays.asList(captureTypes)));
        }
    }

    @Override
    public void disableHarCaptureTypes(Set<CaptureType> captureTypes) {
        if (captureTypes.contains(CaptureType.REQUEST_BINARY_CONTENT) || captureTypes.contains(CaptureType.RESPONSE_BINARY_CONTENT)) {
            setCaptureBinaryContent(false);
        }

        if (captureTypes.contains(CaptureType.REQUEST_CONTENT) || captureTypes.contains(CaptureType.RESPONSE_CONTENT)) {
            setCaptureContent(false);
        }

        if (captureTypes.contains(CaptureType.REQUEST_HEADERS) || captureTypes.contains(CaptureType.RESPONSE_HEADERS)) {
            setCaptureHeaders(false);
        }
    }

    @Override
    public void disableHarCaptureTypes(CaptureType... captureTypes) {
        if (captureTypes == null) {
            disableHarCaptureTypes(EnumSet.noneOf(CaptureType.class));
        } else {
            disableHarCaptureTypes(EnumSet.copyOf(Arrays.asList(captureTypes)));
        }
    }

    @Override
    public Har newPage() {
        return newPage(null);
    }

    public Har newPage(String pageRef) {
        return newPage(pageRef, null);
    }

    @Override
    public Har newPage(String pageRef, String pageTitle) {
        if (pageRef == null) {
            pageRef = "Page " + pageCount.get();
        }

        if (pageTitle == null) {
            pageTitle = pageRef;
        }

        client.setHarPageRef(pageRef);
        currentPage = new HarPage(pageRef, pageTitle);
        client.getHar().getLog().addPage(currentPage);

        pageCount.incrementAndGet();

        return client.getHar();
    }

    @Override
    public Har endHar() {
        endPage();

        return getHar();
    }

    @Override
    public void setReadBandwidthLimit(long bytesPerSecond) {
        getStreamManager().setDownstreamKbps(bytesPerSecond / 1024L);
    }

    @Override
    public long getReadBandwidthLimit() {
        return getStreamManager().getMaxDownstreamKB() * 1024L;
    }

    @Override
    public void setWriteBandwidthLimit(long bytesPerSecond) {
        getStreamManager().setUpstreamKbps(bytesPerSecond / 1024L);
    }

    @Override
    public long getWriteBandwidthLimit() {
        return getStreamManager().getMaxUpstreamKB() * 1024L;
    }

    @Override
    public void setLatency(long latency, TimeUnit timeUnit) {
        getStreamManager().setLatency(TimeUnit.MILLISECONDS.convert(latency, timeUnit));
    }

    @Override
    public void setConnectTimeout(int connectionTimeout, TimeUnit timeUnit) {
        setConnectionTimeout((int) TimeUnit.MILLISECONDS.convert(connectionTimeout, timeUnit));
    }

    @Override
    public void setIdleConnectionTimeout(int idleConnectionTimeout, TimeUnit timeUnit) {
        setSocketOperationTimeout((int) TimeUnit.MILLISECONDS.convert(idleConnectionTimeout, timeUnit));
    }

    @Override
    public void setRequestTimeout(int requestTimeout, TimeUnit timeUnit) {
        setRequestTimeout((int) TimeUnit.MILLISECONDS.convert(requestTimeout, timeUnit));
    }

    @Override
    public void autoAuthorization(String domain, String username, String password, AuthType authType) {
        if (authType == AuthType.BASIC) {
            autoBasicAuthorization(domain, username, password);
        } else {
            throw new UnsupportedOperationException("Legacy ProxyServer implementation does not support non-BASIC authorization");
        }
    }

    @Override
    public void stopAutoAuthorization(String domain) {
        LOG.warn("Legacy ProxyServer implementation does not support stopping auto authorization");
    }

    @Override
    public void chainedProxyAuthorization(String username, String password, AuthType authType) {
        LOG.warn("Legacy ProxyServer implementation does not support chained proxy authorization");
    }

    public void endPage() {
        if (currentPage == null) {
            return;
        }

        currentPage.getPageTimings().setOnLoad(new Date().getTime() - currentPage.getStartedDateTime().getTime());
        client.setHarPageRef(null);
        currentPage = null;
    }

    @Override
    public void setRetryCount(int count) {
        client.setRetryCount(count);
    }

    @Override
    public void addHeaders(Map<String, String> headers) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            addHeader(entry.getKey(), entry.getValue());
        }
    }

    public void remapHost(String source, String target) {
        if (client.getResolver() instanceof AdvancedHostResolver) {
            AdvancedHostResolver advancedHostResolver = (AdvancedHostResolver) client.getResolver();
            advancedHostResolver.remapHost(source, target);
        } else {
            LOG.warn("Attempting to remap host, but host resolver is not an AdvancedHostRemapper. Host resolver is: {}", client.getResolver());
        }
    }

    @Override
    @Deprecated
    public void addRequestInterceptor(HttpRequestInterceptor i) {
        client.addRequestInterceptor(i);
    }

    @Override
    public void addRequestInterceptor(RequestInterceptor interceptor) {
        client.addRequestInterceptor(interceptor);
    }

    @Override
    @Deprecated
    public void addResponseInterceptor(HttpResponseInterceptor i) {
        client.addResponseInterceptor(i);
    }

    @Override
    public void addResponseInterceptor(ResponseInterceptor interceptor) {
        client.addResponseInterceptor(interceptor);
    }

    @Override
    public StreamManager getStreamManager() {
        return streamManager;
    }

    //use getStreamManager().setDownstreamKbps instead
    @Override
    @Deprecated
    public void setDownstreamKbps(long downstreamKbps) {
        streamManager.setDownstreamKbps(downstreamKbps);
        streamManager.enable();
    }

    //use getStreamManager().setUpstreamKbps instead
    @Override
    @Deprecated
    public void setUpstreamKbps(long upstreamKbps) {
        streamManager.setUpstreamKbps(upstreamKbps);
        streamManager.enable();
    }

    //use getStreamManager().setLatency instead
    @Override
    @Deprecated
    public void setLatency(long latency) {
        streamManager.setLatency(latency);
        streamManager.enable();
    }

    @Override
    public void setRequestTimeout(int requestTimeout) {
        client.setRequestTimeout(requestTimeout);
    }

    @Override
    public void setSocketOperationTimeout(int readTimeout) {
        client.setSocketOperationTimeout(readTimeout);
    }

    @Override
    public void setConnectionTimeout(int connectionTimeout) {
        client.setConnectionTimeout(connectionTimeout);
    }

    @Override
    public void autoBasicAuthorization(String domain, String username, String password) {
        client.autoBasicAuthorization(domain, username, password);
    }

    @Override
    public void rewriteUrl(String match, String replace) {
        client.rewriteUrl(match, replace);
    }

    @Override
    public void rewriteUrls(Map<String, String> rewriteRules) {
        for (Map.Entry<String, String> entry : rewriteRules.entrySet()) {
            rewriteUrl(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Map<String, String> getRewriteRules() {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

        for (RewriteRule rewriteRule : client.getRewriteRules()) {
            builder.put(rewriteRule.getPattern().pattern(), rewriteRule.getReplace());
        }

        return builder.build();
    }

    @Override
    public void removeRewriteRule(String urlPattern) {
        client.removeRewriteRule(urlPattern);
    }

    public void clearRewriteRules() {
    	client.clearRewriteRules();
    }

    @Override
    public void blacklistRequests(String pattern, int responseCode) {
    	client.blacklistRequests(pattern, responseCode, null);
    }
    
    @Override
    public void blacklistRequests(String pattern, int responseCode, String method) {
        client.blacklistRequests(pattern, responseCode, method);
    }

    @Override
    public void setBlacklist(Collection<BlacklistEntry> blacklist) {
        for (BlacklistEntry entry : blacklist) {
            if (entry.getHttpMethodPattern() == null) {
                blacklistRequests(entry.getUrlPattern().pattern(), entry.getStatusCode());
            } else {
                blacklistRequests(entry.getUrlPattern().pattern(), entry.getStatusCode(), entry.getHttpMethodPattern().pattern());
            }
        }
    }

    @Override
    public Collection<BlacklistEntry> getBlacklist() {
        return getBlacklistedUrls();
    }

    /**
     * @deprecated use getBlacklistedUrls()
     */
    @Override
    @Deprecated
    public List<BlacklistEntry> getBlacklistedRequests() {
        return client.getBlacklistedRequests();
    }
    
    @Override
    public Collection<BlacklistEntry> getBlacklistedUrls() {
    	return client.getBlacklistedUrls();
    }

	@Override
    public boolean isWhitelistEnabled() {
		return client.isWhitelistEnabled();
	}

	/**
	 * @deprecated use getWhitelistUrls()
	 */
	@Override
    @Deprecated
	public List<Pattern> getWhitelistRequests() {
		return client.getWhitelistRequests();
	}
	
    @Override
    public Collection<String> getWhitelistUrls() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (Pattern pattern : getWhitelistRequests()) {
            builder.add(pattern.pattern());
        }

        return builder.build();
    }

    @Override
    public int getWhitelistStatusCode() {
        return getWhitelistResponseCode();
    }

    public int getWhitelistResponseCode() {
		return client.getWhitelistResponseCode();
	}

    @Override
    public void clearBlacklist() {
    	client.clearBlacklist();
    }

    @Override
    public void whitelistRequests(Collection<String> urlPatterns, int statusCode) {
        whitelistRequests(urlPatterns.toArray(new String[urlPatterns.size()]), statusCode);
    }

    @Override
    public void addWhitelistPattern(String urlPattern) {
        List<String> whitelistUrls = new ArrayList<>(getWhitelistUrls());
        whitelistUrls.add(urlPattern);

        whitelistRequests(whitelistUrls, getWhitelistStatusCode());
    }

    /**
     * Whitelists the specified requests. 
     * <p>
     * <b>Note:</b> This method overwrites any existing whitelist.
     * 
     * @param patterns regular expression patterns matching URLs to whitelist
     * @param responseCode response code to return for non-whitelisted URLs
     */
    @Override
    public void whitelistRequests(String[] patterns, int responseCode) {
        client.whitelistRequests(patterns, responseCode);
    }
    
    /**
     * Enables an empty whitelist, which will return the specified responseCode for all requests.
     * 
     * @param responseCode HTTP response code to return for all requests
     */
    @Override
    public void enableEmptyWhitelist(int responseCode) {
    	client.whitelistRequests(new String[0], responseCode);
    }

    @Override
    public void disableWhitelist() {
        clearWhitelist();
    }

    public void clearWhitelist() {
    	client.clearWhitelist();
    }

    @Override
    public void addHeader(String name, String value) {
        client.addHeader(name, value);
    }

    @Override
    public void removeHeader(String name) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

        for (Map.Entry<String, String> entry : getAllHeaders().entrySet()) {
            if (!entry.getKey().equals(name)) {
                builder.put(entry.getKey(), entry.getValue());
            }
        }

        client.setAdditionalHeaders(builder.build());
    }

    @Override
    public void removeAllHeaders() {
        client.setAdditionalHeaders(Collections.<String, String>emptyMap());
    }

    @Override
    public Map<String, String> getAllHeaders() {
        return client.getAdditionalHeaders();
    }

    @Override
    public void setHostNameResolver(AdvancedHostResolver resolver) {
        client.setResolver(resolver);
    }

    @Override
    public AdvancedHostResolver getHostNameResolver() {
        return client.getResolver();
    }

    @Override
    public boolean waitForQuiescence(long quietPeriod, long timeout, TimeUnit timeUnit) {
        try {
            waitForNetworkTrafficToStop(TimeUnit.MILLISECONDS.convert(quietPeriod, timeUnit), TimeUnit.MILLISECONDS.convert(timeout, timeUnit));

            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    @Override
    public void setChainedProxy(InetSocketAddress chainedProxyAddress) {
        this.chainedProxyAddress = chainedProxyAddress;
        client.setHttpProxy(chainedProxyAddress.getHostString() + ":" + chainedProxyAddress.getPort());
    }

    @Override
    public InetSocketAddress getChainedProxy() {
        return this.chainedProxyAddress;
    }

    public void setCaptureHeaders(boolean captureHeaders) {
        client.setCaptureHeaders(captureHeaders);
    }

    @Override
    public void setCaptureContent(boolean captureContent) {
        client.setCaptureContent(captureContent);
    }
    
    @Override
    public void setCaptureBinaryContent(boolean captureBinaryContent) {
        client.setCaptureBinaryContent(captureBinaryContent);
    }

    @Override
    public void clearDNSCache() {
        if (client.getResolver() instanceof AdvancedHostResolver) {
            AdvancedHostResolver advancedHostResolver = (AdvancedHostResolver) client.getResolver();
            advancedHostResolver.clearDNSCache();
        } else {
            LOG.warn("Attempting to clear DNS cache, but host resolver is not an AdvancedHostRemapper. Host resolver is: {}", client.getResolver());
        }
    }

    @Override
    public void setDNSCacheTimeout(int timeout) {
        if (client.getResolver() instanceof AdvancedHostResolver) {
            AdvancedHostResolver advancedHostResolver = (AdvancedHostResolver) client.getResolver();
            advancedHostResolver.setNegativeDNSCacheTimeout(timeout, TimeUnit.MILLISECONDS);
            advancedHostResolver.setPositiveDNSCacheTimeout(timeout, TimeUnit.MILLISECONDS);
        } else {
            LOG.warn("Attempting to set DNS cache timeout, but host resolver is not an AdvancedHostRemapper. Host resolver is: {}", client.getResolver());
        }
    }

    @Override
    public void waitForNetworkTrafficToStop(final long quietPeriodInMs, long timeoutInMs) {
        boolean result = ThreadUtils.pollForCondition(new ThreadUtils.WaitCondition() {
            @Override
            public boolean checkCondition() {
                Date lastCompleted = null;
                Har har = client.getHar();
                if (har == null || har.getLog() == null) {
                    return true;
                }

                for (HarEntry entry : har.getLog().getEntries()) {
                    // if there is an active request, just stop looking
                    if (entry.getResponse().getStatus() < 0) {
                        return false;
                    }

                    Date end = new Date(entry.getStartedDateTime().getTime() + entry.getTime());
                    if (lastCompleted == null) {
                        lastCompleted = end;
                    } else if (end.after(lastCompleted)) {
                        lastCompleted = end;
                    }
                }

                return lastCompleted != null && System.currentTimeMillis() - lastCompleted.getTime() >= quietPeriodInMs;
            }
        }, timeoutInMs, TimeUnit.MILLISECONDS);

        if (!result) {
            throw new TimeoutException("Timed out after " + timeoutInMs + " ms while waiting for network traffic to stop");
        }
    }

    @Override
    public void setOptions(Map<String, String> options) {
        if (options.containsKey("httpProxy")) {
            client.setHttpProxy(options.get("httpProxy"));
        }
    }

    @Override
    public void addFirstHttpFilterFactory(HttpFiltersSource filterFactory) {
        LOG.warn("The legacy ProxyServer implementation does not support HTTP filter factories. Use addRequestInterceptor/addResponseInterceptor instead.");
    }

    @Override
    public void addLastHttpFilterFactory(HttpFiltersSource filterFactory) {
        LOG.warn("The legacy ProxyServer implementation does not support HTTP filter factories. Use addRequestInterceptor/addResponseInterceptor instead.");
    }

    @Override
    public void addResponseFilter(ResponseFilter filter) {
        LOG.warn("The legacy ProxyServer implementation does not support addRequestFilter and addResponseFilter. Use addRequestInterceptor/addResponseInterceptor instead.");
    }

    @Override
    public void addRequestFilter(RequestFilter filter) {
        LOG.warn("The legacy ProxyServer implementation does not support addRequestFilter and addResponseFilter. Use addRequestInterceptor/addResponseInterceptor instead.");
    }

    @Override
    public void setMitmDisabled(boolean mitmDisabled) {
        LOG.warn("The legacy ProxyServer implementation does not support disabling MITM.");
    }

    @Override
    public void setMitmManager(MitmManager mitmManager) {
        LOG.warn("The legacy ProxyServer implementation does not support custom MITM managers.");
    }

    @Override
    public void setTrustAllServers(boolean trustAllServers) {
        LOG.warn("The legacy ProxyServer implementation does not support the trustAllServers option.");
    }

    @Override
    public void setTrustSource(TrustSource trustSource) {
        LOG.warn("The legacy ProxyServer implementation does not support the setTrustSource option.");
    }

    public void cleanSslCertificates() {
        handler.cleanSslWithCyberVilliansCA();
    }

    /**
     * Exception thrown when waitForNetworkTrafficToStop does not successfully wait for network traffic to stop.
     */
    public static class TimeoutException extends RuntimeException {
        private static final long serialVersionUID = -7179322468198775663L;

        public TimeoutException(String message) {
            super(message);
        }
    }
}
