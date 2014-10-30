package net.lightbody.bmp.proxy.http;

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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.Inflater;

import net.lightbody.bmp.core.har.*;
import net.lightbody.bmp.proxy.BlacklistEntry;
import net.lightbody.bmp.proxy.Main;
import net.lightbody.bmp.proxy.WhitelistEntry;
import net.lightbody.bmp.proxy.util.*;
import net.sf.uadetector.ReadableUserAgent;
import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.service.UADetectorServiceFactory;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpConnection;
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
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;
import org.java_bandwidthlimiter.StreamManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Cache;
import org.xbill.DNS.DClass;


/**
 * WARN : Require zlib > 1.1.4 (deflate support)
 */
public class BrowserMobHttpClient {
	private static final Logger LOG = LoggerFactory.getLogger(BrowserMobHttpClient.class);
	
	public static UserAgentStringParser PARSER = UADetectorServiceFactory.getCachingAndUpdatingParser();
	
    private static final int BUFFER = 4096;

    private Har har;
    private String harPageRef;

    /**
     * keep headers
     */
    private boolean captureHeaders;
    
    /**
     * keep contents
     */
    private boolean captureContent;
    
    /**
     * keep binary contents (if captureContent is set to true, default policy is to capture binary contents too)
     */
    private boolean captureBinaryContent = true;

    /**
     * socket factory dedicated to port 80 (HTTP)
     */
    private SimulatedSocketFactory socketFactory;
    
    /**
     * socket factory dedicated to port 443 (HTTPS)
     */
    private TrustingSSLSocketFactory sslSocketFactory;


    private PoolingHttpClientConnectionManager httpClientConnMgr;
    
    /**
     * Builders for httpClient
     * Each time you change their configuration you should call updateHttpClient()
     */
	private Builder requestConfigBuilder;
    private HttpClientBuilder httpClientBuilder;
    
    /**
     * The current httpClient which will execute HTTP requests
     */
    private CloseableHttpClient httpClient;
    
    private BasicCookieStore cookieStore = new BasicCookieStore();
    
    /**
     * List of rejected URL patterns
     */
    private List<BlacklistEntry> blacklistEntries = new CopyOnWriteArrayList<BlacklistEntry>();
    
    /**
     * List of accepted URL patterns
     */

    private WhitelistEntry whitelistEntry = null;
    
    /**
     * List of URLs to rewrite
     */
    private List<RewriteRule> rewriteRules = new CopyOnWriteArrayList<RewriteRule>();
    
    /**
     * triggers to process when sending request
     */
    private List<RequestInterceptor> requestInterceptors = new CopyOnWriteArrayList<RequestInterceptor>();
    
    /**
     * triggers to process when receiving response
     */
    private List<ResponseInterceptor> responseInterceptors = new CopyOnWriteArrayList<ResponseInterceptor>();
    
    /**
     * additional headers sent with request
     */
    private HashMap<String, String> additionalHeaders = new LinkedHashMap<String, String>();
    
    /**
     * request timeout: set to -1 to disable timeout
     */
    private int requestTimeout = -1;
    
    /**
     * is it possible to add a new request?
     */
    private AtomicBoolean allowNewRequests = new AtomicBoolean(true);
    
    /**
     * DNS lookup handler
     */
    private BrowserMobHostNameResolver hostNameResolver;
    
    /**
     * does the proxy support gzip compression? (set to false if you go through a browser)
     */
    private boolean decompress = true;
    
    /**
     * set of active requests
     */
    // not using CopyOnWriteArray because we're WRITE heavy and it is for READ heavy operations
    // instead doing it the old fashioned way with a synchronized block
    private final Set<ActiveRequest> activeRequests = new HashSet<ActiveRequest>();
    
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
    private AtomicInteger requestCounter;
    
    /**
     * Init HTTP client
     * @param streamManager will be capped to 100 Megabits (by default it is disabled)
     * @param requestCounter indicates the number of remaining requests
     */
    public BrowserMobHttpClient(final StreamManager streamManager, AtomicInteger requestCounter) {
        this.requestCounter = requestCounter;
        hostNameResolver = new BrowserMobHostNameResolver(new Cache(DClass.ANY));
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
        
        httpClientConnMgr = new PoolingHttpClientConnectionManager(registry, hostNameResolver) {
            @Override
            public ConnectionRequest requestConnection(HttpRoute route, Object state) {
                final ConnectionRequest wrapped = super.requestConnection(route, state);
                return new ConnectionRequest() {
                    @Override
                    public HttpClientConnection get(long timeout, TimeUnit tunit) throws InterruptedException, ExecutionException, ConnectionPoolTimeoutException {
                        Date start = new Date();
                        try {
                            return wrapped.get(timeout, tunit);
                        } finally {
                            RequestInfo.get().blocked(start, new Date());
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


                    // set date before sending
                    Date start = new Date();
                    
                    // send request
                    HttpResponse response = super.doSendRequest(request, conn, context);
                    
                    // set "sending" for resource
                    RequestInfo.get().send(start, new Date());
                    return response;
                }

                @Override
                protected HttpResponse doReceiveResponse(HttpRequest request, HttpClientConnection conn, HttpContext context) throws HttpException, IOException {
                    Date start = new Date();    
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
                    if(streamManager.getLatency() > 0 && RequestInfo.get().getLatency() != null){
                        // retrieve real latency discovered in connect SimulatedSocket
                    	long realLatency = RequestInfo.get().getLatency();
                        // add latency
                    	if(realLatency < streamManager.getLatency()){
                            try {
								Thread.sleep(streamManager.getLatency()-realLatency);
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
							}
                        }  
                    }  
                    // set waiting time
                    RequestInfo.get().wait(start,new Date());
                    
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
        hostNameResolver.remap(source, target);
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
            entry.setTime(0);
            entry.setRequest(new HarRequest(method, url, "HTTP/1.1"));
            entry.setResponse(new HarResponse(-998, "Bad URI", "HTTP/1.1"));
            entry.setTimings(new HarTimings());
            har.getLog().addEntry(entry);
        }

        throw new BadURIException("Bad URI requested: " + url, cause);
    }

    public void checkTimeout() {
        synchronized (activeRequests) {
            for (ActiveRequest activeRequest : activeRequests) {
                activeRequest.checkTimeout();
            }
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
        
        // save the browser and version if it's not yet been set
        if (har != null && har.getLog().getBrowser() == null) {
            Header[] uaHeaders = method.getHeaders("User-Agent");
            if (uaHeaders != null && uaHeaders.length > 0) {
                String userAgent = uaHeaders[0].getValue();
                try {
                    // note: this doesn't work for 'Fandango/4.5.1 CFNetwork/548.1.4 Darwin/11.0.0'
                    ReadableUserAgent uai = PARSER.parse(userAgent);
                    String browser = uai.getName();
                    String version = uai.getVersionNumber().toVersionString();
                    har.getLog().setBrowser(new HarNameVersion(browser, version));
                } catch (RuntimeException e) {
                	LOG.warn("Failed to parse user agent string", e);
                }
            }
        }

        // process any rewrite requests
        boolean rewrote = false;
        String newUrl = url;
        for (RewriteRule rule : rewriteRules) {
            Matcher matcher = rule.match.matcher(newUrl);
            newUrl = matcher.replaceAll(rule.replace);
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
        synchronized (this) {
            // guard against concurrent modification of whitelistEntry
            if (whitelistEntry != null) {
                boolean found = false;
                for (Pattern pattern : whitelistEntry.getPatterns()) {
                    if (pattern.matcher(url).matches()) {
                        found = true;
                        break;
                    }
                }
                
                // url does not match whitelist, set the response code
                if (!found) {
                    mockResponseCode = whitelistEntry.getResponseCode();
                }
            }
        }

        if (blacklistEntries != null) {
            for (BlacklistEntry blacklistEntry : blacklistEntries) {
                if (blacklistEntry.getPattern().matcher(url).matches()
					&& blacklistEntry.getMethod().matcher(method.getMethod()).matches()) {
                    mockResponseCode = blacklistEntry.getResponseCode();
                    break;
                }
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

        // clear out any connection-related information so that it's not stale from previous use of this thread.
        RequestInfo.clear(url, entry);

        entry.setRequest(new HarRequest(method.getMethod(), url, method.getProtocolVersion().toString()));
        entry.setResponse(new HarResponse(-999, "NO RESPONSE", method.getProtocolVersion().toString()));
        if (this.har != null && harPageRef != null) {
            har.getLog().addEntry(entry);
        }

    	String query = method.getURI().getRawQuery();
    	if (query != null) {
	        MultiMap<String> params = new MultiMap<String>();
	        UrlEncoded.decodeTo(query, params, "UTF-8");
	        for (String k : params.keySet()) {
	        	for (Object v : params.getValues(k)) {
	        		entry.getRequest().getQueryString().add(new HarNameValuePair(k, (String) v));
	        	}
	        }
        }

        String errorMessage = null;
        CloseableHttpResponse response = null;

        BasicHttpContext ctx = new BasicHttpContext();

        ActiveRequest activeRequest = new ActiveRequest(method, ctx, entry.getStartedDateTime());
        synchronized (activeRequests) {
            activeRequests.add(activeRequest);
        }

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
                method.addHeader("User-Agent", "bmp.lightbody.net/" + Main.getVersion());
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
        	//FIXME: use set backed by ConcurrentHashMap
            synchronized (activeRequests) {
                activeRequests.remove(activeRequest);
            }

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
        entry.setStartedDateTime(RequestInfo.get().getStart());
        entry.setTimings(RequestInfo.get().getTimings());
        entry.setServerIPAddress(RequestInfo.get().getResolvedAddress());
        entry.setTime(RequestInfo.get().getTotalTime());

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
    					String content = new String(req.getCopy().toByteArray(), "UTF-8");

                        if (content != null && content.length() > 0) {
                            List<NameValuePair> result = new ArrayList<NameValuePair>();
                            URLEncodedUtils.parse(result, new Scanner(content), null);

                            List<HarPostDataParam> params = new ArrayList<HarPostDataParam>();
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
                    data.setText(new String(req.getCopy().toByteArray()));
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
                            IOUtils.copy(temp, copy);
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
		entry.getResponse().getContent().setText(Base64.byteArrayToBase64(copy.toByteArray()));
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
        HttpClientInterrupter.release(this);
    }

    public void abortActiveRequests() {
        allowNewRequests.set(true);

        synchronized (activeRequests) {
            for (ActiveRequest activeRequest : activeRequests) {
                activeRequest.abort();
            }
            activeRequests.clear();
        }
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

    public void clearRewriteRules() {
    	rewriteRules.clear();
    }

    // this method is provided for backwards compatibility before we renamed it to
    // blacklistRequests (note the plural)
    public void blacklistRequest(String pattern, int responseCode, String method) {
        blacklistRequests(pattern, responseCode, method);
    }

    public void blacklistRequests(String pattern, int responseCode, String method) {
        blacklistEntries.add(new BlacklistEntry(pattern, responseCode, method));
    }

    public List<BlacklistEntry> getBlacklistedRequests() {
        return blacklistEntries;
    }

    public void clearBlacklist() {
        blacklistEntries.clear();
    }

    public WhitelistEntry getWhitelistRequests() {
        return whitelistEntry;
    }

    public synchronized void whitelistRequests(String[] patterns, int responseCode) {
        // synchronized to guard against concurrent modification
        whitelistEntry = new WhitelistEntry(patterns, responseCode);
    }

    public synchronized void clearWhitelist() {
        // synchronized to guard against concurrent modification
        whitelistEntry = null;
    }
    
    public void addHeader(String name, String value) {
        additionalHeaders.put(name, value);
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
        return hostNameResolver.remapping(host);
    }

    public List<String> originalHosts(String host) {
        return hostNameResolver.original(host);
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
        HttpRequestBase request;
        BasicHttpContext ctx;
        Date start;

        ActiveRequest(HttpRequestBase request, BasicHttpContext ctx, Date start) {
            this.request = request;
            this.ctx = ctx;
            this.start = start;
        }

        void checkTimeout() {
            if (requestTimeout != -1) {
                if (request != null && start != null && new Date(System.currentTimeMillis() - requestTimeout).after(start)) {
                    LOG.info("Aborting request to {} after it failed to complete in {} ms", request.getURI().toString(), requestTimeout);

                    abort();
                }
            }
        }

        public void abort() {
            request.abort();

            // try to close the connection? is this necessary? unclear based on preliminary debugging of HttpClient, but
            // it doesn't seem to hurt to try
            HttpConnection conn = (HttpConnection) ctx.getAttribute("http.connection");
            if (conn != null) {
                try {
                    conn.close();
                } catch (IOException e) {
                    // this is fine, we're shutting it down anyway
                }
            }
        }
    }

    private class RewriteRule {
        private Pattern match;
        private String replace;

        private RewriteRule(String match, String replace) {
            this.match = Pattern.compile(match);
            this.replace = replace;
        }
    }

    private enum AuthType {
        NONE, BASIC, NTLM
    }

    public void clearDNSCache() {
        this.hostNameResolver.clearCache();
    }

    public void setDNSCacheTimeout(int timeout) {
        this.hostNameResolver.setCacheTimeout(timeout);
    }

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
}
