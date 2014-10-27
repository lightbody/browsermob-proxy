package net.lightbody.bmp.proxy;

import net.lightbody.bmp.proxy.http.*;
import org.openqa.jetty.http.*;
import org.openqa.jetty.jetty.Server;
import org.openqa.jetty.util.InetAddrPort;
import org.openqa.jetty.util.URI;
import net.lightbody.bmp.proxy.selenium.SeleniumProxyHandler;
import net.lightbody.bmp.proxy.util.Log;
import org.apache.http.Header;
import org.apache.http.NoHttpResponseException;
import org.apache.http.StatusLine;
import org.apache.http.conn.ConnectTimeoutException;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BrowserMobProxyHandler extends SeleniumProxyHandler {
    private static final Log LOG = new Log();

    private static final int HEADER_BUFFER_DEFAULT = 2;

    private Server jettyServer;
    private int headerBufferMultiplier = HEADER_BUFFER_DEFAULT;
    private BrowserMobHttpClient httpClient;
    protected final Set<SslRelay> sslRelays = new HashSet<SslRelay>();

    public BrowserMobProxyHandler() {
        super(true, "", "", false, false);
        setShutdownLock(new Object());

        // set the tunnel timeout to something larger than the default 30 seconds
        // we're doing this because SSL connections taking longer than this timeout
        // will result in a socket connection close that does NOT get handled by the
        // normal socket connection closing reportError(). Further, it has been seen
        // that Firefox will actually retry the connection, causing very strange
        // behavior observed in case http://browsermob.assistly.com/agent/case/27843
        //
        // You can also reproduce it by simply finding some slow loading SSL site
        // that takes greater than 30 seconds to response.
        //
        // Finally, it should be noted that we're setting this timeout to some value
        // that we anticipate will be larger than any reasonable response time of a
        // real world request. We don't set it to -1 because the underlying ProxyHandler
        // will not use it if it's <= 0. We also don't set it to Long.MAX_VALUE because
        // we don't yet know if this will cause a serious resource drain, so we're
        // going to try something like 5 minutes for now.
        setTunnelTimeoutMs(300000);
    }

    @Override
    public void handleConnect(String pathInContext, String pathParams, HttpRequest request, HttpResponse response) throws HttpException, IOException {
        URI uri = request.getURI();
        String original = uri.toString();
        String host = original;
        String port = null;
        int colon = original.indexOf(':');
        if (colon != -1) {
            host = original.substring(0, colon);
            port = original.substring(colon + 1);
        }
        String altHost = httpClient.remappedHost(host);
        if (altHost != null) {
            if (port != null) {
                uri.setURI(altHost + ":" + port);
            } else {
                uri.setURI(altHost);
            }
        }

        super.handleConnect(pathInContext, pathParams, request, response);
    }

    @Override
    protected void wireUpSslWithCyberVilliansCA(String host, SeleniumProxyHandler.SslRelay listener) {
        List<String> originalHosts = httpClient.originalHosts(host);
        if (originalHosts != null && !originalHosts.isEmpty()) {
            if (originalHosts.size() == 1) {
                host = originalHosts.get(0);
            } else {
                // Warning: this is NASTY, but people rarely even run across this and those that do are solved by this
                // ok, this really isn't legal in real SSL land, but we'll make an exception and just pretend it's a wildcard
                String first = originalHosts.get(0);
                host = "*" + first.substring(first.indexOf('.'));
            }
        }
        super.wireUpSslWithCyberVilliansCA(host, listener);
    }

    @Override
    protected SslRelay getSslRelayOrCreateNew(URI uri, InetAddrPort addrPort, HttpServer server) throws Exception {
        SslRelay relay = super.getSslRelayOrCreateNew(uri, addrPort, server);
        relay.setNukeDirOrFile(null);

        synchronized (sslRelays) {
            sslRelays.add(relay);
        }

        if (!relay.isStarted()) {
            server.addListener(relay);

            startRelayWithPortTollerance(server, relay, 1);
        }

        return relay;
    }

    private void startRelayWithPortTollerance(HttpServer server, SslRelay relay, int tries) throws Exception {
        if (tries >= 5) {
            throw new BindException("Unable to bind to several ports, most recently " + relay.getPort() + ". Giving up");
        }
        try {
            if (server.isStarted()) {
                relay.start();
            } else {
                throw new RuntimeException("Can't start SslRelay: server is not started (perhaps it was just shut down?)");
            }
        } catch (BindException e) {
            // doh - the port is being used up, let's pick a new port
            LOG.info("Unable to bind to port %d, going to try port %d now", relay.getPort(), relay.getPort() + 1);
            relay.setPort(relay.getPort() + 1);
            startRelayWithPortTollerance(server, relay, tries + 1);
        }
    }

    @Override
    protected HttpTunnel newHttpTunnel(HttpRequest httpRequest, HttpResponse httpResponse, InetAddress inetAddress, int i, int i1) throws IOException {
        // we're opening up a new tunnel, so let's make sure that the associated SslRelay (which may or may not be new) has the proper buffer settings
        adjustListenerBuffers();

        return super.newHttpTunnel(httpRequest, httpResponse, inetAddress, i, i1);
    }

    @SuppressWarnings({"unchecked"})
    protected long proxyPlainTextRequest(final URL url, String pathInContext, String pathParams, HttpRequest request, final HttpResponse response) throws IOException {
        try {
            String urlStr = url.toString();

            // We don't want selenium-related showing up in the detailed transaction logs
            if (urlStr.contains("/selenium-server/")) {
                return super.proxyPlainTextRequest(url, pathInContext, pathParams, request, response);
            }

            // we also don't URLs that Firefox always loads on startup showing up, or even wasting bandwidth.
            // so for these we just nuke them right on the spot!
            if (urlStr.startsWith("https://sb-ssl.google.com:443/safebrowsing")
                    || urlStr.startsWith("http://en-us.fxfeeds.mozilla.com/en-US/firefox/headlines.xml")
                    || urlStr.startsWith("http://fxfeeds.mozilla.com/firefox/headlines.xml")
                    || urlStr.startsWith("http://fxfeeds.mozilla.com/en-US/firefox/headlines.xml")
                    || urlStr.startsWith("http://newsrss.bbc.co.uk/rss/newsonline_world_edition/front_page/rss.xml")) {
                // don't even xfer these!
                request.setHandled(true);
                return -1;
            }

            // this request must have come in just as we were shutting down, since there is no more associted http client
            // so let's just handle it like we do any other request we don't know what to do with :)
            if (httpClient == null) {
                // don't even xfer these!
                request.setHandled(true);
                return -1;

                // for debugging purposes, NOT to be used in product!
                // httpClient = new BrowserMobHttpClient(Integer.MAX_VALUE);
                // httpClient.setDecompress(false);
                // httpClient.setFollowRedirects(false);
            }

            BrowserMobHttpRequest httpReq = null;
            if ("GET".equals(request.getMethod())) {
                httpReq = httpClient.newGet(urlStr, request);
            } else if ("POST".equals(request.getMethod())) {
                httpReq = httpClient.newPost(urlStr, request);
            } else if ("PUT".equals(request.getMethod())) {
                httpReq = httpClient.newPut(urlStr, request);
            } else if ("DELETE".equals(request.getMethod())) {
                httpReq = httpClient.newDelete(urlStr, request);
            } else if ("OPTIONS".equals(request.getMethod())) {
                httpReq = httpClient.newOptions(urlStr, request);
            } else if ("HEAD".equals(request.getMethod())) {
                httpReq = httpClient.newHead(urlStr, request);
            } else if ("PATCH".equals(request.getMethod())) {
            	httpReq = httpClient.newPatch(urlStr, request);
            } else {
                LOG.warn("Unexpected request method %s, giving up", request.getMethod());
                request.setHandled(true);
                return -1;
            }

            // copy request headers
            boolean isGet = "GET".equals(request.getMethod());
            boolean hasContent = false;
            Enumeration enm = request.getFieldNames();
            long contentLength = 0;
            while (enm.hasMoreElements()) {
                String hdr = (String) enm.nextElement();

                if (!isGet && HttpFields.__ContentType.equals(hdr)) {
                    hasContent = true;
                }


                Enumeration vals = request.getFieldValues(hdr);
                while (vals.hasMoreElements()) {
                    String val = (String) vals.nextElement();
                    if (val != null) {
                    	
                        if (!isGet && HttpFields.__ContentLength.equals(hdr)) {
                            contentLength = Long.parseLong(request.getField(hdr));
                            if(contentLength > 0) {
                            	hasContent = true;
                            }
                        }  

                        if (!_DontProxyHeaders.containsKey(hdr)) {
                            httpReq.addRequestHeader(hdr, val);
                        }
                    }
                }
            }

            try {
                // do input thang!
                InputStream in = request.getInputStream();
                if (hasContent) {
                    httpReq.setRequestInputStream(in, contentLength);
                }
            } catch (Exception e) {
                LOG.fine(e.getMessage(), e);
            }

            // execute the request
            httpReq.setOutputStream(response.getOutputStream());
            httpReq.setRequestCallback(new RequestCallback() {
                @Override
                public void handleStatusLine(StatusLine statusLine) {
                    response.setStatus(statusLine.getStatusCode());
                    response.setReason(statusLine.getReasonPhrase());
                }

                @Override
                public void handleHeaders(Header[] headers) {
                    for (Header header : headers) {
                        if (reportHeader(header)) {
                            response.addField(header.getName(), header.getValue());
                        }
                    }
                }

                @Override
                public boolean reportHeader(Header header) {
                    // don't pass in things like Transfer-Encoding and other headers that are being masked by the underlying HttpClient impl
                    return !_DontProxyHeaders.containsKey(header.getName()) && !_ProxyAuthHeaders.containsKey(header.getName());
                }

                @Override
                public void reportError(Exception e) {
                    BrowserMobProxyHandler.reportError(e, url, response);
                }
            });

            BrowserMobHttpResponse httpRes = httpReq.execute();

            // ALWAYS mark the request as handled if we actually handled it. Otherwise, Jetty will think non 2xx responses
            // mean it wasn't actually handled, resulting in totally valid 304 Not Modified requests turning in to 404 responses
            // from Jetty. NOT good :(
            request.setHandled(true);
            return httpRes.getEntry().getResponse().getBodySize();
        } catch (BadURIException e) {
            // this is a known error case (see MOB-93)
            LOG.info(e.getMessage());
            BrowserMobProxyHandler.reportError(e, url, response);
            return -1;
        } catch (Exception e) {
            LOG.info("Exception while proxying " + url, e);
            BrowserMobProxyHandler.reportError(e, url, response);
            return -1;
        }
    }

    private static void reportError(Exception e, URL url, HttpResponse response) {
        FirefoxErrorContent error = FirefoxErrorContent.GENERIC;
        if (e instanceof UnknownHostException) {
            error = FirefoxErrorContent.DNS_NOT_FOUND;
        } else if (e instanceof ConnectException) {
            error = FirefoxErrorContent.CONN_FAILURE;
        } else if (e instanceof ConnectTimeoutException) {
            error = FirefoxErrorContent.NET_TIMEOUT;
        } else if (e instanceof NoHttpResponseException) {
            error = FirefoxErrorContent.NET_RESET;
        } else if (e instanceof EOFException) {
            error = FirefoxErrorContent.NET_INTERRUPT;
        } else if (e instanceof IllegalArgumentException && e.getMessage().startsWith("Host name may not be null")){
            error = FirefoxErrorContent.DNS_NOT_FOUND;
        } else if (e instanceof BadURIException){
            error = FirefoxErrorContent.MALFORMED_URI;
        }

        String shortDesc = String.format(error.getShortDesc(), url.getHost());
        String text = String.format(FirefoxErrorConstants.ERROR_PAGE, error.getTitle(), shortDesc, error.getLongDesc());

        e.printStackTrace();

        try {
            response.setStatus(HttpResponse.__502_Bad_Gateway);
            response.setContentLength(text.length());
            response.getOutputStream().write(text.getBytes());
        } catch (IOException e1) {
            LOG.warn("IOException while trying to report an HTTP error");
        }
    }

    public void autoBasicAuthorization(String domain, String username, String password) {
        httpClient.autoBasicAuthorization(domain, username, password);
    }

    public void rewriteUrl(String match, String replace) {
        httpClient.rewriteUrl(match, replace);
    }

    public void remapHost(String source, String target) {
        httpClient.remapHost(source, target);
    }

    public void setJettyServer(Server jettyServer) {
        this.jettyServer = jettyServer;
    }

    public void adjustListenerBuffers(int headerBufferMultiplier) {
        // limit to 10 so there can't be any out of control memory consumption by a rogue script
        if (headerBufferMultiplier > 10) {
            headerBufferMultiplier = 10;
        }

        this.headerBufferMultiplier = headerBufferMultiplier;
        adjustListenerBuffers();
    }

    public void resetListenerBuffers() {
        this.headerBufferMultiplier = HEADER_BUFFER_DEFAULT;
        adjustListenerBuffers();
    }

    public void adjustListenerBuffers() {
        // configure the listeners to have larger buffers. We do this because we've seen cases where the header is
        // too large. Normally this would happen on "meaningless" JS includes for ad networks, but we eventually saw
        // it in a way that caused a Selenium script not to work due to too many headers (see tom.schwenk@musictoday.com)
        HttpListener[] listeners = jettyServer.getListeners();
        for (HttpListener listener : listeners) {
            if (listener instanceof SocketListener) {
                SocketListener sl = (SocketListener) listener;
                if (sl.getBufferReserve() != 512 * headerBufferMultiplier) {
                    sl.setBufferReserve(512 * headerBufferMultiplier);
                }

                if (sl.getBufferSize() != 8192 * headerBufferMultiplier) {
                    sl.setBufferSize(8192 * headerBufferMultiplier);
                }
            }
        }
    }

    public void setHttpClient(BrowserMobHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void cleanup() {
        synchronized (sslRelays) {
            for (SslRelay relay : sslRelays) {
                if (relay.getHttpServer() != null && relay.isStarted()) {
                    relay.getHttpServer().removeListener(relay);
                }
            }

            sslRelays.clear();
        }
    }
}
