package net.lightbody.bmp.proxy.util;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ExpirableMap<K,V> extends ConcurrentHashMap<K,V>{
    public final static int DEFAULT_CHECK_INTERVAL = 10*60;
    public final static int DEFAULT_TTL = 30*60;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    return t;
                }
            }
    );
    private final long ttl;    
    private final Map<K, Long> expires;
    private final OnExpire<V> onExpire;

    public ExpirableMap(int ttl, int checkInterval, OnExpire<V> onExpire) {
        this.ttl = ttl*1000;
        this.onExpire = onExpire;
        expires = new HashMap<K, Long>();        
        scheduler.scheduleWithFixedDelay(new Worker(), checkInterval, checkInterval, TimeUnit.SECONDS);
    }
    
    public ExpirableMap(int ttl, OnExpire<V> onExpire) {
        this(ttl, DEFAULT_CHECK_INTERVAL, onExpire); 
    }

    public ExpirableMap(OnExpire<V> onExpire) {
        this(DEFAULT_TTL, DEFAULT_CHECK_INTERVAL, onExpire);        
    }   
    
    public ExpirableMap() {
        this(DEFAULT_TTL, DEFAULT_CHECK_INTERVAL, null);        
    }  

    @Override
    public V putIfAbsent(K key, V value) {
        synchronized(this){            
            expires.put(key, new Date().getTime()+ttl);            
            return super.putIfAbsent(key, value);            
        }   
    }        

    @Override
    public V put(K key, V value) {                        
        synchronized(this){
            expires.put(key, new Date().getTime()+ttl);
            return super.put(key, value);
        }        
    }
            
    private class Worker implements Runnable{

        @Override
        public void run() {
            Map<K, Long> m;
            synchronized(ExpirableMap.this){
                m = new HashMap<K, Long>(expires);
            }
            Long now = new Date().getTime();
            for(Entry<K, Long> e : m.entrySet()){
                if(e.getValue() > now){
                    continue;
                }
                synchronized(ExpirableMap.this){
                    Long expire = expires.get(e.getKey());
                    if(expire == null){                            
                        continue;
                    }
                    if(expire <= new Date().getTime()){
                        expires.remove(e.getKey());                        
                        V v = ExpirableMap.this.remove(e.getKey());                                            
                        if(v != null && onExpire != null){
                            onExpire.run(v);
                        }
                    }
                }                
            }
        }
        
    }
    
    public interface OnExpire<V>{
        public abstract void run(V value);
    }
}
