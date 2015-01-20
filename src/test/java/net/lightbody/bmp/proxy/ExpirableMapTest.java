package net.lightbody.bmp.proxy;

import java.util.HashSet;
import java.util.Set;

import net.lightbody.bmp.proxy.util.ExpirableMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class ExpirableMapTest {    
    private Set<String> strings = new HashSet<String>();
    private ExpirableMap<Integer, String> m;
    
    @Before
    public void setUp() throws Exception {
        m = new ExpirableMap<Integer, String>(1, 1, new ExpirableMap.OnExpire<String>(){
            @Override
            public void run(String s) {
                ExpirableMapTest.this.strings.add(s);
            }
        });
    }
    
    @Test
    public void testKeyExpiration() throws Exception {
                        
        m.put(1, "a");
        m.put(1, "b");
        String s = m.putIfAbsent(2, "c");
        
        assertNull(s);
        
        s = m.putIfAbsent(2, "d");
        
        assertEquals("c", s);
        
        Thread.sleep(2000);
        
        assertEquals(0, m.size());
                        
        assertFalse(strings.contains("a"));
        
        assertTrue(strings.contains("b"));
        
        assertTrue(strings.contains("c"));
        
    }
}
