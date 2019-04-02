package net.lightbody.bmp.proxy;

import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarLog;
import net.lightbody.bmp.core.har.HarPostData;
import net.lightbody.bmp.core.har.HarRequest;
import net.lightbody.bmp.core.util.ThreadUtils;
import net.lightbody.bmp.proxy.http.BrowserMobHttpRequest;
import net.lightbody.bmp.proxy.http.BrowserMobHttpResponse;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.http.ResponseInterceptor;
import net.lightbody.bmp.proxy.test.util.LocalServerTest;
import net.lightbody.bmp.proxy.util.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assume.assumeFalse;

public class MailingListIssuesTest extends LocalServerTest {
    @Test
    public void testThatInterceptorIsCalled() throws IOException, InterruptedException {
        assumeFalse(Boolean.getBoolean("bmp.use.littleproxy"));

        final boolean[] interceptorHit = {false};
        proxy.addRequestInterceptor((RequestInterceptor) (request, har) -> interceptorHit[0] = true);

        String body = IOUtils.toStringAndClose(client.execute(new HttpGet(getLocalServerHostnameAndPort() + "/a.txt")).getEntity().getContent());

        Assert.assertTrue(body.contains("this is a.txt"));
        Assert.assertTrue(interceptorHit[0]);
    }

    @Test
    public void testThatInterceptorCanCaptureCallingIpAddress() throws IOException, InterruptedException {
        assumeFalse(Boolean.getBoolean("bmp.use.littleproxy"));

        final String[] remoteHost = {null};
        proxy.addRequestInterceptor(new RequestInterceptor() {
            @Override
            public void process(BrowserMobHttpRequest request, Har har) {
                remoteHost[0] = request.getProxyRequest().getRemoteHost();
            }
        });

        String body = IOUtils.toStringAndClose(client.execute(new HttpGet(getLocalServerHostnameAndPort() + "/a.txt")).getEntity().getContent());

        Assert.assertTrue(body.contains("this is a.txt"));
        Assert.assertEquals("Remote host incorrect", "127.0.0.1", remoteHost[0]);
    }

    @Test
    public void testThatWeCanChangeTheUserAgent() throws IOException, InterruptedException {
        assumeFalse(Boolean.getBoolean("bmp.use.littleproxy"));

        proxy.addRequestInterceptor(new RequestInterceptor() {
            @Override
            public void process(BrowserMobHttpRequest request, Har har) {
                request.getMethod().removeHeaders("User-Agent");
                request.getMethod().addHeader("User-Agent", "Bananabot/1.0");
            }
        });

        String body = IOUtils.toStringAndClose(client.execute(new HttpGet(getLocalServerHostnameAndPort() + "/a.txt")).getEntity().getContent());

        Assert.assertTrue(body.contains("this is a.txt"));
    }

    @Test
    public void testThatInterceptorsCanRewriteUrls() throws IOException, InterruptedException {
        assumeFalse(Boolean.getBoolean("bmp.use.littleproxy"));

        proxy.addRequestInterceptor(new RequestInterceptor() {
            @Override
            public void process(BrowserMobHttpRequest request, Har har) {
                try {
                    request.getMethod().setURI(new URI(getLocalServerHostnameAndPort() + "/b.txt"));
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        });

        String body = IOUtils.toStringAndClose(client.execute(new HttpGet(getLocalServerHostnameAndPort() + "/a.txt")).getEntity().getContent());

        Assert.assertTrue(body.contains("this is b.txt"));
    }

    @Test
    public void testThatInterceptorsCanReadResponseBodies() throws IOException, InterruptedException {
        assumeFalse(Boolean.getBoolean("bmp.use.littleproxy"));

        final String[] interceptedBody = {null};

        proxy.setCaptureContent(true);
        proxy.addResponseInterceptor(new ResponseInterceptor() {
            @Override
            public void process(BrowserMobHttpResponse response, Har har) {
                interceptedBody[0] = response.getEntry().getResponse().getContent().getText();
            }
        });

        String body = IOUtils.toStringAndClose(client.execute(new HttpGet(getLocalServerHostnameAndPort() + "/a.txt")).getEntity().getContent());

        ThreadUtils.pollForCondition(new ThreadUtils.WaitCondition() {
            @Override
            public boolean checkCondition() {
                return interceptedBody[0] != null;
            }
        }, 10, TimeUnit.SECONDS);

        Assert.assertEquals(interceptedBody[0], body);
    }

    // @Ignoring this test because accessing the HttpRequest from the interceptor causes the connection to hang
    @Test
    @Ignore
    public void testThatInterceptorsCanReadPostParamaters() throws IOException {
        assumeFalse(Boolean.getBoolean("bmp.use.littleproxy"));

        proxy.setCaptureContent(true);
        proxy.newHar("testThatInterceptorsCanReadPostParamaters");

        final String[] capturedPostData = new String[2];

        proxy.addRequestInterceptor(new RequestInterceptor() {
            @Override
            public void process(BrowserMobHttpRequest request, Har har) {
                capturedPostData[0] = request.getProxyRequest().getParameter("testParam");
            }
        });

        HttpPost post = new HttpPost(getLocalServerHostnameAndPort() + "/echo");
        HttpEntity entity = new StringEntity("testParam=testValue");
        post.setEntity(entity);
        post.addHeader("Content-Type", "application/x-www-form-urlencoded");

        EntityUtils.consumeQuietly(client.execute(post).getEntity());

        Har har = proxy.getHar();
        HarLog log = har.getLog();
        List<HarEntry> entries = log.getEntries();
        HarEntry entry = entries.get(0);
        HarRequest request = entry.getRequest();
        HarPostData postdata = request.getPostData();
        capturedPostData[1] = postdata.getParams().get(0).getValue();

        Assert.assertNotNull("Interceptor POST data was null", capturedPostData[0]);
        Assert.assertNotNull("HAR POST data was null", capturedPostData[1]);
        Assert.assertEquals("POST param from interceptor does not match POST param captured in HAR", capturedPostData[1], capturedPostData[0]);
    }

}
