package net.lightbody.bmp.proxy;

import java.util.regex.Pattern;

public class BlacklistEntry
{
    private Pattern pattern;
    private int responseCode;
	private Pattern method;

    public BlacklistEntry(String pattern, int responseCode, String method) {
        this.pattern = Pattern.compile(pattern);
        this.responseCode = responseCode;
		this.method = Pattern.compile("".equals(method) ? ".*" : method);
    }

    public Pattern getPattern() {
        return this.pattern;
    }

    public int getResponseCode() {
        return this.responseCode;
    }
	
	public Pattern getMethod() {
		return this.method;
	}
}
