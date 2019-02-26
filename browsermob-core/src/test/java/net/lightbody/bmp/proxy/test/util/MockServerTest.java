package net.lightbody.bmp.proxy.test.util;

import org.junit.After;
import org.junit.Before;
import org.mockserver.integration.ClientAndServer;

/**
 * Tests can subclass this to get access to a ClientAndServer instance for creating mock responses.
 */
public class MockServerTest {
    protected ClientAndServer mockServer;
    protected int mockServerPort;

    @Before
    public void setUpMockServer() {
        mockServer = new ClientAndServer(0);
        mockServerPort = mockServer.getLocalPort();
    }

    @After
    public void tearDownMockServer() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }
}
