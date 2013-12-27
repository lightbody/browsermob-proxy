package net.lightbody.bmp.proxy;

public class FeatureFlags {

    private boolean featureFlags = true;
    private boolean requestLogs = false;

    /**
     * private constructor
     */
    private FeatureFlags() {

    }

    public boolean getFeatureFlags() {
        return this.featureFlags;
    }

    public boolean getRequestLogs() {
        return this.requestLogs;
    }

    public void setRequestLogs(boolean requestLogs) {
        this.requestLogs = requestLogs;
    }

    public static FeatureFlags getInstance() {
        FeatureFlags instance = null;

        if (instance == null) {
            instance = new FeatureFlags();
        }

        return instance;
    }
}