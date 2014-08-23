package net.lightbody.bmp.proxy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class WhitelistEntry {
    private List<Pattern> patterns = new CopyOnWriteArrayList<Pattern>();
    private int responseCode;

    public WhitelistEntry(String[] patterns, int responseCode) {
        for (String pattern : patterns) {
            this.patterns.add(Pattern.compile(pattern));
        }
        this.responseCode = responseCode;
    }

    public List<Pattern> getPatterns() {
        return this.patterns;
    }

    public int getResponseCode() {
        return this.responseCode;
    }
}
