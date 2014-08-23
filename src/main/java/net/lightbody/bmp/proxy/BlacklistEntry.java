package net.lightbody.bmp.proxy;

import java.util.regex.Pattern;

public class BlacklistEntry
{
    private Pattern pattern;
    private int responseCode;

    public BlacklistEntry(String pattern, int responseCode) {
        this.pattern = Pattern.compile(pattern);
        this.responseCode = responseCode;
    }

    public Pattern getPattern() {
        return this.pattern;
    }

    public int getResponseCode() {
        return this.responseCode;
    }
}
