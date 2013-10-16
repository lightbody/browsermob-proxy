package net.lightbody.bmp.proxy;

import org.java_bandwidthlimiter.BandwidthLimiter;

import org.java_bandwidthlimiter.IStreamManager;

import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.http.ResponseInterceptor;

import java.net.UnknownHostException;
import java.util.Map;

public interface IProxyServer {

  public abstract void start() throws Exception;

  public abstract org.openqa.selenium.Proxy seleniumProxy() throws UnknownHostException;

  public abstract void cleanup();

  public abstract void stop() throws Exception;

  public abstract int getPort();

  public abstract void setPort(int port);

  public abstract Har getHar();

  public abstract Har newHar(String initialPageRef);

  public abstract void newPage(String pageRef);

  public abstract void endPage();

  public abstract void setRetryCount(int count);

  public abstract void remapHost(String source, String target);

  public abstract void addRequestInterceptor(RequestInterceptor interceptor);

  public abstract void addResponseInterceptor(ResponseInterceptor interceptor);

  public abstract IStreamManager getStreamManager();

  public abstract void setRequestTimeout(int requestTimeout);

  public abstract void setSocketOperationTimeout(int readTimeout);

  public abstract void setConnectionTimeout(int connectionTimeout);

  public abstract void autoBasicAuthorization(String domain, String username, String password);

  public abstract void rewriteUrl(String match, String replace);

  public abstract void blacklistRequests(String pattern, int responseCode);

  public abstract void whitelistRequests(String[] patterns, int responseCode);

  public abstract void addHeader(String name, String value);

  public abstract void setCaptureHeaders(boolean captureHeaders);

  public abstract void setCaptureContent(boolean captureContent);

  public abstract void setCaptureBinaryContent(boolean captureBinaryContent);

  public abstract void clearDNSCache();

  public abstract void setDNSCacheTimeout(int timeout);

  public abstract void waitForNetworkTrafficToStop(long quietPeriodInMs, long timeoutInMs);

  public abstract void setOptions(Map<String, String> options);

}
