package net.lightbody.bmp.proxy;

import org.junit.After;
import org.junit.Before;

public abstract class DummyServerTest extends ProxyServerTest {
    protected final int DUMMY_SERVER_PORT = 8080;
    protected DummyServer dummy = new DummyServer(DUMMY_SERVER_PORT);

    @Before
    public void startServer() throws Exception {
        dummy.start();
        super.startServer();
    }

    @After
    public void stopServer() throws Exception {
        super.stopServer();
        dummy.stop();
    }

}
