package net.lightbody.bmp.proxy.bricks;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.sitebricks.At;
import com.google.sitebricks.client.transport.Json;
import com.google.sitebricks.headless.Reply;
import com.google.sitebricks.headless.Request;
import com.google.sitebricks.headless.Service;
import com.google.sitebricks.http.Delete;
import com.google.sitebricks.http.Get;
import com.google.sitebricks.http.Post;
import com.google.sitebricks.http.Put;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.proxy.ProxyManager;
import net.lightbody.bmp.proxy.ProxyServer;
import net.lightbody.bmp.proxy.http.BrowserMobHttpRequest;
import net.lightbody.bmp.proxy.http.BrowserMobHttpResponse;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.http.ResponseInterceptor;
import net.lightbody.bmp.proxy.util.Log;
import org.java_bandwidthlimiter.StreamManager;

import javax.script.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

@At("/proxy")
@Service
public class ProxyResource extends BaseBrick {
    private static final Log LOG = new Log();

    private ProxyManager proxyManager;

    @Inject
    public ProxyResource(ProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Get
    public Reply<?> getProxies(Request request) throws Exception {
        this.logRequest("GET /proxy");

        Collection<ProxyDescriptor> proxyList = new ArrayList<ProxyDescriptor> ();
        for (ProxyServer proxy : proxyManager.get()) {
            proxyList.add(new ProxyDescriptor(proxy.getPort()));
        }
        return this.wrapSuccess(new ProxyListDescriptor(proxyList));
    }

    @Post
    public Reply<?> newProxy(Request request) throws Exception {
        this.logRequest("POST /proxy");

        String systemProxyHost = System.getProperty("http.proxyHost");
        String systemProxyPort = System.getProperty("http.proxyPort");
        String httpProxy = request.param("httpProxy");
        this.logParam("httpProxy", httpProxy);
        Hashtable<String, String> options = new Hashtable<String, String>();

        // If the upstream proxy is specified via query params that should override any default system level proxy.
        if (httpProxy != null) {
            options.put("httpProxy", httpProxy);
        } else if ((systemProxyHost != null) && (systemProxyPort != null)) {
            options.put("httpProxy", String.format("%s:%s", systemProxyHost, systemProxyPort));
        }

        String paramBindAddr = request.param("bindAddress");
        this.logParam("bindAddress", paramBindAddr);

        Integer paramPort = request.param("port") == null ? null : Integer.parseInt(request.param("port"));
        this.logParam("port", paramPort);
        LOG.fine("POST proxy instance on bindAddress `{}` & port `{}`",
                paramBindAddr, paramPort);

        ProxyServer proxy = proxyManager.create(options, paramPort, paramBindAddr);

        return this.wrapSuccess(new ProxyDescriptor(proxy.getPort()));
    }

    @Get
    @At("/:port/har")
    public Reply<?> getHar(@Named("port") int port) {
        this.logRequest("GET /proxy/{}/har", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        Har har = proxy.getHar();

        return this.wrapSuccess(har);
    }

    @Put
    @At("/:port/har")
    public Reply<?> newHar(@Named("port") int port, Request request) {
        this.logRequest("PUT /proxy/{}/har", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        String initialPageRef = request.param("initialPageRef");
        this.logParam("initialPageRef", initialPageRef);
        Har oldHar = proxy.newHar(initialPageRef);

        String captureHeaders = request.param("captureHeaders");
        this.logParam("captureHeaders", captureHeaders);
        String captureContent = request.param("captureContent");
        this.logParam("captureContent", captureContent);
        String captureBinaryContent = request.param("captureBinaryContent");
        this.logParam("captureBinaryContent", captureBinaryContent);
        proxy.setCaptureHeaders(Boolean.parseBoolean(captureHeaders));
        proxy.setCaptureContent(Boolean.parseBoolean(captureContent));
        proxy.setCaptureBinaryContent(Boolean.parseBoolean(captureBinaryContent));

        if (oldHar != null) {
            return this.wrapSuccess(oldHar);
        } else {
            return this.wrapNoContent();
        }
    }

    @Put
    @At("/:port/har/pageRef")
    public Reply<?> setPage(@Named("port") int port, Request request) {
        this.logRequest("PUT /proxy/{}/har/pageRef", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        String pageRef = request.param("pageRef");
        this.logParam("pageRef", pageRef);
        proxy.newPage(pageRef);

        return this.wrapEmptySuccess();
    }

    @Put
    @At("/:port/blacklist")
    public Reply<?> blacklist(@Named("port") int port, Request request) {
        this.logRequest("PUT /proxy/{}/blacklist", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        String blacklist = request.param("regex");
        this.logParam("regex", blacklist);
        int responseCode = parseResponseCode(request.param("status"));
        this.logParam("status", request.param("status"));
        proxy.blacklistRequests(blacklist, responseCode);

        return this.wrapEmptySuccess();
    }

    @Delete
    @At("/:port/blacklist")
    public Reply<?> clearBlacklist(@Named("port") int port, Request request) {
        this.logRequest("DELETE /proxy/{}/blacklist", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

    	proxy.clearBlacklist();
    	return this.wrapEmptySuccess();
    }

    @Put
    @At("/:port/whitelist")
    public Reply<?> whitelist(@Named("port") int port, Request request) {
        this.logRequest("PUT /proxy/{}/whitelist", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        String regex = request.param("regex");
        this.logParam("regex", regex);
        int responseCode = parseResponseCode(request.param("status"));
        this.logParam("status", request.param("status"));
        proxy.whitelistRequests(regex.split(","), responseCode);

        return this.wrapEmptySuccess();
    }

    @Delete
    @At("/:port/whitelist")
    public Reply<?> clearWhitelist(@Named("port") int port, Request request) {
        this.logRequest("DELETE /proxy/{}/whitelist", port);

    	ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

    	proxy.clearWhitelist();
    	return this.wrapEmptySuccess();
    }

    @Post
    @At("/:port/auth/basic/:domain")
    public Reply<?> autoBasicAuth(@Named("port") int port, @Named("domain") String domain, Request request) {
        this.logRequest("POST /proxy/{}/auth/basic/{}", port, domain);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        Map<String, String> credentials = request.read(HashMap.class).as(Json.class);
        this.logParam("username", credentials.get("username"));
        this.logParam("password", credentials.get("password"));
        proxy.autoBasicAuthorization(domain, credentials.get("username"), credentials.get("password"));

        return this.wrapEmptySuccess();
    }

    @Post
    @At("/:port/headers")
    public Reply<?> updateHeaders(@Named("port") int port, Request request) {
        this.logRequest("POST /proxy/{}/headers", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        Map<String, String> headers = request.read(Map.class).as(Json.class);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            this.logParam(key, value);
            proxy.addHeader(key, value);
        }
        return this.wrapEmptySuccess();
    }

    @Post
    @At("/:port/interceptor/response")
    public Reply<?> addResponseInterceptor(@Named("port") int port, Request request) throws IOException, ScriptException {
        this.logRequest("POST /proxy/{}/interceptor/response", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        request.readTo(baos);

        ScriptEngineManager mgr = new ScriptEngineManager();
        final ScriptEngine engine = mgr.getEngineByName("JavaScript");
        Compilable compilable = (Compilable)  engine;
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
                    LOG.severe("Could not execute JS-based response interceptor", e);
                }
            }
        });

        return this.wrapEmptySuccess();
    }

    @Post
    @At("/:port/interceptor/request")
    public Reply<?> addRequestInterceptor(@Named("port") int port, Request request) throws IOException, ScriptException {
        this.logRequest("POST /proxy/{}/interceptor/request", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        request.readTo(baos);

        ScriptEngineManager mgr = new ScriptEngineManager();
        final ScriptEngine engine = mgr.getEngineByName("JavaScript");
        Compilable compilable = (Compilable)  engine;
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
                    LOG.severe("Could not execute JS-based response interceptor", e);
                }
            }
        });

        return wrapEmptySuccess();
    }

    @Put
    @At("/:port/limit")
    public Reply<?> limit(@Named("port") int port, Request request) {
        this.logRequest("PUT /proxy/{}/limit", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        StreamManager streamManager = proxy.getStreamManager();
        String upstreamKbps = request.param("upstreamKbps");
        this.logParam("upstreamKbps", upstreamKbps);
        if (upstreamKbps != null) {
            try {
                streamManager.setUpstreamKbps(Integer.parseInt(upstreamKbps));
                streamManager.enable();
            } catch (NumberFormatException e) { }
        }
        String downstreamKbps = request.param("downstreamKbps");
        this.logParam("downstreamKbps", downstreamKbps);
        if (downstreamKbps != null) {
            try {
                streamManager.setDownstreamKbps(Integer.parseInt(downstreamKbps));
                streamManager.enable();
            } catch (NumberFormatException e) { }
        }
        String latency = request.param("latency");
        this.logParam("latency", latency);
        if (latency != null) {
            try {
                streamManager.setLatency(Integer.parseInt(latency));
                streamManager.enable();
            } catch (NumberFormatException e) { }
        }
        String payloadPercentage = request.param("payloadPercentage");
        this.logParam("payloadPercentage", payloadPercentage);
        if (payloadPercentage != null) {
            try {
                streamManager.setPayloadPercentage(Integer.parseInt(payloadPercentage));
            } catch (NumberFormatException e) { }
        }
        String maxBitsPerSecond = request.param("maxBitsPerSecond");
        this.logParam("maxBitsPerSecond", maxBitsPerSecond);
        if (maxBitsPerSecond != null) {
            try {
                streamManager.setMaxBitsPerSecondThreshold(Integer.parseInt(maxBitsPerSecond));
            } catch (NumberFormatException e) { }
        }
        String enable = request.param("enable");
        this.logParam("enable", enable);
        if (enable != null) {
            if( Boolean.parseBoolean(enable) ) {
                streamManager.enable();
            } else {
                streamManager.disable();
            }
        }
        return this.wrapEmptySuccess();
    }

    @Put
    @At("/:port/timeout")
    public Reply<?> timeout(@Named("port") int port, Request request) {
        this.logRequest("PUT /proxy/{}/timeout", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        String requestTimeout = request.param("requestTimeout");
        this.logParam("requestTimeout", requestTimeout);
        if (requestTimeout != null) {
            try {
                proxy.setRequestTimeout(Integer.parseInt(requestTimeout));
            } catch (NumberFormatException e) { }
        }
        String readTimeout = request.param("readTimeout");
        this.logParam("readTimeout", readTimeout);
        if (readTimeout != null) {
            try {
                proxy.setSocketOperationTimeout(Integer.parseInt(readTimeout));
            } catch (NumberFormatException e) { }
        }
        String connectionTimeout = request.param("connectionTimeout");
        this.logParam("connectionTimeout", connectionTimeout);
        if (connectionTimeout != null) {
            try {
                proxy.setConnectionTimeout(Integer.parseInt(connectionTimeout));
            } catch (NumberFormatException e) { }
        }
        String dnsCacheTimeout = request.param("dnsCacheTimeout");
        this.logParam("dnsCacheTimeout", dnsCacheTimeout);
        if (dnsCacheTimeout != null) {
            try {
                proxy.setDNSCacheTimeout(Integer.parseInt(dnsCacheTimeout));
            } catch (NumberFormatException e) { }
        }
        return this.wrapEmptySuccess();
    }

    @Delete
    @At("/:port")
    public Reply<?> delete(@Named("port") int port) throws Exception {
        this.logRequest("DELETE /proxy/{}", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        proxyManager.delete(port);
        return this.wrapEmptySuccess();
    }

    @Post
    @At("/:port/hosts")
    public Reply<?> remapHosts(@Named("port") int port, Request request) {
        this.logRequest("POST /proxy/{}/hosts", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        @SuppressWarnings("unchecked") Map<String, String> headers = request.read(Map.class).as(Json.class);

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            this.logParam(key, value);
            proxy.remapHost(key, value);
            proxy.setDNSCacheTimeout(0);
            proxy.clearDNSCache();
        }

        return this.wrapEmptySuccess();
    }


    @Put
    @At("/:port/wait")
    public Reply<?> wait(@Named("port") int port, Request request) {
        this.logRequest("PUT /proxy/{}/wait", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        String quietPeriodInMs = request.param("quietPeriodInMs");
        this.logParam("quietPeriodInMs", quietPeriodInMs);
        String timeoutInMs = request.param("timeoutInMs");
        this.logParam("timeoutInMs", timeoutInMs);
        proxy.waitForNetworkTrafficToStop(Integer.parseInt(quietPeriodInMs), Integer.parseInt(timeoutInMs));
        return this.wrapEmptySuccess();
    }

    @Delete
    @At("/:port/dns/cache")
    public Reply<?> clearDnsCache(@Named("port") int port) throws Exception {
        this.logRequest("DELETE /proxy/{}/dns/cache", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

    	proxy.clearDNSCache();
        return this.wrapEmptySuccess();
    }

    @Put
    @At("/:port/rewrite")
    public Reply<?> rewriteUrl(@Named("port") int port, Request request) {
        this.logRequest("PUT /proxy/{}/rewrite", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        String match = request.param("matchRegex");
        this.logParam("matchRegex", match);
        String replace = request.param("replace");
        this.logParam("replace", replace);
        proxy.rewriteUrl(match, replace);
        return this.wrapEmptySuccess();
    }

    @Delete
    @At("/:port/rewrite")
    public Reply<?> clearRewriteRules(@Named("port") int port, Request request) {
        this.logRequest("DELETE /proxy/{}/rewrite", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

    	proxy.clearRewriteRules();
    	return this.wrapEmptySuccess();
    }

    @Put
    @At("/:port/retry")
    public Reply<?> retryCount(@Named("port") int port, Request request) {
        this.logRequest("PUT /proxy/{}/retry", port);

        ProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return this.wrapNotFound();
        }

        String count = request.param("retrycount");
        this.logParam("retrycount", count);
        proxy.setRetryCount(Integer.parseInt(count));
        return this.wrapEmptySuccess();
    }

    private int parseResponseCode(String response) {
        int responseCode = 200;
        if (response != null) {
            try {
                responseCode = Integer.parseInt(response);
            } catch (NumberFormatException e) { }
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

        public ProxyListDescriptor() {
        }

        public ProxyListDescriptor(Collection<ProxyDescriptor> proxyList) {
            this.proxyList = proxyList;
        }

        public Collection<ProxyDescriptor> getProxyList() {
            return proxyList;
        }

        public void setProxyList(Collection<ProxyDescriptor> proxyList) {
            this.proxyList = proxyList;
        }
    }
}
