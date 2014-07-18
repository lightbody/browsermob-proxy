package net.lightbody.bmp.core.har;

import java.util.Set;

public class PageRefFilteredHar extends Har {
    public PageRefFilteredHar(Har har, Set<String> pageRef) {
        super(new PageRefFilteredHarLog(har.getLog(), pageRef));
    }
}
