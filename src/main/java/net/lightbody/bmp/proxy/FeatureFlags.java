package net.lightbody.bmp.proxy;

public class FeatureFlags {

    private boolean featureFlags = true;

    /**
     * private constructor
     */
    private FeatureFlags() {

    }

    public boolean getFeatureFlags() {
        return this.featureFlags;
    }

    public static FeatureFlags getInstance() {
        FeatureFlags instance = null;

        if (instance == null) {
            instance = new FeatureFlags();
        }

        return instance;
    }
}