package net.lightbody.bmp.proxy;

import junit.framework.Assert;
import net.lightbody.bmp.core.har.*;
import net.lightbody.bmp.core.util.ThreadUtils;
import net.lightbody.bmp.proxy.http.BrowserMobHttpRequest;
import net.lightbody.bmp.proxy.http.BrowserMobHttpResponse;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.http.ResponseInterceptor;
import net.lightbody.bmp.proxy.util.IOUtils;
import net.lightbody.bmp.proxy.util.Log;
import org.apache.http.HttpEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.junit.After;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

public class MailingListIssuesTest extends DummyServerTest {
    private static final Log LOG = new Log();

    private final String DUMMY_SERVER_BASE_URL = "http://127.0.0.1:" + DUMMY_SERVER_PORT;
    private final String DUMMY_SERVER_A_TXT = DUMMY_SERVER_BASE_URL + "/a.txt";

    private WebDriver driver;

    @After
    public void shutdownWebDriver() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    public void testThatInterceptorIsCalled() throws IOException, InterruptedException {
        final boolean[] interceptorHit = {false};
        proxy.addRequestInterceptor(new RequestInterceptor() {
            @Override
            public void process(BrowserMobHttpRequest request, Har har) {
                interceptorHit[0] = true;
            }
        });

        String body = IOUtils.readFully(client.execute(new HttpGet(DUMMY_SERVER_A_TXT)).getEntity().getContent());

        Assert.assertTrue(body.contains("this is a.txt"));
        Assert.assertTrue(interceptorHit[0]);
    }

    @Test
    public void testThatInterceptorCanCaptureCallingIpAddress() throws IOException, InterruptedException {
        final String[] remoteHost = {null};
        proxy.addRequestInterceptor(new RequestInterceptor() {
            @Override
            public void process(BrowserMobHttpRequest request, Har har) {
                remoteHost[0] = request.getProxyRequest().getRemoteHost();
            }
        });

        String body = IOUtils.readFully(client.execute(new HttpGet(DUMMY_SERVER_A_TXT)).getEntity().getContent());

        Assert.assertTrue(body.contains("this is a.txt"));
        Assert.assertEquals("Remote host incorrect", "127.0.0.1", remoteHost[0]);
    }

    @Test
    public void testThatWeCanChangeTheUserAgent() throws IOException, InterruptedException {
        proxy.addRequestInterceptor(new RequestInterceptor() {
            @Override
            public void process(BrowserMobHttpRequest request, Har har) {
                request.getMethod().removeHeaders("User-Agent");
                request.getMethod().addHeader("User-Agent", "Bananabot/1.0");
            }
        });

        String body = IOUtils.readFully(client.execute(new HttpGet(DUMMY_SERVER_A_TXT)).getEntity().getContent());

        Assert.assertTrue(body.contains("this is a.txt"));
    }

    @Test
    public void testThatInterceptorsCanRewriteUrls() throws IOException, InterruptedException {
        proxy.addRequestInterceptor(new RequestInterceptor() {
            @Override
            public void process(BrowserMobHttpRequest request, Har har) {
                try {
                    request.getMethod().setURI(new URI(DUMMY_SERVER_BASE_URL + "/b.txt"));
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        });

        String body = IOUtils.readFully(client.execute(new HttpGet(DUMMY_SERVER_A_TXT)).getEntity().getContent());

        Assert.assertTrue(body.contains("this is b.txt"));
    }

    @Test
    public void testThatInterceptorsCanReadResponseBodies() throws IOException, InterruptedException {
        final String[] interceptedBody = {null};

        proxy.setCaptureContent(true);
        proxy.addResponseInterceptor(new ResponseInterceptor() {
            @Override
            public void process(BrowserMobHttpResponse response, Har har) {
                interceptedBody[0] = response.getEntry().getResponse().getContent().getText();
            }
        });

        String body = IOUtils.readFully(client.execute(new HttpGet(DUMMY_SERVER_A_TXT)).getEntity().getContent());

        ThreadUtils.waitFor(new ThreadUtils.WaitCondition() {
            @Override
            public boolean checkCondition(long elapsedTimeInMs) {
                return interceptedBody[0] != null;
            }
        }, TimeUnit.SECONDS, 10);

        Assert.assertEquals(interceptedBody[0], body);
    }

    @Test
    public void testThatProxyCanCaptureBodyInHar() throws IOException, InterruptedException {
        proxy.setCaptureContent(true);
        proxy.newHar("Test");

        String body = IOUtils.readFully(client.execute(new HttpGet(DUMMY_SERVER_A_TXT)).getEntity().getContent());
        System.out.println("Done with request");

        Assert.assertTrue(body.contains("this is a.txt"));

        Har har = proxy.getHar();
        Assert.assertNotNull("Har is null", har);
        HarLog log = har.getLog();
        Assert.assertNotNull("Log is null", log);
        List<HarEntry> entries = log.getEntries();
        Assert.assertNotNull("Entries are null", entries);
        HarEntry entry = entries.get(0);
        Assert.assertNotNull("No entry found", entry);
        HarResponse response = entry.getResponse();
        Assert.assertNotNull("Response is null", response);
        HarContent content = response.getContent();
        Assert.assertNotNull("Content is null", content);
        String mime = content.getMimeType();
        Assert.assertEquals("Mime not matched", "text/plain", mime);
        String text = content.getText();
        Assert.assertEquals("Text not matched", "this is a.txt", text);
    }

    @Test
    public void testThatProxyCanCaptureJsonRpc() throws IOException, InterruptedException {
        proxy.setCaptureContent(true);
        proxy.newHar("Test");

        HttpPost post = new HttpPost(DUMMY_SERVER_BASE_URL + "/jsonrpc/");
        HttpEntity entity = new StringEntity("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\",\"params\":{}}");
        post.setEntity(entity);
        post.addHeader("Accept", "application/json-rpc");
        post.addHeader("Content-Type", "application/json; charset=UTF-8");

        String body = IOUtils.readFully(client.execute(post).getEntity().getContent());

        Assert.assertTrue(body.contains("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}"));

        Har har = proxy.getHar();
        Assert.assertNotNull("Har is null", har);
        HarLog log = har.getLog();
        Assert.assertNotNull("Log is null", log);
        List<HarEntry> entries = log.getEntries();
        Assert.assertNotNull("Entries are null", entries);
        HarEntry entry = entries.get(0);
        Assert.assertNotNull("No entry found", entry);
        HarResponse response = entry.getResponse();
        Assert.assertNotNull("Response is null", response);
        HarRequest request = entry.getRequest();
        Assert.assertNotNull("Request is null", request);
        HarPostData postdata = request.getPostData();
        Assert.assertNotNull("PostData is null", postdata);
        Assert.assertTrue(postdata.getText().contains("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\",\"params\":{}}"));
    }

    @Test
    public void testThatTraditionalPostParamsAreCaptured() throws IOException, InterruptedException {
        proxy.setCaptureContent(true);
        proxy.newHar("Test");

        HttpPost post = new HttpPost(DUMMY_SERVER_BASE_URL + "/jsonrpc/");
        post.setEntity(new UrlEncodedFormEntity(Collections.singletonList(new BasicNameValuePair("foo", "bar"))));

        IOUtils.readFully(client.execute(post).getEntity().getContent());

        Har har = proxy.getHar();
        Assert.assertNotNull("Har is null", har);
        HarLog log = har.getLog();
        Assert.assertNotNull("Log is null", log);
        List<HarEntry> entries = log.getEntries();
        Assert.assertNotNull("Entries are null", entries);
        HarEntry entry = entries.get(0);
        Assert.assertNotNull("No entry found", entry);
        HarResponse response = entry.getResponse();
        Assert.assertNotNull("Response is null", response);
        HarRequest request = entry.getRequest();
        Assert.assertNotNull("Request is null", request);
        HarPostData postdata = request.getPostData();
        Assert.assertNotNull("PostData is null", postdata);
        Assert.assertEquals("application/x-www-form-urlencoded", postdata.getMimeType());
        Assert.assertEquals(1, postdata.getParams().size());
        Assert.assertEquals("foo", postdata.getParams().get(0).getName());
        Assert.assertEquals("bar", postdata.getParams().get(0).getValue());
    }

    @Test
    public void testThatImagesAreCapturedAsBase64EncodedContent() throws IOException, InterruptedException {
        proxy.setCaptureContent(true);
        proxy.newHar("Test");

        InputStream is1 = client.execute(new HttpGet(DUMMY_SERVER_BASE_URL + "/c.png")).getEntity().getContent();
        ByteArrayOutputStream o1 = new ByteArrayOutputStream();
        IOUtils.copy(is1, o1);
        ByteArrayOutputStream o2 = new ByteArrayOutputStream();
        IOUtils.copy(new FileInputStream("src/test/dummy-server/c.png"), o2);

        Assert.assertTrue("Image does not match file system", Arrays.equals(o1.toByteArray(), o2.toByteArray()));

        Har har = proxy.getHar();
        Assert.assertNotNull("Har is null", har);
        HarLog log = har.getLog();
        Assert.assertNotNull("Log is null", log);
        List<HarEntry> entries = log.getEntries();
        Assert.assertNotNull("Entries are null", entries);
        HarEntry entry = entries.get(0);
        Assert.assertNotNull("No entry found", entry);
        HarResponse response = entry.getResponse();
        Assert.assertNotNull("Response is null", response);
        HarContent content = response.getContent();
        Assert.assertNotNull("Content is null", content);
        String mime = content.getMimeType();
        Assert.assertEquals("Mime not matched", "image/png", mime);
        String text = content.getText();
        String base64 = "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";
        Assert.assertEquals("Base64 not correct", base64, text);
    }

    @Test
    public void testThatUrlEncodedQueryStringIsParsedCorrecty() throws IOException, InterruptedException {
        proxy.setCaptureContent(true);
        proxy.newHar("Test");

        HttpGet get = new HttpGet(DUMMY_SERVER_A_TXT + "?foo=bar&a=1%262");
        client.execute(get);

        Har har = proxy.getHar();
        Assert.assertNotNull("Har is null", har);
        HarLog log = har.getLog();
        Assert.assertNotNull("Log is null", log);
        List<HarEntry> entries = log.getEntries();
        Assert.assertNotNull("Entries are null", entries);
        HarEntry entry = entries.get(0);
        Assert.assertNotNull("No entry found", entry);
        HarRequest req = entry.getRequest();
        Assert.assertNotNull("No request found", req);
        HarNameValuePair queryStringParam = req.getQueryString().get(1);
        Assert.assertNotNull("No request query string param found", queryStringParam);
        Assert.assertEquals("foo", queryStringParam.getName());
        Assert.assertEquals("bar", queryStringParam.getValue());
        queryStringParam = req.getQueryString().get(0);
        Assert.assertNotNull("No request query string param found", queryStringParam);
        Assert.assertEquals("a", queryStringParam.getName());
        Assert.assertEquals("1&2", queryStringParam.getValue());
    }

    @Test
    public void testThatGzippedContentIsProperlyCapturedInHar() throws IOException, InterruptedException {
        proxy.setCaptureContent(true);
        proxy.newHar("Test");

        // gzip all requests
        dummy.getHandler().setMinGzipLength(1);


        HttpGet get = new HttpGet(DUMMY_SERVER_A_TXT);
        get.addHeader("Accept-Encoding", "gzip");
        String body = IOUtils.readFully(new GZIPInputStream(client.execute(get).getEntity().getContent()));
        System.out.println("Done with request");

        Assert.assertTrue(body.contains("this is a.txt"));

        Har har = proxy.getHar();
        Assert.assertNotNull("Har is null", har);
        HarLog log = har.getLog();
        Assert.assertNotNull("Log is null", log);
        List<HarEntry> entries = log.getEntries();
        Assert.assertNotNull("Entries are null", entries);
        HarEntry entry = entries.get(0);
        Assert.assertNotNull("No entry found", entry);
        HarResponse response = entry.getResponse();
        Assert.assertNotNull("Response is null", response);
        HarContent content = response.getContent();
        Assert.assertNotNull("Content is null", content);
        String mime = content.getMimeType();
        Assert.assertEquals("Mime not matched", "text/plain", mime);
        String text = content.getText();
        Assert.assertEquals("Text not matched", "this is a.txt", text);
    }

    @Test
    @Ignore
    public void testThatInterceptorsCanReadPostParamaters() throws IOException, InterruptedException {

        proxy.setCaptureContent(true);
        proxy.newHar("test");

        final String[] capturedPostData = new String[2];

        proxy.addRequestInterceptor(new RequestInterceptor() {
            @Override
            public void process(BrowserMobHttpRequest request, Har har) {
                capturedPostData[0] = request.getProxyRequest().getParameter("testParam");
            }
        });

        HttpPost post = new HttpPost(DUMMY_SERVER_BASE_URL + "/echo/");
        HttpEntity entity = new StringEntity("testParam=testValue");
        post.setEntity(entity);
        post.addHeader("Content-Type", "application/x-www-form-urlencoded");

        client.execute(post);

        Har har = proxy.getHar();
        HarLog log = har.getLog();
        List<HarEntry> entries = log.getEntries();
        HarEntry entry = entries.get(0);
        HarRequest request = entry.getRequest();
        HarPostData postdata = request.getPostData();
        capturedPostData[1] = postdata.getParams().get(0).getValue();

        System.out.println(capturedPostData[0]);
        System.out.println(capturedPostData[1]);

        boolean postDataCapturedAndLoggedCorrectly = capturedPostData[0].equals(capturedPostData[1]);

        Assert.assertEquals(true, postDataCapturedAndLoggedCorrectly);

    }

    @Test
    public void issue27() throws Exception {
        // see: https://github.com/lightbody/browsermob-proxy/issues/27

        // configure the proxy
        proxy.setCaptureHeaders(true);
        proxy.setCaptureContent(true);

        // get the selenium proxy object
        Proxy seleniumProxy = proxy.seleniumProxy();
        DesiredCapabilities capabilities = new DesiredCapabilities();

        capabilities.setCapability(CapabilityType.PROXY, seleniumProxy);

        // start the browser up and skip if it can't find FireFox.
        try {
            driver = new FirefoxDriver(capabilities);
        } catch (WebDriverException wde) {
            LOG.warn("WebDriverException instantiating FirefoxDriver.  Browser is most likely not installed.  Skipping this test. ", wde);
            Assume.assumeNoException(wde);
        }

        proxy.newHar("assertselenium.com");

        driver.get("http://whatsmyuseragent.com");
        //driver.get("https://google.com");

        // get the HAR data
        Har har = proxy.getHar();

        // make sure something came back in the har
        Assert.assertTrue(!har.getLog().getEntries().isEmpty());

        // show that we can capture the HTML of the root page
        String text = har.getLog().getEntries().get(0).getResponse().getContent().getText();
        Assert.assertTrue(text.contains("<title>Whats My User Agent?</title>"));
    }

    @Test
    public void googleCaSslNotWorkingInFirefox() throws Exception {
        // configure the proxy
        proxy.setCaptureHeaders(true);
        proxy.setCaptureContent(true);

        // get the selenium proxy object
        Proxy seleniumProxy = proxy.seleniumProxy();
        DesiredCapabilities capabilities = new DesiredCapabilities();

        capabilities.setCapability(CapabilityType.PROXY, seleniumProxy);

        // start the browser up and skip if it can't find FireFox.
        try {
            driver = new FirefoxDriver(capabilities);
        } catch (WebDriverException wde) {
            LOG.warn("WebDriverException instantiating FirefoxDriver.  Browser is most likely not installed.  Skipping this test. ", wde);
            Assume.assumeNoException(wde);
        }

        proxy.newHar("Google.ca");

        driver.get("https://www.google.ca/");

        // get the HAR data
        Har har = proxy.getHar();

        // make sure something came back in the har
        Assert.assertTrue(!har.getLog().getEntries().isEmpty());

        // show that we can capture the HTML of the root page
        String text = har.getLog().getEntries().get(0).getResponse().getContent().getText();
        Assert.assertTrue(text.contains("<title>Google</title>"));
    }

}
