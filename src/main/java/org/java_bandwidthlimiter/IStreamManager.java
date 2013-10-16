package org.java_bandwidthlimiter;

public interface IStreamManager {

    /**
     * Calling this method to start throttling all registered streams.
     * By default the StreamManager is disabled
     */
    public abstract void enable();

    /**
     * Calling this method to stop throttling all registered streams.
     * By default the StreamManager is disabled
     */
    public abstract void disable();

    /**
     * To take into account overhead due to underlying protocols (e.g. TCP/IP)
     * @param payloadPercentage a  ] 0 , 100] value. where 100 means that the required
     *                          downstream/upstream bandwidth will be full used for
     *                          sending payload.
     *                          Default value is 95%.
     *                          The default value is applied if an out of boundaries value is passed in.
     */
    public abstract void setPayloadPercentage(int payloadPercentage);

    /**
     * This function sets the max bits per second threshold
     * {@link #setDownstreamKbps} and {@link #setDownstreamKbps(long)} won't be allowed
     * to set a bandwidth higher than what specified here.
     * @param maxBitsPerSecond The max bits per seconds you want this instance of StreamManager to respect.
     */
    public abstract void setMaxBitsPerSecondThreshold(long maxBitsPerSecond);

    /**
     * setting the max kilobits per seconds this StreamManager should apply in downstream,
     * as aggregate bandwidth of all the InputStream registered.
     * @param downstreamKbps the desired max kilobits per second downstream rate.
     */
    public abstract void setDownstreamKbps(long downstreamKbps);

    /**
     * setting the max kilobits per seconds this StreamManager should apply in upstream,
     * as aggregate bandwidth of all the OutputStream registered.
     * @param upstreamKbps the desired max kilobits per second upstream rate.
     */
    public abstract void setUpstreamKbps(long upstreamKbps);

    /**
     * setting the additional (simulated) latency that the streams will suffer.
     * By default the latency applied is equal to zero.
     * @param latency the desired additional latency in milliseconds
     */
    public abstract void setLatency(long latency);

}
