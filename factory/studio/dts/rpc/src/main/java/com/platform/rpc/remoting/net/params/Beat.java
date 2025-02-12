package com.platform.rpc.remoting.net.params;

/**
 * beat for keep-alive
 *
 * @author AllDataDC 2023-01-27
 */
public final class Beat {

    public static final int BEAT_INTERVAL = 30;
    public static final String BEAT_ID = "BEAT_PING_PONG";

    public static XxlRpcRequest BEAT_PING;

    static {
        BEAT_PING = new XxlRpcRequest(){};
        BEAT_PING.setRequestId(BEAT_ID);
    }

}
