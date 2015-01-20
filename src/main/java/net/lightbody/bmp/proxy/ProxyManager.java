package net.lightbody.bmp.proxy;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.lightbody.bmp.proxy.util.ExpirableMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ProxyManager {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyManager.class);

    private int lastPort;
    private final int minPort; 
    private final int maxPort;
    private final Provider<ProxyServer> proxyServerProvider;
    private final ConcurrentMap<Integer, ProxyServer> proxies;

    @Inject
    public ProxyManager(Provider<ProxyServer> proxyServerProvider, @Named("minPort") Integer minPort, @Named("maxPort") Integer maxPort, @Named("ttl") Integer ttl) {
        this.proxyServerProvider = proxyServerProvider;
        this.minPort = minPort;
        this.maxPort = maxPort;
        this.lastPort = maxPort; 
        this.proxies = ttl > 0 ?                
                new ExpirableMap<Integer, ProxyServer>(ttl, new ExpirableMap.OnExpire<ProxyServer>(){
                    @Override
                    public void run(ProxyServer proxy) {
                        try {
                            LOG.debug("Expiring ProxyServer `{}`...", proxy.getPort());
                            proxy.stop();
                        } catch (Exception ex) {
                            LOG.warn("Error while stopping an expired proxy", ex);
                        }
                    }
                }) : 
                new ConcurrentHashMap<Integer, ProxyServer>();    
    }

    public ProxyServer create(Map<String, String> options, Integer port, String bindAddr) {
        LOG.debug("Instantiate ProxyServer...");
        ProxyServer proxy = proxyServerProvider.get();
        
        LOG.debug("Apply options `{}` to new ProxyServer...", options);
        proxy.setOptions(options);                        
        
        if (bindAddr != null) {            
            LOG.debug("Bind ProxyServer to `{}`...", bindAddr);
            InetAddress inetAddress;
            try {
            	inetAddress = InetAddress.getByName(bindAddr);
            } catch (UnknownHostException e) {
            	LOG.error("Unable to bind proxy to address: " + bindAddr + "; proxy will not be created.", e);
            	
            	throw new RuntimeException("Unable to bind proxy to address: ", e);
            }
            
            proxy.setLocalHost(inetAddress);
        }
        
        if (port != null) {                        
            return startProxy(proxy, port);            
        }                
        
        while(proxies.size() <= maxPort-minPort){
            LOG.debug("Use next available port for new ProxyServer...");
            port = nextPort();                        
            try{
                return startProxy(proxy, port);                
            }catch(ProxyExistsException ex){
                LOG.debug("Proxy already exists at port {}", port);
            }
        }
        throw new ProxyPortsExhaustedException();    
    }

    public ProxyServer create(Map<String, String> options, Integer port) {
        return create(options, port, null);
    }

    public ProxyServer create(Map<String, String> options) {
        return create(options, null, null);
    }

    public ProxyServer get(int port) {
        return proxies.get(port);
    }
    
    private ProxyServer startProxy(ProxyServer proxy, int port) {
        proxy.setPort(port);
        ProxyServer old = proxies.putIfAbsent(port, proxy);
        if(old != null){
            LOG.info("Proxy already exists at port {}", port);
            throw new ProxyExistsException(port);
        }
        try{
            proxy.start();
            return proxy;
        }catch(RuntimeException ex){
            proxies.remove(port);
            try{
                proxy.stop();
            }catch(RuntimeException ex2){
                LOG.warn("Encountered exception while stopping proxy", ex2);
            }                
            throw ex;
        }
    }
    
    private synchronized int nextPort(){
        return lastPort < maxPort? ++lastPort : (lastPort = minPort); 
    }

    public Collection<ProxyServer> get() {
        return proxies.values();
    }

    public void delete(int port) {
        ProxyServer proxy = proxies.remove(port);
        proxy.stop();
    }
    
}
