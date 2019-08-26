package net.lightbody.bmp.proxy.http;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarCookie;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarNameValuePair;
import net.lightbody.bmp.core.har.HarPostData;
import net.lightbody.bmp.core.har.HarPostDataParam;
import net.lightbody.bmp.core.har.HarRequest;
import net.lightbody.bmp.core.har.HarResponse;
import net.lightbody.bmp.proxy.BlacklistEntry;
import net.lightbody.bmp.proxy.RewriteRule;
import net.lightbody.bmp.proxy.Whitelist;
import net.lightbody.bmp.proxy.dns.AdvancedHostResolver;
import net.lightbody.bmp.proxy.jetty.util.MultiMap;
import net.lightbody.bmp.proxy.jetty.util.UrlEncoded;
import net.lightbody.bmp.proxy.util.CappedByteArrayOutputStream;
import net.lightbody.bmp.proxy.util.ClonedOutputStream;
import net.lightbody.bmp.proxy.util.IOUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.BestMatchSpecFactory;
import org.apache.http.impl.cookie.BrowserCompatSpec;
import org.apache.http.impl.cookie.BrowserCompatSpecFactory;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.java_bandwidthlimiter.StreamManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * WARN : Require zlib > 1.1.4 (deflate support)
 */
public class BrowserMobHttpClient {
    // No longer getting the version from Main.getVersion().
    private static final String VERSION = "2.1";

	private static final Logger LOG = LoggerFactory.getLogger(BrowserMobHttpClient.class);

    private static final int BUFFER = 4096;

    private volatile Har har;
    private volatile String harPageRef;

    /**
     * keep headers
     */
    private volatile boolean captureHeaders;

    /**
     * keep contents
     */
    private volatile boolean captureContent;

    /**
     * keep binary contents (if captureContent is set to true, default policy is to capture binary contents too)
     */
    private volatile boolean captureBinaryContent = true;

    /**
     * socket factory dedicated to port 80 (HTTP)
     */
    private final SimulatedSocketFactory socketFactory;

    /**
     * socket factory dedicated to port 443 (HTTPS)
     */
    private final TrustingSSLSocketFactory sslSocketFactory;


    private final PoolingHttpClientConnectionManager httpClientConnMgr;

    /**
     * Builders for httpClient
     * Each time you change their configuration you should call updateHttpClient()
     */
	private final Builder requestConfigBuilder;
    private final HttpClientBuilder httpClientBuilder;

    /**
     * The current httpClient which will execute HTTP requests
     */
    private volatile CloseableHttpClient httpClient;

    private final BasicCookieStore cookieStore = new BasicCookieStore();

    /**
     * List of rejected URL patterns
     */
    private final Collection<BlacklistEntry> blacklistEntries = new CopyOnWriteArrayList<BlacklistEntry>();

    /**
     * List of accepted URL patterns
     */
    private volatile Whitelist whitelist = Whitelist.WHITELIST_DISABLED;

    /**
     * List of URLs to rewrite
     */
    private final CopyOnWriteArrayList<RewriteRule> rewriteRules = new CopyOnWriteArrayList<RewriteRule>();

    /**
     * triggers to process when sending request
     */
    private final List<RequestInterceptor> requestInterceptors = new CopyOnWriteArrayList<RequestInterceptor>();

    /**
     * triggers to process when receiving response
     */
    private final List<ResponseInterceptor> responseInterceptors = new CopyOnWriteArrayList<ResponseInterceptor>();

    /**
     * additional headers sent with request
     */
    private final Map<String, String> additionalHeaders = new ConcurrentHashMap<String, String>();

    /**
     * Regexp to check request url and inject headers if url match regexp.
     */
    private static String  headersFilterRegexp;

    /**
     * request timeout: set to -1 to disable timeout
     */
    private volatile int requestTimeout = -1;
    
    /**
     * is it possible to add a new request?
     */
    private final AtomicBoolean allowNewRequests = new AtomicBoolean(true);

    /**
     * Hostname resolver that wraps a {@link net.lightbody.bmp.proxy.dns.HostResolver}. The wrapped HostResolver can be replaced safely at
     * runtime using {@link LegacyHostResolverAdapter#setResolver(net.lightbody.bmp.proxy.dns.AdvancedHostResolver)}.
     * See {@link #setResolver(net.lightbody.bmp.proxy.dns.AdvancedHostResolver)}.
     */
    private final LegacyHostResolverAdapter resolverWrapper = new LegacyHostResolverAdapter(ClientUtil.createDnsJavaWithNativeFallbackResolver());

    /**
     * does the proxy support gzip compression? (set to false if you go through a browser)
     */
    private boolean decompress = true;

    /**
     * set of active requests
     */
    private final Set<ActiveRequest> activeRequests = Collections.newSetFromMap(new ConcurrentHashMap<ActiveRequest, Boolean>());

    /**
     * credentials used for authentication
     */
    private WildcardMatchingCredentialsProvider credsProvider;

    /**
     * is the client shutdown?
     */
    private volatile boolean shutdown = false;

    /**
     * authentication type used
     */
    private AuthType authType;

    /**
     * does the proxy follow redirects? (set to false if you go through a browser)
     */
    private boolean followRedirects = true;

    /**
     * maximum redirects supported by the proxy
     */
    private static final int MAX_REDIRECT = 10;

    /**
     * remaining requests counter
     */
    private final AtomicInteger requestCounter;

    /**
     * Init HTTP client
     * @param streamManager will be capped to 100 Megabits (by default it is disabled)
     * @param requestCounter indicates the number of remaining requests
     */
    public BrowserMobHttpClient(final StreamManager streamManager, AtomicInteger requestCounter) {
        this.requestCounter = requestCounter;
        socketFactory = new SimulatedSocketFactory(streamManager);
        sslSocketFactory = new TrustingSSLSocketFactory(new AllowAllHostnameVerifier(), streamManager);

        requestConfigBuilder = RequestConfig.custom()
    		.setConnectionRequestTimeout(60000)
    		.setConnectTimeout(2000)
    		.setSocketTimeout(60000);

        // we associate each SocketFactory with their protocols
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
        	.register("http", this.socketFactory)
        	.register("https", this.sslSocketFactory)
        	.build();

        httpClientConnMgr = new PoolingHttpClientConnectionManager(registry, resolverWrapper) {
            @Override
            public ConnectionRequest requestConnection(HttpRoute route, Object state) {
                final ConnectionRequest wrapped = super.requestConnection(route, state);
                return new ConnectionRequest() {
                    @Override
                    public HttpClientConnection get(long timeout, TimeUnit tunit) throws InterruptedException, ExecutionException, ConnectionPoolTimeoutException {
                        long start = System.nanoTime();
                        try {
                            return wrapped.get(timeout, tunit);
                        } finally {
                            RequestInfo.get().blocked(start, System.nanoTime());
                        }
                    }

					@Override
					public boolean cancel() {
						return wrapped.cancel();
					}
                };
            }
        };

        // we set high limits for request parallelism to let the browser set is own limit 
        httpClientConnMgr.setMaxTotal(600);
        httpClientConnMgr.setDefaultMaxPerRoute(300);
        credsProvider = new WildcardMatchingCredentialsProvider();
        httpClientBuilder = getDefaultHttpClientBuilder(streamManager);
        httpClient = httpClientBuilder.build();

        HttpClientInterrupter.watch(this);
    }

	private HttpClientBuilder getDefaultHttpClientBuilder(final StreamManager streamManager) {
		assert requestConfigBuilder != null;
		return HttpClientBuilder.create()
        	.setConnectionManager(httpClientConnMgr)
        	.setRequestExecutor(new HttpRequestExecutor() {
        		@Override
                protected HttpResponse doSendRequest(HttpRequest request, HttpClientConnection conn, HttpContext context) throws IOException, HttpException {
                    long start = System.nanoTime();

                    // send request
                    HttpResponse response = super.doSendRequest(request, conn, context);

                    // set "sending" for resource
                    RequestInfo.get().send(start, System.nanoTime());
                    return response;
                }

                @Override
                protected HttpResponse doReceiveResponse(HttpRequest request, HttpClientConnection conn, HttpContext context) throws HttpException, IOException {
                    long start = System.nanoTime();
                    HttpResponse response = super.doReceiveResponse(request, conn, context);

                    // +4 => header/data separation
                    long responseHeadersSize = response.getStatusLine().toString().length() + 4;
					for (Header header : response.getAllHeaders()) {
						// +2 => new line
						responseHeadersSize += header.toString().length() + 2;
					}
					// set current entry response
                    HarEntry entry = RequestInfo.get().getEntry();
                    if (entry != null) {
						entry.getResponse().setHeadersSize(responseHeadersSize);
					}
                    if (streamManager.getLatency() > 0) {
                        // retrieve real latency discovered in connect SimulatedSocket
                        long realLatency = RequestInfo.get().getLatency(TimeUnit.MILLISECONDS);
                        // add latency
                        if (realLatency < streamManager.getLatency()) {
                            try {
                                Thread.sleep(streamManager.getLatency() - realLatency);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                    // set waiting time
                    RequestInfo.get().wait(start, System.nanoTime());

                    return response;
                }
	        })
	        .setDefaultRequestConfig(requestConfigBuilder.build())
	        .setDefaultCredentialsProvider(credsProvider)
	        .setDefaultCookieStore(cookieStore)
	        .addInterceptorLast(new PreemptiveAuth())
	        .setRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
	        // we set an empty httpProcessorBuilder to remove the automatic compression management
	        .setHttpProcessor(HttpProcessorBuilder.create().build())
	        // we always set this to false so it can be handled manually:
	        .disableRedirectHandling();
	}

    public void setRetryCount(int count) {
    	httpClientBuilder.setRetryHandler(new DefaultHttpRequestRetryHandler(count, false));
    	updateHttpClient();
    }

    public void remapHost(String source, String target) {
        if (resolverWrapper.getResolver() instanceof AdvancedHostResolver) {
            AdvancedHostResolver advancedHostResolver = (AdvancedHostResolver) resolverWrapper.getResolver();
            advancedHostResolver.remapHost(source, target);
        } else {
            LOG.warn("Attempting to remap host, but resolver is not an AdvancedHostResolver. Resolver: {}", resolverWrapper.getResolver());
        }
    }

    @Deprecated
    public void addRequestInterceptor(HttpRequestInterceptor i) {
    	httpClientBuilder.addInterceptorLast(i);
    	updateHttpClient();
    }

    public void addRequestInterceptor(RequestInterceptor interceptor) {
        requestInterceptors.add(interceptor);
    }

    @Deprecated
    public void addResponseInterceptor(HttpResponseInterceptor i) {
    	httpClientBuilder.addInterceptorLast(i);
    	updateHttpClient();
    }

    public void addResponseInterceptor(ResponseInterceptor interceptor) {
        responseInterceptors.add(interceptor);
    }

    public void createCookie(String name, String value, String domain) {
        createCookie(name, value, domain, null);
    }

    public void createCookie(String name, String value, String domain, String path) {
        BasicClientCookie cookie = new BasicClientCookie(name, value);
        cookie.setDomain(domain);
        if (path != null) {
            cookie.setPath(path);
        }
        cookieStore.addCookie(cookie);
    }

    public void clearCookies() {
    	cookieStore.clear();
    }

    public Cookie getCookie(String name) {
        return getCookie(name, null, null);
    }

    public Cookie getCookie(String name, String domain) {
        return getCookie(name, domain, null);
    }

    public Cookie getCookie(String name, String domain, String path) {
        for (Cookie cookie : cookieStore.getCookies()) {
            if(cookie.getName().equals(name)) {
                if(domain != null && !domain.equals(cookie.getDomain())) {
                    continue;
                }
                if(path != null && !path.equals(cookie.getPath())) {
                    continue;
                }

                return cookie;
            }
        }

        return null;
    }

    public BrowserMobHttpRequest newPost(String url, net.lightbody.bmp.proxy.jetty.http.HttpRequest proxyRequest) {
        try {
            URI uri = makeUri(url);
            return new BrowserMobHttpRequest(new HttpPost(uri), this, -1, captureContent, proxyRequest);
        } catch (URISyntaxException e) {
            throw reportBadURI(url, "POST", e);
        }
    }

    public BrowserMobHttpRequest newGet(String url, net.lightbody.bmp.proxy.jetty.http.HttpRequest proxyRequest) {
        try {
            URI uri = makeUri(url);
            return new BrowserMobHttpRequest(new HttpGet(uri), this, -1, captureContent, proxyRequest);
        } catch (URISyntaxException e) {
            throw reportBadURI(url, "GET", e);
        }
    }

    public BrowserMobHttpRequest newPatch(String url, net.lightbody.bmp.proxy.jetty.http.HttpRequest proxyRequest) {
    	try {
    		URI uri = makeUri(url);
    		return new BrowserMobHttpRequest(new HttpPatch(uri), this, -1, captureContent, proxyRequest);
    	} catch (URISyntaxException e) {
    		throw reportBadURI(url, "PATCH", e);
    	}
    }

    public BrowserMobHttpRequest newPut(String url, net.lightbody.bmp.proxy.jetty.http.HttpRequest proxyRequest) {
        try {
            URI uri = makeUri(url);
            return new BrowserMobHttpRequest(new HttpPut(uri), this, -1, captureContent, proxyRequest);
        } catch (URISyntaxException e) {
            throw reportBadURI(url, "PUT", e);
        }
    }

    public BrowserMobHttpRequest newDelete(String url, net.lightbody.bmp.proxy.jetty.http.HttpRequest proxyRequest) {
        try {
            URI uri = makeUri(url);
            return new BrowserMobHttpRequest(new HttpDeleteWithBody(uri), this, -1, captureContent, proxyRequest);
        } catch (URISyntaxException e) {
            throw reportBadURI(url, "DELETE", e);
        }
    }

    public BrowserMobHttpRequest newOptions(String url, net.lightbody.bmp.proxy.jetty.http.HttpRequest proxyRequest) {
        try {
            URI uri = makeUri(url);
            return new BrowserMobHttpRequest(new HttpOptions(uri), this, -1, captureContent, proxyRequest);
        } catch (URISyntaxException e) {
            throw reportBadURI(url, "OPTIONS", e);
        }
    }

    public BrowserMobHttpRequest newHead(String url, net.lightbody.bmp.proxy.jetty.http.HttpRequest proxyRequest) {
        try {
            URI uri = makeUri(url);
            return new BrowserMobHttpRequest(new HttpHead(uri), this, -1, captureContent, proxyRequest);
        } catch (URISyntaxException e) {
            throw reportBadURI(url, "HEAD", e);
        }
    }

    public BrowserMobHttpRequest newTrace(String url, net.lightbody.bmp.proxy.jetty.http.HttpRequest proxyRequest) {
        try {
            URI uri = makeUri(url);
            return new BrowserMobHttpRequest(new HttpTrace(uri), this, -1, captureContent, proxyRequest);
        } catch (URISyntaxException e) {
            throw reportBadURI(url, "TRACE", e);
        }
    }

    private URI makeUri(String url) throws URISyntaxException {
        // MOB-120: check for | character and change to correctly escaped %7C
        url = url.replace(" ", "%20");
        url = url.replace(">", "%3C");
        url = url.replace("<", "%3E");
        url = url.replace("#", "%23");
        url = url.replace("{", "%7B");
        url = url.replace("}", "%7D");
        url = url.replace("|", "%7C");
        url = url.replace("\\", "%5C");
        url = url.replace("^", "%5E");
        url = url.replace("~", "%7E");
        url = url.replace("[", "%5B");
        url = url.replace("]", "%5D");
        url = url.replace("`", "%60");
        url = url.replace("\"", "%22");

        URI uri = new URI(url);

        // are we using the default ports for http/https? if so, let's rewrite the URI to make sure the :80 or :443
        // is NOT included in the string form the URI. The reason we do this is that in HttpClient 4.0 the Host header
        // would include a value such as "yahoo.com:80" rather than "yahoo.com". Not sure why this happens but we don't
        // want it to, and rewriting the URI solves it
        if ((uri.getPort() == 80 && "http".equals(uri.getScheme()))
                || (uri.getPort() == 443 && "https".equals(uri.getScheme()))) {
            // we rewrite the URL with a StringBuilder (vs passing in the components of the URI) because if we were
            // to pass in these components using the URI's 7-arg constructor query parameters get double escaped (bad!)
            StringBuilder sb = new StringBuilder(uri.getScheme()).append("://");
            if (uri.getRawUserInfo() != null) {
                sb.append(uri.getRawUserInfo()).append("@");
            }
            sb.append(uri.getHost());
            if (uri.getRawPath() != null) {
                sb.append(uri.getRawPath());
            }
            if (uri.getRawQuery() != null) {
                sb.append("?").append(uri.getRawQuery());
            }
            if (uri.getRawFragment() != null) {
                sb.append("#").append(uri.getRawFragment());
            }

            uri = new URI(sb.toString());
        }
        return uri;
    }

    private BadURIException reportBadURI(String url, String method, URISyntaxException cause) {
        if (this.har != null && harPageRef != null) {
            HarEntry entry = new HarEntry(harPageRef);
            entry.setStartedDateTime(new Date());
            entry.setRequest(new HarRequest(method, url, "HTTP/1.1"));
            entry.setResponse(new HarResponse(-998, "Bad URI", "HTTP/1.1"));
            har.getLog().addEntry(entry);
        }

        throw new BadURIException("Bad URI requested: " + url, cause);
    }

    public void checkTimeout() {
        for (ActiveRequest activeRequest : activeRequests) {
            activeRequest.checkTimeout();
        }

        // Close expired connections
        httpClientConnMgr.closeExpiredConnections();
        // Optionally, close connections
        // that have been idle longer than 30 sec
        httpClientConnMgr.closeIdleConnections(30, TimeUnit.SECONDS);
    }

    public BrowserMobHttpResponse execute(BrowserMobHttpRequest req) {
        if (!allowNewRequests.get()) {
            throw new RuntimeException("No more requests allowed");
        }

        try {
            requestCounter.incrementAndGet();

            for (RequestInterceptor interceptor : requestInterceptors) {
                interceptor.process(req, har);
            }

            BrowserMobHttpResponse response = execute(req, 1);
            for (ResponseInterceptor interceptor : responseInterceptors) {
                interceptor.process(response, har);
            }

            return response;
        } finally {
            requestCounter.decrementAndGet();
        }
    }

    //
    //If we were making cake, this would be the filling :)
    //
    private BrowserMobHttpResponse execute(BrowserMobHttpRequest req, int depth) {
        if (depth >= MAX_REDIRECT) {
            throw new IllegalStateException("Max number of redirects (" + MAX_REDIRECT + ") reached");
        }

        RequestCallback callback = req.getRequestCallback();

        HttpRequestBase method = req.getMethod();
        String url = method.getURI().toString();

        // process any rewrite requests
        boolean rewrote = false;
        String newUrl = url;
        for (RewriteRule rule : rewriteRules) {
            Matcher matcher = rule.getPattern().matcher(newUrl);
            newUrl = matcher.replaceAll(rule.getReplace());
            rewrote = true;
        }

        if (rewrote) {
            try {
                method.setURI(new URI(newUrl));
                url = newUrl;
            } catch (URISyntaxException e) {
                LOG.warn("Could not rewrite url to " + newUrl, e);
            }
        }

        // handle whitelist and blacklist entries
        int mockResponseCode = -1;
        // alias the current whitelist, in case the whitelist is changed while processing this request
        Whitelist currentWhitelist = whitelist;
        if (currentWhitelist.isEnabled()) {
            boolean found = false;
            for (Pattern pattern : currentWhitelist.getPatterns()) {
                if (pattern.matcher(url).matches()) {
                    found = true;
                    break;
                }
            }

            // url does not match whitelist, set the response code
            if (!found) {
                mockResponseCode = currentWhitelist.getResponseCode();
            }
        }

        for (BlacklistEntry blacklistEntry : blacklistEntries) {
            if (blacklistEntry.matches(url, method.getMethod())) {
                mockResponseCode = blacklistEntry.getResponseCode();
                break;
            }
        }

        if (!additionalHeaders.isEmpty()) {
            // Set the additional headers
            for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                method.removeHeaders(key);
                method.addHeader(key, value);
            }
        }


        String charSet = "UTF-8";
        InputStream is = null;
        int statusCode = -998;
        long bytes = 0;
        boolean gzipping = false;
        boolean deflating = false;
        OutputStream os = req.getOutputStream();
        if (os == null) {
            os = new CappedByteArrayOutputStream(1024 * 1024); // MOB-216 don't buffer more than 1 MB
        }

        // link the object up now, before we make the request, so that if we get cut off (ie: favicon.ico request and browser shuts down)
        // we still have the attempt associated, even if we never got a response
        HarEntry entry = new HarEntry(harPageRef);
        entry.setStartedDateTime(new Date());

        // clear out any connection-related information so that it's not stale from previous use of this thread.
        RequestInfo.clear(url, entry);
        RequestInfo.get().start();

        entry.setRequest(new HarRequest(method.getMethod(), url, method.getProtocolVersion().toString()));
        entry.setResponse(new HarResponse(-999, "NO RESPONSE", method.getProtocolVersion().toString()));
        if (this.har != null && harPageRef != null) {
            har.getLog().addEntry(entry);
        }

    	String query = method.getURI().getRawQuery();
    	if (query != null) {
	        MultiMap params = new MultiMap();
	        UrlEncoded.decodeTo(query, params, "UTF-8");
	        for (Object k : params.keySet()) {
	        	for (Object v : params.getValues(k)) {
	        		entry.getRequest().getQueryString().add(new HarNameValuePair((String) k, (String) v));
	        	}
	        }
        }

        String errorMessage = null;
        CloseableHttpResponse response = null;

        BasicHttpContext ctx = new BasicHttpContext();

        ActiveRequest activeRequest = new ActiveRequest(method, entry.getStartedDateTime());
        activeRequests.add(activeRequest);

        // for dealing with automatic authentication
        if (authType == AuthType.NTLM) {
            // todo: not supported yet
            //ctx.setAttribute("preemptive-auth", new NTLMScheme(new JCIFSEngine()));
        } else if (authType == AuthType.BASIC) {
            ctx.setAttribute("preemptive-auth", new BasicScheme());
        }

        StatusLine statusLine = null;
        try {
            // set the User-Agent if it's not already set
            if (method.getHeaders("User-Agent").length == 0) {
                method.addHeader("User-Agent", "bmp.lightbody.net/" + VERSION);
            }

            // was the request mocked out?
            if (mockResponseCode != -1) {
                statusCode = mockResponseCode;

                // TODO: HACKY!!
                callback.handleHeaders(new Header[]{
                        new Header(){
                            @Override
                            public String getName() {
                                return "Content-Type";
                            }

                            @Override
                            public String getValue() {
                                return "text/plain";
                            }

                            @Override
                            public HeaderElement[] getElements() throws ParseException {
                                return new HeaderElement[0];
                            }
                        }
                });
                // Make sure we set the status line here too.
                // Use the version number from the request
                ProtocolVersion version = null;
                int reqDotVersion = req.getProxyRequest().getDotVersion();
                if (reqDotVersion == -1) {
                	version = new HttpVersion(0, 9);
                } else if (reqDotVersion == 0) {
                	version = new HttpVersion(1, 0);
                } else if (reqDotVersion == 1) {
                   	version = new HttpVersion(1, 1);
                }
                // and if not any of these, trust that a Null version will
                // cause an appropriate error
				callback.handleStatusLine(new BasicStatusLine(version, statusCode, "Status set by browsermob-proxy"));
				// No mechanism to look up the response text by status code,
				// so include a notification that this is a synthetic error code.
            } else {
                response = httpClient.execute(method, ctx);
                statusLine = response.getStatusLine();
                statusCode = statusLine.getStatusCode();
                if (callback != null) {
                    callback.handleStatusLine(statusLine);
                    callback.handleHeaders(response.getAllHeaders());
                }

                if (response.getEntity() != null) {
                    is = response.getEntity().getContent();
                }

                // check for null (resp 204 can cause HttpClient to return null, which is what Google does with http://clients1.google.com/generate_204)
                if (is != null) {
                    Header contentEncodingHeader = response.getFirstHeader("Content-Encoding");
                	if(contentEncodingHeader != null) {
                        if ("gzip".equalsIgnoreCase(contentEncodingHeader.getValue())) {
                            gzipping = true;
                        } else if ("deflate".equalsIgnoreCase(contentEncodingHeader.getValue())) {
                        	deflating = true;
                        }
                	}

                    // deal with GZIP content!
                    if(decompress && response.getEntity().getContentLength() != 0) { //getContentLength<0 if unknown
                        if (gzipping) {
                            is = new GZIPInputStream(is);
                        } else if (deflating) {
                        	// RAW deflate only
                        	// WARN : if system is using zlib<=1.1.4 the stream must be append with a dummy byte
                        	// that is not requiered for zlib>1.1.4 (not mentioned on current Inflater javadoc)
                        	is = new InflaterInputStream(is, new Inflater(true));
                        }
                    }

                    if (captureContent) {
                        // todo - something here?
                        os = new ClonedOutputStream(os);
                    }
                    bytes = copyWithStats(is, os);
                }
            }
        } catch (IOException e) {
            errorMessage = e.toString();

            if (callback != null) {
                callback.reportError(e);
            }

            // only log it if we're not shutdown (otherwise, errors that happen during a shutdown can likely be ignored)
            if (!shutdown) {
            	if (LOG.isDebugEnabled()) {
            		LOG.info(String.format("%s when requesting %s", errorMessage, url), e);
            	} else {
            		LOG.info(String.format("%s when requesting %s", errorMessage, url));
            	}
            }
        } finally {
            // the request is done, get it out of here
            activeRequests.remove(activeRequest);

            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // this is OK to ignore
                	LOG.info("Error closing input stream", e);
                }
            }
            if (response != null) {
            	try {
					response.close();
				} catch (IOException e) {
					// nothing to do
					LOG.info("Error closing response stream", e);
				}
            }
        }

        // record the response as ended
        RequestInfo.get().finish();

        // set the start time and other timings
        entry.setStartedDateTime(RequestInfo.get().getStartDate());
        entry.setTimings(RequestInfo.get().getTimings());
        entry.setServerIPAddress(RequestInfo.get().getResolvedAddress());

        // todo: where you store this in HAR?
        // obj.setErrorMessage(errorMessage);
        entry.getResponse().setBodySize(bytes);
        entry.getResponse().getContent().setSize(bytes);
        entry.getResponse().setStatus(statusCode);
        if (response != null) {
        	entry.getResponse().setHttpVersion(response.getProtocolVersion().toString());
        }
        if (statusLine != null) {
            entry.getResponse().setStatusText(statusLine.getReasonPhrase());
        }

        boolean urlEncoded = false;
        if (captureHeaders || captureContent) {
            for (Header header : method.getAllHeaders()) {
                if (header.getValue() != null && header.getValue().startsWith(URLEncodedUtils.CONTENT_TYPE)) {
                    urlEncoded = true;
                }

                entry.getRequest().getHeaders().add(new HarNameValuePair(header.getName(), header.getValue()));
            }

            if (response != null) {
                for (Header header : response.getAllHeaders()) {
                    entry.getResponse().getHeaders().add(new HarNameValuePair(header.getName(), header.getValue()));
                }
            }
        }


    	// +4 => header/data separation
		long requestHeadersSize = method.getRequestLine().toString().length() + 4;
		long requestBodySize = 0;
		for (Header header : method.getAllHeaders()) {
			// +2 => new line
			requestHeadersSize += header.toString().length() + 2;
			// get body size
			if (header.getName().equals("Content-Length")) {
				requestBodySize += Integer.valueOf(header.getValue());
			}
		}
        entry.getRequest().setHeadersSize(requestHeadersSize);
        entry.getRequest().setBodySize(requestBodySize);
        if (captureContent) {

            // can we understand the POST data at all?
            if (method instanceof HttpEntityEnclosingRequestBase && req.getCopy() != null) {
                HttpEntityEnclosingRequestBase enclosingReq = (HttpEntityEnclosingRequestBase) method;
                HttpEntity entity = enclosingReq.getEntity();


                HarPostData data = new HarPostData();
                data.setMimeType(req.getMethod().getFirstHeader("Content-Type").getValue());
                entry.getRequest().setPostData(data);

                if (urlEncoded || URLEncodedUtils.isEncoded(entity)) {
                    try {
    					String content = req.getCopy().toString("UTF-8");

                        if (content != null && content.length() > 0) {
                            List<NameValuePair> result = new ArrayList<NameValuePair>();
                            URLEncodedUtils.parse(result, new Scanner(content), null);

                            List<HarPostDataParam> params = new ArrayList<HarPostDataParam>(result.size());
                            data.setParams(params);

                            for (NameValuePair pair : result) {
                                params.add(new HarPostDataParam(pair.getName(), pair.getValue()));
                            }
                        }
                    } catch (UnsupportedEncodingException e) {
						// realistically this should never happen, since UTF-8 is always a supported encoding
                    	LOG.info("Unexpected problem when parsing input copy", e);
					} catch (RuntimeException e) {
                        LOG.info("Unexpected problem when parsing input copy", e);
                    }
                } else {
                    // not URL encoded, so let's grab the body of the POST and capture that
					try {
						String postBody = req.getCopy().toString("UTF-8");
						data.setText(postBody);
					} catch (UnsupportedEncodingException e) {
						// realistically this should never happen, since UTF-8 is always a supported encoding
                    	LOG.info("Unexpected problem when parsing post body", e);
					}
                }
            }
        }

        //capture request cookies
        javax.servlet.http.Cookie[] cookies = req.getProxyRequest().getCookies();
        for (javax.servlet.http.Cookie cookie : cookies) {
            HarCookie hc = new HarCookie();
            hc.setName(cookie.getName());
            hc.setValue(cookie.getValue());
            entry.getRequest().getCookies().add(hc);
        }

        String contentType = null;

        if (response != null) {
            Header contentTypeHdr = response.getFirstHeader("Content-Type");
            if (contentTypeHdr != null) {
                contentType = contentTypeHdr.getValue();
                entry.getResponse().getContent().setMimeType(contentType);

                if (captureContent && os != null && os instanceof ClonedOutputStream) {
                    ByteArrayOutputStream copy = ((ClonedOutputStream) os).getOutput();

                    if (entry.getResponse().getBodySize() != 0 && (gzipping || deflating)) {
                        // ok, we need to decompress it before we can put it in the har file
                        try {
                            InputStream temp = null;
                            if(gzipping){
                                temp = new GZIPInputStream(new ByteArrayInputStream(copy.toByteArray()));
                            } else if (deflating) {
                            	// RAW deflate only?
                            	// WARN : if system is using zlib<=1.1.4 the stream must be append with a dummy byte
                            	// that is not requiered for zlib>1.1.4 (not mentioned on current Inflater javadoc)
                            	temp = new InflaterInputStream(new ByteArrayInputStream(copy.toByteArray()), new Inflater(true));
                            }
                            copy = new ByteArrayOutputStream();
                            IOUtils.copyAndClose(temp, copy);
                        } catch (IOException e) {
                            throw new RuntimeException("Error when decompressing input stream", e);
                        }
                    }

                    if (hasTextualContent(contentType)) {
                        setTextOfEntry(entry, copy, contentType);
                    } else if(captureBinaryContent){
                        setBinaryContentOfEntry(entry, copy);
                    }
                }

                NameValuePair nvp = contentTypeHdr.getElements()[0].getParameterByName("charset");

                if (nvp != null) {
                    charSet = nvp.getValue();
                }
            }
        }

        if (contentType != null) {
            entry.getResponse().getContent().setMimeType(contentType);
        }

        // checking to see if the client is being redirected
        boolean isRedirect = false;

        String location = null;
        if (response != null && statusCode >= 300 && statusCode < 400 && statusCode != 304) {
            isRedirect = true;

            // pulling the header for the redirect
            Header locationHeader = response.getLastHeader("location");
            if (locationHeader != null) {
                location = locationHeader.getValue();
            } else if (this.followRedirects) {
                throw new RuntimeException("Invalid redirect - missing location header");
            }
        }

        //
        // Response validation - they only work if we're not following redirects
        //

        int expectedStatusCode = req.getExpectedStatusCode();

        // if we didn't mock out the actual response code and the expected code isn't what we saw, we have a problem
        if (mockResponseCode == -1 && expectedStatusCode > -1) {
            if (this.followRedirects) {
                throw new RuntimeException("Response validation cannot be used while following redirects");
            }
            if (expectedStatusCode != statusCode) {
                if (isRedirect) {
                    throw new RuntimeException("Expected status code of " + expectedStatusCode + " but saw " + statusCode
                            + " redirecting to: " + location);
                } else {
                    throw new RuntimeException("Expected status code of " + expectedStatusCode + " but saw " + statusCode);
                }
            }
        }

        // Location header check:
        if (isRedirect && (req.getExpectedLocation() != null)) {
            if (this.followRedirects) {
                throw new RuntimeException("Response validation cannot be used while following redirects");
            }

            if (location.compareTo(req.getExpectedLocation()) != 0) {
                throw new RuntimeException("Expected a redirect to  " + req.getExpectedLocation() + " but saw " + location);
            }
        }

        // end of validation logic

        // basic tail recursion for redirect handling
        if (isRedirect && this.followRedirects) {
            // updating location:
            try {
                URI redirectUri = new URI(location);
                URI newUri = method.getURI().resolve(redirectUri);
                method.setURI(newUri);

                return execute(req, ++depth);
            } catch (URISyntaxException e) {
                LOG.warn("Could not parse URL", e);
            }
        }

        return new BrowserMobHttpResponse(entry, method, response, errorMessage, contentType, charSet);
    }

	private boolean hasTextualContent(String contentType) {
		return contentType != null && contentType.startsWith("text/") ||
				contentType.startsWith("application/x-javascript") ||
				contentType.startsWith("application/javascript")  ||
				contentType.startsWith("application/json")  ||
				contentType.startsWith("application/xml")  ||
				contentType.startsWith("application/xhtml+xml");
	}

	private void setBinaryContentOfEntry(HarEntry entry, ByteArrayOutputStream copy) {
        entry.getResponse().getContent().setText(BaseEncoding.base64().encode(copy.toByteArray()));
		entry.getResponse().getContent().setEncoding("base64");
	}

	private void setTextOfEntry(HarEntry entry, ByteArrayOutputStream copy, String contentType) {
		ContentType contentTypeCharset = ContentType.parse(contentType);
		Charset charset = contentTypeCharset.getCharset();
		if (charset != null) {
			entry.getResponse().getContent().setText(new String(copy.toByteArray(), charset));
		} else {
			entry.getResponse().getContent().setText(new String(copy.toByteArray()));
		}
	}


    public void shutdown() {
        shutdown = true;
        abortActiveRequests();
        rewriteRules.clear();
        blacklistEntries.clear();
        credsProvider.clear();
        httpClientConnMgr.shutdown();
    }

    public void abortActiveRequests() {
        allowNewRequests.set(false);

        for (ActiveRequest activeRequest : activeRequests) {
            activeRequest.abort();
        }

        activeRequests.clear();
    }

    public void setHar(Har har) {
        this.har = har;
    }

    public void setHarPageRef(String harPageRef) {
        this.harPageRef = harPageRef;
    }

    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public void setSocketOperationTimeout(int readTimeout) {
        requestConfigBuilder.setSocketTimeout(readTimeout);
    	httpClientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());
    	updateHttpClient();
    }

    public void setConnectionTimeout(int connectionTimeout) {
        requestConfigBuilder.setConnectTimeout(connectionTimeout);
    	httpClientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());
    	updateHttpClient();
    }

    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;

    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public void autoBasicAuthorization(String domain, String username, String password) {
        authType = AuthType.BASIC;
        credsProvider.setCredentials(
                new AuthScope(domain, -1),
                new UsernamePasswordCredentials(username, password));
    }

    public void autoNTLMAuthorization(String domain, String username, String password) {
        authType = AuthType.NTLM;
        credsProvider.setCredentials(
                new AuthScope(domain, -1),
                new NTCredentials(username, password, "workstation", domain));
    }

    public void rewriteUrl(String match, String replace) {
        rewriteRules.add(new RewriteRule(match, replace));
    }

    public List<RewriteRule> getRewriteRules() {
        return rewriteRules;
    }

    public void removeRewriteRule(String urlPattern) {
        for (RewriteRule rewriteRule : rewriteRules) {
            if (rewriteRule.getPattern().pattern().equals(urlPattern)) {
                // rewriteRules is a CopyOnWriteArrayList, so we can modify it while iterating over it
                rewriteRules.remove(rewriteRule);
            }
        }
    }

    public void clearRewriteRules() {
    	rewriteRules.clear();
    }

    /**
     * this method is provided for backwards compatibility before we renamed it to blacklistRequests (note the plural)
     * @deprecated use blacklistRequests(String pattern, int responseCode)
     */
    @Deprecated
    public void blacklistRequest(String pattern, int responseCode, String method) {
        blacklistRequests(pattern, responseCode, method);
    }

    public void blacklistRequests(String pattern, int responseCode, String method) {
        blacklistEntries.add(new BlacklistEntry(pattern, responseCode, method));
    }

    /**
     * @deprecated Use getBlacklistedUrls()
     */
    @Deprecated
    public List<BlacklistEntry> getBlacklistedRequests() {
    	List<BlacklistEntry> blacklist = new ArrayList<BlacklistEntry>(blacklistEntries.size());
    	blacklist.addAll(blacklistEntries);

        return blacklist;
    }

    public Collection<BlacklistEntry> getBlacklistedUrls() {
    	return blacklistEntries;
    }

    public void clearBlacklist() {
        blacklistEntries.clear();
    }

    public boolean isWhitelistEnabled() {
    	return whitelist.isEnabled();
    }

    /**
     * @deprecated use getWhitelistUrls()
     * @return <i>unmodifiable</i> list of whitelisted Patterns
     */
    @Deprecated
    public List<Pattern> getWhitelistRequests() {
    	List<Pattern> whitelistPatterns = new ArrayList<Pattern>(whitelist.getPatterns().size());
    	whitelistPatterns.addAll(whitelist.getPatterns());

        return Collections.unmodifiableList(whitelistPatterns);
    }

    /**
     * Retrieves Patterns of URLs that have been whitelisted.
     *
     * @return <i>unmodifiable</i> whitelisted URL Patterns
     */
    public Collection<Pattern> getWhitelistUrls() {
    	return whitelist.getPatterns();
    }

    public int getWhitelistResponseCode() {
    	return whitelist.getResponseCode();
    }

    /**
     * Whitelist the specified request patterns, returning the specified responseCode for non-whitelisted
     * requests.
     *
     * @param patterns regular expression strings matching URL patterns to whitelist. if empty or null,
     * 		  the whitelist will be enabled but will not match any URLs.
     * @param responseCode the HTTP response code to return for non-whitelisted requests
     */
    public void whitelistRequests(String[] patterns, int responseCode) {
    	if (patterns == null || patterns.length == 0) {
    		whitelist = new Whitelist(responseCode);
    	} else {
    		whitelist = new Whitelist(patterns, responseCode);
    	}
    }

    /**
     * Clears and disables the current whitelist.
     */
    public void clearWhitelist() {
    	whitelist = Whitelist.WHITELIST_DISABLED;
    }

    public void addHeader(String name, String value) {
        additionalHeaders.put(name, value);
    }

    public void setAdditionalHeaders(Map<String, String> additionalHeaders) {
        additionalHeaders.clear();
        additionalHeaders.putAll(additionalHeaders);
    }

    public Map<String, String> getAdditionalHeaders() {
        return ImmutableMap.<String, String>builder().putAll(additionalHeaders).build();
    }

    /**
     * init HTTP client, using a browser which handle cookies, gzip compression and redirects
     */
    public void prepareForBrowser() {
        // Clear cookies, let the browser handle them
    	cookieStore.clear();
    	CookieSpecProvider easySpecProvider = new CookieSpecProvider() {
    	    public CookieSpec create(HttpContext context) {
    	        return new BrowserCompatSpec() {
    	            @Override
    	            public void validate(Cookie cookie, CookieOrigin origin)
    	                    throws MalformedCookieException {
    	                // Oh, I am easy
    	            }
    	        };
    	    }
    	};

    	Registry<CookieSpecProvider> r = RegistryBuilder.<CookieSpecProvider>create()
    	        .register(CookieSpecs.BEST_MATCH,
    	            new BestMatchSpecFactory())
    	        .register(CookieSpecs.BROWSER_COMPATIBILITY,
    	            new BrowserCompatSpecFactory())
    	        .register("easy", easySpecProvider)
    	        .build();

    	RequestConfig requestConfig = RequestConfig.custom()
    	        .setCookieSpec("easy")
    	        .build();

    	httpClientBuilder.setDefaultCookieSpecRegistry(r)
	    .setDefaultRequestConfig(requestConfig);
    	updateHttpClient();

        decompress =  false;
        setFollowRedirects(false);
    }

    /**
     * CloseableHttpClient doesn't permit anymore to change parameters easily.
     * This method allow you to rebuild the httpClientBuilder to get the CloseableHttpClient
     * When the config is changed.
     *
     * So httpClient reference change this may lead to concurrency issue.
     */
    private void updateHttpClient(){
    	httpClient = httpClientBuilder.build();
    }

    public String remappedHost(String host) {
        if (resolverWrapper.getResolver() instanceof  AdvancedHostResolver) {
            AdvancedHostResolver advancedHostResolver = (AdvancedHostResolver) resolverWrapper.getResolver();

            return advancedHostResolver.getHostRemappings().get(host);
        } else {
            LOG.warn("Attempting to find remapped host for {}, but resolver is not an AdvancedHostResolver. Resolver: {}", host, resolverWrapper.getResolver());

            return "";
        }
    }

    public List<String> originalHosts(String host) {
        if (resolverWrapper.getResolver() instanceof AdvancedHostResolver) {
            AdvancedHostResolver advancedHostResolver = (AdvancedHostResolver) resolverWrapper.getResolver();
            return ImmutableList.copyOf(advancedHostResolver.getOriginalHostnames(host));
        } else {
            LOG.warn("Attempting to find original hosts for {}, but resolver is not an AdvancedHostResolver. Resolver: {}", host, resolverWrapper.getResolver());

            return Collections.emptyList();
        }
    }

    public Har getHar() {
        return har;
    }

    public void setCaptureHeaders(boolean captureHeaders) {
        this.captureHeaders = captureHeaders;
    }

    public void setCaptureContent(boolean captureContent) {
        this.captureContent = captureContent;
    }

    public void setCaptureBinaryContent(boolean captureBinaryContent) {
        this.captureBinaryContent = captureBinaryContent;
    }

    public void setHttpProxy(String httpProxy) {
        String host = httpProxy.split(":")[0];
        Integer port = Integer.parseInt(httpProxy.split(":")[1]);
        HttpHost proxy = new HttpHost(host, port);
        httpClientBuilder.setProxy(proxy);
        updateHttpClient();
    }

    static class PreemptiveAuth implements HttpRequestInterceptor {
        public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
            AuthState authState = (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);

            // If no auth scheme avaialble yet, try to initialize it preemptively
            if (authState.getAuthScheme() == null) {
                AuthScheme authScheme = (AuthScheme) context.getAttribute("preemptive-auth");
                CredentialsProvider credsProvider = (CredentialsProvider) context.getAttribute(HttpClientContext.CREDS_PROVIDER);
                HttpHost targetHost = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
                if (authScheme != null) {
                    Credentials creds = credsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()));
                    if (creds != null) {
                        authState.update(authScheme, creds);
                    }
                }
            }
        }
    }

    class ActiveRequest {
        private final HttpRequestBase request;
        private final Date start;
        private final AtomicBoolean aborting = new AtomicBoolean(false);

        ActiveRequest(HttpRequestBase request, Date start) {
            this.request = request;
            this.start = start;
        }

        /**
         * Checks the timeout for this request, and aborts if necessary.
         * @return true if the request was aborted for exceeding its timeout, otherwise false.
         */
        boolean checkTimeout() {
        	if (aborting.get()) {
        		return false;
        	}

            if (requestTimeout != -1) {
                if (request != null && start != null && new Date(System.currentTimeMillis() - requestTimeout).after(start)) {
                	boolean okayToAbort = aborting.compareAndSet(false, true);
                	if (okayToAbort) {
                		LOG.info("Aborting request to {} after it failed to complete in {} ms", request.getURI().toString(), requestTimeout);

                    	abort();

                    	return true;
                	}
                }
            }

            return false;
        }

        public void abort() {
            request.abort();

            // no need to close the connection -- the call to request.abort() releases the connection itself 
        }
    }

    private enum AuthType {
        NONE, BASIC, NTLM
    }

    public boolean isShutdown() {
        return shutdown;
    }

//    public void clearDNSCache() {
//        this.hostNameResolver.clearCache();
//    }

//    public void setDNSCacheTimeout(int timeout) {
//        this.hostNameResolver.setCacheTimeout(timeout);
//    }

    public static long copyWithStats(InputStream is, OutputStream os) throws IOException {
        long bytesCopied = 0;
        byte[] buffer = new byte[BUFFER];
        int length;

        try {
            // read the first byte
            int firstByte = is.read();

            if (firstByte == -1) {
                return 0;
            }

            os.write(firstByte);
            bytesCopied++;

            do {
                length = is.read(buffer, 0, BUFFER);
                if (length != -1) {
                    bytesCopied += length;
                    os.write(buffer, 0, length);
                    os.flush();
                }
            } while (length != -1);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                // ok to ignore
            }

            try {
                os.close();
            } catch (IOException e) {
                // ok to ignore
            }
        }

        return bytesCopied;
    }

    public String getHeadersFilterRegexp() {
        return headersFilterRegexp;
    }

    public void setHeadersFilterRegexp(String headersFilterRegexp) {
        this.headersFilterRegexp = headersFilterRegexp;
    }

    public boolean isCaptureBinaryContent() {
        return captureBinaryContent;
    }

    public boolean isCaptureContent() {
        return captureContent;
    }

    public boolean isCaptureHeaders() {
        return captureHeaders;
    }

    public AdvancedHostResolver getResolver() {
        return resolverWrapper.getResolver();
    }

    public void setResolver(AdvancedHostResolver resolver) {
        resolverWrapper.setResolver(resolver);
    }
}
