package net.lightbody.bmp.proxy.test.util;

import net.lightbody.bmp.proxy.test.servlet.EchoPayloadServlet;
import net.lightbody.bmp.proxy.test.servlet.EchoServlet;
import net.lightbody.bmp.proxy.test.servlet.JsonServlet;
import net.lightbody.bmp.proxy.test.servlet.SetCookieServlet;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local server for unit tests. The start() method wires up simple servlets to be used for unit testing. Static resources are served from the
 * classpath at /local-server. By default, no content will be gzipped; call forceGzip() to force gzipping content.
 * <p/>
 * <b>Note:</b> Call getPort() <b>after</b> calling start() to get the port the server is bound to.
 */
public class LocalServer {
    private int port;
    private Server server;

    private static Logger log = LoggerFactory.getLogger(LocalServer.class);

    private static final AtomicBoolean started = new AtomicBoolean(false);

    public void start() {
        server = new Server(0);

        HandlerList handlers = new HandlerList();

        // create a ServletHandler and add unit test servlets to it
        ServletHandler servletHandler = new ServletHandler();

        servletHandler.addServletWithMapping(JsonServlet.class, "/jsonrpc");
        servletHandler.addServletWithMapping(SetCookieServlet.class, "/cookie");
        servletHandler.addServletWithMapping(EchoServlet.class, "/echo");
        servletHandler.addServletWithMapping(EchoPayloadServlet.class, "/echopayload");


        // create a ResourceHandler to serve up static resources from the classpath at /local-server
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setBaseResource(Resource.newClassPathResource("/local-server"));


        // wrap the other handlers in a GzipHandler that does not gzip anything by default
        GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.setMinGzipSize(Integer.MAX_VALUE);
        handlers.addHandler(resourceHandler);
        handlers.addHandler(servletHandler);
        gzipHandler.setHandler(handlers);

        server.setHandler(gzipHandler);

        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException("Could not start local Jetty server for tests", e);
        }

        this.port = server.getURI().getPort();

        started.set(true);
    }

    public int getPort() {
        // simple sanity check to fail fast on incorrect unit tests
        if (!started.get()) {
            throw new IllegalStateException("Cannot get test server port until server is started. Call start() first.");
        }

        return this.port;
    }

    /**
     * Forces the server to gzip all responses (see {@link org.eclipse.jetty.server.handler.gzip.GzipHandler} for response codes that will
     * be gzipped).
     */
    public void forceGzip() {
        GzipHandler gzipHandler = (GzipHandler) server.getHandler();
        gzipHandler.setMinGzipSize(1);
    }

    /**
     * Forces the server to NOT gzip any responses.
     */
    public void disableGzip() {
        GzipHandler gzipHandler = (GzipHandler) server.getHandler();
        gzipHandler.setMinGzipSize(Integer.MAX_VALUE);
    }

    public void stop() {
        try {
            if (server != null) {
                server.stop();
            }
        } catch (Exception e) {
            log.error("Could not stop local Jetty server for tests", e);
        }
    }

}
