package net.lightbody.bmp.proxy.remote;

import com.google.common.base.Joiner;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarLog;
import net.lightbody.bmp.proxy.IProxyServer;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.http.ResponseInterceptor;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.java_bandwidthlimiter.BandwidthLimiter;
import org.java_bandwidthlimiter.IStreamManager;
import org.openqa.selenium.Proxy;

import javax.ws.rs.core.MultivaluedMap;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class ProxyRemoteClient implements IProxyServer, IStreamManager {
    private Log log = LogFactory.getLog(ProxyRemoteClient.class);

    /** The service. */
    private WebResource service;

    /** The mapper. */
    private ObjectMapper mapper = new ObjectMapper();

    private String host;
    private int tempPort;
    private int port;
    private boolean captureHeaders = false;
    private boolean captureContent = true;
    private boolean captureBinaryContent = true;

    /** The Constant PORT_BEGININDEX. */
    public static final int PORT_BEGININDEX = 8;

    /** The Constant PORT_ENDINDEX. */
    public static final int PORT_ENDINDEX = 12;

    public ProxyRemoteClient(String host, int port) {
        this.host = host;
        this.tempPort = port;
    }

    public void start() throws Exception {
        ClientConfig config = new DefaultClientConfig();
        Client client = Client.create(config);
        service = client.resource(host + ":" + tempPort
            + "/proxy");
        ClientResponse response = service.post(ClientResponse.class);
        String responseBody = response.getEntity(String.class);
        int actPort = Integer.parseInt(responseBody.substring(PORT_BEGININDEX,
                PORT_ENDINDEX));
        this.port = actPort;
    }

    public Proxy seleniumProxy() throws UnknownHostException {
        Proxy proxy = new Proxy();
        proxy.setProxyType(Proxy.ProxyType.MANUAL);
        String proxyStr = String.format("%s:%d", getHost().replace("http://", ""), getPort());
        proxy.setHttpProxy(proxyStr);
        proxy.setSslProxy(proxyStr);
        return proxy;
    }

    public void cleanup() {
        log.debug("cleanup: no need to implement this method");
    }

    public void stop() throws Exception {
        service.path(Integer.toString(port)).delete(
                ClientResponse.class);
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public void setPort(int port) {
        log.debug("setPort: no need to implement this method");
    }

    public Har getHar() {
        ClientResponse response = service.path(Integer.toString(getPort()))
                .path("har").get(ClientResponse.class);
        String responseBody = response.getEntity(String.class);
        if (response.getStatus() == HttpStatus.SC_OK) {
            try {
                return mapper.readValue(responseBody, Har.class);
            } catch (JsonParseException e) {
                log.error(e);
            } catch (JsonMappingException e) {
                log.error(e);
            } catch (IOException e) {
                log.error(e);
            }
        }
        return new Har(new HarLog());
    }

    public Har newHar(String initialPageRef) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        if (!initialPageRef.isEmpty()) {
            formData.add("initialPageRef", initialPageRef);
        }
        formData.add("captureHeaders", Boolean.toString(captureHeaders));
        formData.add("captureContent", Boolean.toString(captureContent));
        formData.add("captureBinaryContent", Boolean.toString(captureBinaryContent));
        ClientResponse response = service.path(Integer.toString(getPort()))
                .path("har").put(ClientResponse.class, formData);
        if (response.getStatus() == HttpStatus.SC_OK) {
            String responseBody = response.getEntity(String.class);
            try {
                return mapper.readValue(responseBody, Har.class);
            } catch (JsonParseException e) {
                log.error(e);
            } catch (JsonMappingException e) {
                log.error(e);
            } catch (IOException e) {
                log.error(e);
            }
        }
        return new Har(new HarLog());
    }

    public void newPage(String pageRef) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        if (!pageRef.isEmpty()) {
            formData.add("pageRef", pageRef);
        }
        service.path(Integer.toString(port))
                .path("har").path("pageRef")
                .put(ClientResponse.class, formData);
    }

    public void endPage() {
        log.debug("endPage: no need to implement this method");
    }

    public void setRetryCount(int count) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("retrycount", Integer.toString(count));
        service.path(Integer.toString(port))
                .path("retry").put(ClientResponse.class, formData);
    }

    public void remapHost(String source, String target) {
        String data = "{\"" + source + "\": \"" + target + "\"}";
        service.path(Integer.toString(port))
                .path("hosts").post(ClientResponse.class, data);

    }

    public void addRequestInterceptor(RequestInterceptor interceptor) {
        log.debug("addRequestInterceptor: not yet implemented");
    }

    public void addResponseInterceptor(ResponseInterceptor interceptor) {
        log.debug("addResponseInterceptor: not yet implemented");
    }

    public IStreamManager getStreamManager() {
        return this;
    }

    public void setRequestTimeout(int requestTimeout) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("requestTimeout", Long.toString(requestTimeout));
        service.path(Integer.toString(getPort()))
                .path("timeout").put(ClientResponse.class, formData);
    }

    public void setSocketOperationTimeout(int readTimeout) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("readTimeout", Long.toString(readTimeout));
        service.path(Integer.toString(getPort()))
                .path("timeout").put(ClientResponse.class, formData);
    }

    public void setConnectionTimeout(int connectionTimeout) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("connectionTimeout", Long.toString(connectionTimeout));
        service.path(Integer.toString(port))
                .path("timeout").put(ClientResponse.class, formData);
    }

    public void autoBasicAuthorization(String domain, String username, String password) {
        Map<String, String> auth = new HashMap<String, String>();
        auth.put("username", username);
        auth.put("password", password);
        try {
            service.path(Integer.toString(getPort()))
                    .path("auth").path("basic").path(domain)
                    .post(ClientResponse.class, mapper.writeValueAsString(auth));
        } catch (JsonParseException e) {
            log.error(e);
        } catch (JsonMappingException e) {
            log.error(e);
        } catch (IOException e) {
            log.error(e);
        }
    }

    public void rewriteUrl(String match, String replace) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("matchRegex", match);
        formData.add("replace", replace);
        service.path(Integer.toString(port))
                .path("rewrite").put(ClientResponse.class, formData);
    }

    public void blacklistRequests(String pattern, int responseCode) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("regex", pattern);
        formData.add("status", Integer.toString(responseCode));
        service.path(Integer.toString(port))
                .path("blacklist").put(ClientResponse.class, formData);
    }

    public void whitelistRequests(String[] patterns, int responseCode) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("regex", Joiner.on(",").skipNulls().join(patterns));
        formData.add("status", Integer.toString(responseCode));
        service.path(Integer.toString(port))
                .path("whitelist").put(ClientResponse.class, formData);

    }

    public void addHeader(String name, String value) {
        String data = "{\"" + name + "\": \"" + value + "\"}";
        service.path(Integer.toString(getPort()))
                .path("headers").post(ClientResponse.class, data);
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

    public void clearDNSCache() {
        service.path(Integer.toString(getPort()))
                .path("dns").path("cache").delete(ClientResponse.class);
    }

    public void setDNSCacheTimeout(int timeout) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("dnsCacheTimeout", Long.toString(timeout));
        service.path(Integer.toString(port))
                .path("timeout").put(ClientResponse.class, formData);
    }

    public void waitForNetworkTrafficToStop(long quietPeriodInMs, long timeoutInMs) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("quietPeriodInMs", Long.toString(quietPeriodInMs));
        formData.add("timeoutInMs", Long.toString(timeoutInMs));
        service.path(Integer.toString(port))
                .path("wait").put(ClientResponse.class, formData);
    }

    public void setOptions(Map<String, String> options) {
        if (options.containsKey("httpProxy")) {
            ClientResponse response = service.queryParam("httpProxy",
                    options.get("httpProxy")).post(ClientResponse.class);
            setPort(Integer.parseInt(response.getEntity(String.class).substring(
                    PORT_BEGININDEX, PORT_ENDINDEX)));
        }
    }

    public void setDownstreamKbps(long downstreamKbps) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("downstreamKbps", Long.toString(downstreamKbps));
        service.path(Integer.toString(port))
                .path("limit").put(ClientResponse.class, formData);
    }

    public void setUpstreamKbps(long upstreamKbps) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("upstreamKbps", Long.toString(upstreamKbps));
        service.path(Integer.toString(port))
                .path("limit").put(ClientResponse.class, formData);
    }

    public void setLatency(long latency) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("latency", Long.toString(latency));
        service.path(Integer.toString(port))
                .path("limit").put(ClientResponse.class, formData);
    }

    public void enable() {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("enable", Boolean.toString(true));
        service.path(Integer.toString(port))
                .path("limit").put(ClientResponse.class, formData);
    }

    public void disable() {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("enable", Boolean.toString(false));
        service.path(Integer.toString(port))
                .path("limit").put(ClientResponse.class, formData);
    }

    public void setPayloadPercentage(int payloadPercentage) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("payloadPercentage", Long.toString(payloadPercentage));
        service.path(Integer.toString(port))
                .path("limit").put(ClientResponse.class, formData);
    }

    public void setMaxBitsPerSecondThreshold(long maxBitsPerSecond) {
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("maxBitsPerSecond", Long.toString(maxBitsPerSecond));
        service.path(Integer.toString(port))
                .path("limit").put(ClientResponse.class, formData);
    }

}
