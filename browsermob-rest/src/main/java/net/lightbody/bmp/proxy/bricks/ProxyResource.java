package net.lightbody.bmp.proxy.bricks;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.sitebricks.At;
import com.google.sitebricks.client.transport.Json;
import com.google.sitebricks.client.transport.Text;
import com.google.sitebricks.headless.Reply;
import com.google.sitebricks.headless.Request;
import com.google.sitebricks.headless.Service;
import com.google.sitebricks.http.Delete;
import com.google.sitebricks.http.Get;
import com.google.sitebricks.http.Post;
import com.google.sitebricks.http.Put;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.exception.ProxyExistsException;
import net.lightbody.bmp.exception.ProxyPortsExhaustedException;
import net.lightbody.bmp.exception.UnsupportedCharsetException;
import net.lightbody.bmp.filters.JavascriptRequestResponseFilter;
import net.lightbody.bmp.proxy.CaptureType;
import net.lightbody.bmp.proxy.LegacyProxyServer;
import net.lightbody.bmp.proxy.ProxyManager;
import net.lightbody.bmp.proxy.ProxyServer;
import net.lightbody.bmp.proxy.http.BrowserMobHttpRequest;
import net.lightbody.bmp.proxy.http.BrowserMobHttpResponse;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.http.ResponseInterceptor;
import net.lightbody.bmp.util.BrowserMobHttpUtil;
import org.java_bandwidthlimiter.StreamManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.*;

@At("/proxy")
@Service
public class ProxyResource {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyResource.class);

    private final ProxyManager proxyManager;

    @Inject
    public ProxyResource(ProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Get
    public Reply<?> getProxies() {
        Collection<ProxyDescriptor> proxyList = new ArrayList<ProxyDescriptor>();
        for (LegacyProxyServer proxy : proxyManager.get()) {
            proxyList.add(new ProxyDescriptor(proxy.getPort()));
        }
        return Reply.with(new ProxyListDescriptor(proxyList)).as(Json.class);
    }

    @Post
    public Reply<?> newProxy(Request<String> request) {
        String systemProxyHost = System.getProperty("http.proxyHost");
        String systemProxyPort = System.getProperty("http.proxyPort");
        String httpProxy = request.param("httpProxy");
        String proxyUsername = request.param("proxyUsername");
        String proxyPassword = request.param("proxyPassword");

        Hashtable<String, String> options = new Hashtable<String, String>();

        // If the upstream proxy is specified via query params that should override any default system level proxy.
        if (httpProxy != null) {
            options.put("httpProxy", httpProxy);
        } else if ((systemProxyHost != null) && (systemProxyPort != null)) {
            options.put("httpProxy", String.format("%s:%s", systemProxyHost, systemProxyPort));
        }

        // this is a short-term work-around for Proxy Auth in the REST API until the upcoming REST API refactor
        if (proxyUsername != null && proxyPassword != null) {
            options.put("proxyUsername", proxyUsername);
            options.put("proxyPassword", proxyPassword);
        }

        String paramBindAddr = request.param("bindAddress");
        String paramServerBindAddr = request.param("serverBindAddress");
        Integer paramPort = request.param("port") == null ? null : Integer.parseInt(request.param("port"));

        String useEccString = request.param("useEcc");
        boolean useEcc = Boolean.parseBoolean(useEccString);

        String trustAllServersString = request.param("trustAllServers");
        boolean trustAllServers = Boolean.parseBoolean(trustAllServersString);

        LOG.debug("POST proxy instance on bindAddress `{}` & port `{}` & serverBindAddress `{}`",
                paramBindAddr, paramPort, paramServerBindAddr);
        LegacyProxyServer proxy;
        try {
            proxy = proxyManager.create(options, paramPort, paramBindAddr, paramServerBindAddr, useEcc, trustAllServers);
        } catch (ProxyExistsException ex) {
            return Reply.with(new ProxyDescriptor(ex.getPort())).status(455).as(Json.class);
        } catch (ProxyPortsExhaustedException ex) {
            return Reply.saying().status(456);
        } catch (Exception ex) {
            StringWriter s = new StringWriter();
            ex.printStackTrace(new PrintWriter(s));
            return Reply.with(s).as(Text.class).status(550);
        }
        return Reply.with(new ProxyDescriptor(proxy.getPort())).as(Json.class);
    }

    @Get
    @At("/:port/har")
    public Reply<?> getHar(@Named("port") int port) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        Har har = proxy.getHar();

        return Reply.with(har).as(Json.class);
    }

    @Put
    @At("/:port/har")
    public Reply<?> newHar(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String initialPageRef = request.param("initialPageRef");
        String initialPageTitle = request.param("initialPageTitle");
        Har oldHar = proxy.newHar(initialPageRef, initialPageTitle);

        String captureHeaders = request.param("captureHeaders");
        String captureContent = request.param("captureContent");
        String captureBinaryContent = request.param("captureBinaryContent");
        proxy.setCaptureHeaders(Boolean.parseBoolean(captureHeaders));
        proxy.setCaptureContent(Boolean.parseBoolean(captureContent));
        proxy.setCaptureBinaryContent(Boolean.parseBoolean(captureBinaryContent));

        String captureCookies = request.param("captureCookies");
        if (proxy instanceof BrowserMobProxyServer && Boolean.parseBoolean(captureCookies)) {
            BrowserMobProxyServer browserMobProxyServer = (BrowserMobProxyServer) proxy;
            browserMobProxyServer.enableHarCaptureTypes(CaptureType.getCookieCaptureTypes());
        }

        if (oldHar != null) {
            return Reply.with(oldHar).as(Json.class);
        } else {
            return Reply.saying().noContent();
        }
    }

    @Put
    @At("/:port/har/pageRef")
    public Reply<?> setPage(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String pageRef = request.param("pageRef");
        String pageTitle = request.param("pageTitle");
        proxy.newPage(pageRef, pageTitle);

        return Reply.saying().ok();
    }

    @Get
    @At("/:port/blacklist")
    public Reply<?> getBlacklist(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        return Reply.with(proxy.getBlacklistedUrls()).as(Json.class);
    }

    @Put
    @At("/:port/blacklist")
    public Reply<?> blacklist(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String blacklist = request.param("regex");
        int responseCode = parseResponseCode(request.param("status"));
        String method = request.param("method");
        proxy.blacklistRequests(blacklist, responseCode, method);

        return Reply.saying().ok();
    }

    @Delete
    @At("/:port/blacklist")
    public Reply<?> clearBlacklist(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        proxy.clearBlacklist();
        return Reply.saying().ok();
    }

    @Get
    @At("/:port/whitelist")
    public Reply<?> getWhitelist(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        return Reply.with(proxy.getWhitelistUrls()).as(Json.class);
    }

    @Put
    @At("/:port/whitelist")
    public Reply<?> whitelist(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String regex = request.param("regex");
        int responseCode = parseResponseCode(request.param("status"));
        proxy.whitelistRequests(regex.split(","), responseCode);

        return Reply.saying().ok();
    }

    @Delete
    @At("/:port/whitelist")
    public Reply<?> clearWhitelist(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        proxy.clearWhitelist();
        return Reply.saying().ok();
    }

    @Post
    @At("/:port/auth/basic/:domain")
    public Reply<?> autoBasicAuth(@Named("port") int port, @Named("domain") String domain, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        Map<String, String> credentials = request.read(HashMap.class).as(Json.class);
        proxy.autoBasicAuthorization(domain, credentials.get("username"), credentials.get("password"));

        return Reply.saying().ok();
    }

    @Post
    @At("/:port/headers")
    public Reply<?> updateHeaders(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        Map<String, String> headers = request.read(Map.class).as(Json.class);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            proxy.addHeader(key, value);
        }
        return Reply.saying().ok();
    }

    @Post
    @At("/:port/interceptor/response")
    public Reply<?> addResponseInterceptor(@Named("port") int port, Request<String> request) throws IOException, ScriptException {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        if (!(proxy instanceof ProxyServer)) {
            return Reply.saying().badRequest();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        request.readTo(baos);

        ScriptEngineManager mgr = new ScriptEngineManager();
        final ScriptEngine engine = mgr.getEngineByName("JavaScript");
        Compilable compilable = (Compilable) engine;
        final CompiledScript script = compilable.compile(baos.toString());

        proxy.addResponseInterceptor(new ResponseInterceptor() {
            @Override
            public void process(BrowserMobHttpResponse response, Har har) {
                Bindings bindings = engine.createBindings();
                bindings.put("response", response);
                bindings.put("har", har);
                bindings.put("log", LOG);
                try {
                    script.eval(bindings);
                } catch (ScriptException e) {
                    LOG.error("Could not execute JS-based response interceptor", e);
                }
            }
        });

        return Reply.saying().ok();
    }

    @Post
    @At("/:port/interceptor/request")
    public Reply<?> addRequestInterceptor(@Named("port") int port, Request<String> request) throws IOException, ScriptException {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        if (!(proxy instanceof ProxyServer)) {
            return Reply.saying().badRequest();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        request.readTo(baos);

        ScriptEngineManager mgr = new ScriptEngineManager();
        final ScriptEngine engine = mgr.getEngineByName("JavaScript");
        Compilable compilable = (Compilable) engine;
        final CompiledScript script = compilable.compile(baos.toString());

        proxy.addRequestInterceptor(new RequestInterceptor() {
            @Override
            public void process(BrowserMobHttpRequest request, Har har) {
                Bindings bindings = engine.createBindings();
                bindings.put("request", request);
                bindings.put("har", har);
                bindings.put("log", LOG);
                try {
                    script.eval(bindings);
                } catch (ScriptException e) {
                    LOG.error("Could not execute JS-based response interceptor", e);
                }
            }
        });

        return Reply.saying().ok();
    }

    @Post
    @At("/:port/filter/request")
    public Reply<?> addRequestFilter(@Named("port") int port, Request<String> request) throws IOException, ScriptException {
        LegacyProxyServer legacyProxy = proxyManager.get(port);
        if (legacyProxy == null) {
            return Reply.saying().notFound();
        }

        if (!(legacyProxy instanceof BrowserMobProxyServer)) {
            return Reply.saying().badRequest();
        }

        BrowserMobProxy proxy = (BrowserMobProxy) legacyProxy;

        JavascriptRequestResponseFilter requestResponseFilter = new JavascriptRequestResponseFilter();

        String script = getEntityBodyFromRequest(request);
        requestResponseFilter.setRequestFilterScript(script);

        proxy.addRequestFilter(requestResponseFilter);

        return Reply.saying().ok();
    }

    @Post
    @At("/:port/filter/response")
    public Reply<?> addResponseFilter(@Named("port") int port, Request<String> request) throws IOException, ScriptException {
        LegacyProxyServer legacyProxy = proxyManager.get(port);
        if (legacyProxy == null) {
            return Reply.saying().notFound();
        }

        if (!(legacyProxy instanceof BrowserMobProxyServer)) {
            return Reply.saying().badRequest();
        }

        BrowserMobProxy proxy = (BrowserMobProxy) legacyProxy;

        JavascriptRequestResponseFilter requestResponseFilter = new JavascriptRequestResponseFilter();

        String script = getEntityBodyFromRequest(request);
        requestResponseFilter.setResponseFilterScript(script);

        proxy.addResponseFilter(requestResponseFilter);

        return Reply.saying().ok();
    }

    @Put
    @At("/:port/limit")
    public Reply<?> limit(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        StreamManager streamManager = proxy.getStreamManager();
        String upstreamKbps = request.param("upstreamKbps");
        if (upstreamKbps != null) {
            try {
                streamManager.setUpstreamKbps(Integer.parseInt(upstreamKbps));
                streamManager.enable();
            } catch (NumberFormatException e) {
            }
        }

        String upstreamBps = request.param("upstreamBps");
        if (upstreamBps != null) {
            try {
                ((BrowserMobProxy) proxy).setWriteBandwidthLimit(Integer.parseInt(upstreamBps));
            } catch (NumberFormatException e) {
            }
        }

        String downstreamKbps = request.param("downstreamKbps");
        if (downstreamKbps != null) {
            try {
                streamManager.setDownstreamKbps(Integer.parseInt(downstreamKbps));
                streamManager.enable();
            } catch (NumberFormatException e) {
            }
        }

        String downstreamBps = request.param("downstreamBps");
        if (downstreamBps != null) {
            try {
                ((BrowserMobProxy) proxy).setReadBandwidthLimit(Integer.parseInt(downstreamBps));
            } catch (NumberFormatException e) {
            }
        }

        String upstreamMaxKB = request.param("upstreamMaxKB");
        if (upstreamMaxKB != null) {
            try {
                streamManager.setUpstreamMaxKB(Integer.parseInt(upstreamMaxKB));
                streamManager.enable();
            } catch (NumberFormatException e) {
            }
        }
        String downstreamMaxKB = request.param("downstreamMaxKB");
        if (downstreamMaxKB != null) {
            try {
                streamManager.setDownstreamMaxKB(Integer.parseInt(downstreamMaxKB));
                streamManager.enable();
            } catch (NumberFormatException e) {
            }
        }
        String latency = request.param("latency");
        if (latency != null) {
            try {
                streamManager.setLatency(Integer.parseInt(latency));
                streamManager.enable();
            } catch (NumberFormatException e) {
            }
        }
        String payloadPercentage = request.param("payloadPercentage");
        if (payloadPercentage != null) {
            try {
                streamManager.setPayloadPercentage(Integer.parseInt(payloadPercentage));
            } catch (NumberFormatException e) {
            }
        }
        String maxBitsPerSecond = request.param("maxBitsPerSecond");
        if (maxBitsPerSecond != null) {
            try {
                streamManager.setMaxBitsPerSecondThreshold(Integer.parseInt(maxBitsPerSecond));
            } catch (NumberFormatException e) {
            }
        }
        String enable = request.param("enable");
        if (enable != null) {
            if (Boolean.parseBoolean(enable)) {
                streamManager.enable();
            } else {
                streamManager.disable();
            }
        }
        return Reply.saying().ok();
    }

    @Get
    @At("/:port/limit")
    public Reply<?> getLimits(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }
        return Reply.with(new BandwidthLimitDescriptor(proxy.getStreamManager())).as(Json.class);
    }

    @Put
    @At("/:port/timeout")
    public Reply<?> timeout(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String requestTimeout = request.param("requestTimeout");
        if (requestTimeout != null) {
            try {
                proxy.setRequestTimeout(Integer.parseInt(requestTimeout));
            } catch (NumberFormatException e) {
            }
        }
        String readTimeout = request.param("readTimeout");
        if (readTimeout != null) {
            try {
                proxy.setSocketOperationTimeout(Integer.parseInt(readTimeout));
            } catch (NumberFormatException e) {
            }
        }
        String connectionTimeout = request.param("connectionTimeout");
        if (connectionTimeout != null) {
            try {
                proxy.setConnectionTimeout(Integer.parseInt(connectionTimeout));
            } catch (NumberFormatException e) {
            }
        }
        String dnsCacheTimeout = request.param("dnsCacheTimeout");
        if (dnsCacheTimeout != null) {
            try {
                proxy.setDNSCacheTimeout(Integer.parseInt(dnsCacheTimeout));
            } catch (NumberFormatException e) {
            }
        }
        return Reply.saying().ok();
    }

    @Delete
    @At("/:port")
    public Reply<?> delete(@Named("port") int port) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        proxyManager.delete(port);
        return Reply.saying().ok();
    }

    @Post
    @At("/:port/hosts")
    public Reply<?> remapHosts(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        @SuppressWarnings("unchecked") Map<String, String> headers = request.read(Map.class).as(Json.class);

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            proxy.remapHost(key, value);
            proxy.setDNSCacheTimeout(0);
            proxy.clearDNSCache();
        }

        return Reply.saying().ok();
    }


    @Put
    @At("/:port/wait")
    public Reply<?> wait(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String quietPeriodInMs = request.param("quietPeriodInMs");
        String timeoutInMs = request.param("timeoutInMs");
        proxy.waitForNetworkTrafficToStop(Integer.parseInt(quietPeriodInMs), Integer.parseInt(timeoutInMs));
        return Reply.saying().ok();
    }

    @Delete
    @At("/:port/dns/cache")
    public Reply<?> clearDnsCache(@Named("port") int port) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        proxy.clearDNSCache();
        return Reply.saying().ok();
    }

    @Put
    @At("/:port/rewrite")
    public Reply<?> rewriteUrl(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String match = request.param("matchRegex");
        String replace = request.param("replace");
        proxy.rewriteUrl(match, replace);
        return Reply.saying().ok();
    }

    @Delete
    @At("/:port/rewrite")
    public Reply<?> clearRewriteRules(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        proxy.clearRewriteRules();
        return Reply.saying().ok();
    }

    @Put
    @At("/:port/retry")
    public Reply<?> retryCount(@Named("port") int port, Request<String> request) {
        LegacyProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String count = request.param("retrycount");
        proxy.setRetryCount(Integer.parseInt(count));
        return Reply.saying().ok();
    }

    private int parseResponseCode(String response) {
        int responseCode = 200;
        if (response != null) {
            try {
                responseCode = Integer.parseInt(response);
            } catch (NumberFormatException e) {
            }
        }
        return responseCode;
    }

    public static class ProxyDescriptor {
        private int port;

        public ProxyDescriptor() {
        }

        public ProxyDescriptor(int port) {
            this.port = port;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class ProxyListDescriptor {
        private Collection<ProxyDescriptor> proxyList;
        private int numberOfProxies;

        public ProxyListDescriptor() {
        }

        public ProxyListDescriptor(Collection<ProxyDescriptor> proxyList) {
            this.proxyList = proxyList;
            this.numberOfProxies = proxyList.size();
        }

        public Collection<ProxyDescriptor> getProxyList() {
            return proxyList;
        }

        public void setProxyList(Collection<ProxyDescriptor> proxyList) {
            this.proxyList = proxyList;
        }

        public int getNumberOfProxies() {
            return numberOfProxies;
        }

        public ProxyListDescriptor setNumberOfProxies(int numberOfProxies) {
            this.numberOfProxies = numberOfProxies;
            return this;
        }
    }

    public static class BandwidthLimitDescriptor {
        private long maxUpstreamKB;
        private long remainingUpstreamKB;
        private long maxDownstreamKB;
        private long remainingDownstreamKB;

        public BandwidthLimitDescriptor() {
        }

        public BandwidthLimitDescriptor(StreamManager manager) {
            this.maxDownstreamKB = manager.getMaxDownstreamKB();
            this.remainingDownstreamKB = manager.getRemainingDownstreamKB();
            this.maxUpstreamKB = manager.getMaxUpstreamKB();
            this.remainingUpstreamKB = manager.getRemainingUpstreamKB();
        }

        public long getMaxUpstreamKB() {
            return maxUpstreamKB;
        }

        public void setMaxUpstreamKB(long maxUpstreamKB) {
            this.maxUpstreamKB = maxUpstreamKB;
        }

        public long getRemainingUpstreamKB() {
            return remainingUpstreamKB;
        }

        public void setRemainingUpstreamKB(long remainingUpstreamKB) {
            this.remainingUpstreamKB = remainingUpstreamKB;
        }

        public long getMaxDownstreamKB() {
            return maxDownstreamKB;
        }

        public void setMaxDownstreamKB(long maxDownstreamKB) {
            this.maxDownstreamKB = maxDownstreamKB;
        }

        public long getRemainingDownstreamKB() {
            return remainingDownstreamKB;
        }

        public void setRemainingDownstreamKB(long remainingDownstreamKB) {
            this.remainingDownstreamKB = remainingDownstreamKB;
        }
    }

    private String getEntityBodyFromRequest(Request<String> request) throws IOException {
        String contentTypeHeader = request.header("Content-Type");
        Charset charset = null;
        try {
            charset = BrowserMobHttpUtil.readCharsetInContentTypeHeader(contentTypeHeader);
        } catch (UnsupportedCharsetException e) {
            java.nio.charset.UnsupportedCharsetException cause = e.getUnsupportedCharsetExceptionCause();
            LOG.error("Character set declared in Content-Type header is not supported. Content-Type header: {}", contentTypeHeader, cause);

            throw cause;
        }

        if (charset == null) {
            charset = BrowserMobHttpUtil.DEFAULT_HTTP_CHARSET;
        }

        ByteArrayOutputStream entityBodyBytes = new ByteArrayOutputStream();
        request.readTo(entityBodyBytes);

        return new String(entityBodyBytes.toByteArray(), charset);
    }

}
