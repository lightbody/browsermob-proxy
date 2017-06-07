package net.lightbody.bmp.proxy;

import net.lightbody.bmp.proxy.test.util.ProxyServerTest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class SslTest extends ProxyServerTest {
    @Test
    public void testGoogleCa() throws Exception {
        get("https://www.google.ca/");
    }

    @Test
    public void testFidelity() throws Exception {
        get("https://www.fidelity.com/");
    }

    @Test
    public void testSalesforce() throws Exception {
        get("https://login.salesforce.com/");
    }

    @Test
    public void testNewRelic() throws Exception {
        // see https://github.com/webmetrics/browsermob-proxy/issues/105
        proxy.remapHost("foo.newrelic.com", "login.newrelic.com");
        proxy.remapHost("bar.newrelic.com", "login.newrelic.com");
        get("https://foo.newrelic.com/");
        get("https://bar.newrelic.com/");
        get("https://login.newrelic.com/");
    }

    private void get(String url) throws IOException {
        HttpGet get = new HttpGet(url);
        CloseableHttpResponse response = client.execute(get);
        EntityUtils.consumeQuietly(response.getEntity());
        Assert.assertEquals("Expected 200 when fetching " + url, 200, response.getStatusLine().getStatusCode());
    }
}
