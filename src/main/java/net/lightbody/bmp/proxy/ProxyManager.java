package net.lightbody.bmp.proxy;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.lightbody.bmp.proxy.util.Log;

@Singleton
public class ProxyManager {
    private static final Log LOG = new Log();
    
    private AtomicInteger portCounter = new AtomicInteger(9090);
    private Provider<ProxyServer> proxyServerProvider;
    private Map<Integer, ProxyServer> proxies = new ConcurrentHashMap<Integer, ProxyServer>();

    @Inject
    public ProxyManager(Provider<ProxyServer> proxyServerProvider) {
        this.proxyServerProvider = proxyServerProvider;
    }

    public ProxyServer create (Map<String, String> options, Integer port, String bindAddr) throws Exception
    {
        LOG.fine("Instantiate ProxyServer...");
        ProxyServer proxy = proxyServerProvider.get();
        
        if (bindAddr != null)
        {
            LOG.fine("Bind ProxyServer to `{}`...", bindAddr);
            proxy.setLocalHost(InetAddress.getByName(bindAddr));
        }
        
        if (port != null)
        {
            proxy.setPort(port);
        } else
        {
            LOG.fine("Use next available port for new ProxyServer...");
            proxy.setPort(portCounter.incrementAndGet());
        }
        
        proxy.start();
        LOG.fine("Apply options `{}` to new ProxyServer...", options);
        proxy.setOptions(options);
        proxies.put(proxy.getPort(), proxy);
        return proxy;
    }

    public ProxyServer create(Map<String, String> options, Integer port) throws Exception {
        return create(options, port, null);
    }

    public ProxyServer create(Map<String, String> options) throws Exception {
        return create(options, null, null);
    }

    public ProxyServer get(int port) {
        return proxies.get(port);
    }

    public void delete(int port) throws Exception {
        ProxyServer proxy = proxies.remove(port);
        proxy.stop();
    }
}
