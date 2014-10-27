package net.lightbody.bmp.proxy;

import org.openqa.jetty.http.HttpContext;
import org.openqa.jetty.http.HttpListener;
import org.openqa.jetty.http.SocketListener;
import org.openqa.jetty.http.handler.ResourceHandler;
import org.openqa.jetty.jetty.Server;
import org.openqa.jetty.jetty.servlet.ServletHttpContext;
import org.openqa.jetty.util.InetAddrPort;
import org.openqa.jetty.util.Resource;

import javax.servlet.http.HttpServlet;

public class DummyServer {
    private int port;
    private Server server = new Server();
    private ResourceHandler handler;

    public DummyServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        HttpListener listener = new SocketListener(new InetAddrPort(port));
        
        server.addListener(listener);
        addServlet("/jsonrpc/", JsonServlet.class);
        addServlet("/cookie/", SetCookieServlet.class);
        addServlet("/echo/", EchoServlet.class);

        HttpContext context = new HttpContext();
        context.setContextPath("/");
        context.setBaseResource(Resource.newResource("src/test/dummy-server"));
        server.addContext(context);
        handler = new ResourceHandler();
        context.addHandler(handler);

        server.start();
    }

    private void addServlet(String path, Class<? extends HttpServlet> servletClass) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        ServletHttpContext servletContext = new ServletHttpContext();
        servletContext.setContextPath(path);
        servletContext.addServlet("/", servletClass.getName());
        server.addContext(servletContext);
    }

    public ResourceHandler getHandler() {
        return handler;
    }

    public void stop() throws InterruptedException {
        server.stop();
    }

}
