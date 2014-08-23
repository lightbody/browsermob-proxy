package net.lightbody.bmp.proxy;

import org.junit.Assert;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.proxy.http.BrowserMobHttpRequest;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class RepeatableInputStreamTest extends DummyServerTest{

    @Test
    public void test()
            throws UnsupportedEncodingException {
        TestRequestInterceptor testRequestInterceptor = new TestRequestInterceptor();

        proxy.addRequestInterceptor(testRequestInterceptor);

        HttpPost post = new HttpPost("http://127.0.0.1:8080/jsonrpc/");
        HttpEntity entity = new StringEntity("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\",\"params\":{}}");
        post.setEntity(entity);
        post.addHeader("Accept", "application/json-rpc");
        post.addHeader("Content-Type", "application/json; charset=UTF-8");

        HttpResponse response = null;
        try {
            response = client.execute(post);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        } finally {

            EntityUtils.consumeQuietly(response.getEntity());
        }

        Assert.assertTrue(response.getStatusLine().getStatusCode() == 200);
        Assert.assertNotNull(testRequestInterceptor.getBrowserMobHttpRequest());
        Assert.assertNotNull(testRequestInterceptor.getBrowserMobHttpRequest().getInputStreamEntity());
        Assert.assertTrue(testRequestInterceptor.getBrowserMobHttpRequest().getInputStreamEntity().isRepeatable());
    }

    class TestRequestInterceptor implements RequestInterceptor{
        BrowserMobHttpRequest _browserMobHttpRequest;

        public BrowserMobHttpRequest getBrowserMobHttpRequest(){
            return _browserMobHttpRequest;
        }

        @Override
        public void process(BrowserMobHttpRequest request, Har har) {
            _browserMobHttpRequest = request;
        }
    }
}
