package net.lightbody.bmp.core.har;

public class HarTimings {
    private long blocked;
    private long dns;
    private long connect;
    private long send;
    private long wait;
    private long receive;
    private long ssl;
    private String comment;

    public HarTimings() {
    }

    public HarTimings(long blocked, long dns, long connect, long send, long wait, long receive) {
        this.blocked = blocked;
        this.dns = dns;
        this.connect = connect;
        this.send = send;
        this.wait = wait;
        this.receive = receive;
    }

    public Long getBlocked() {
        return blocked;
    }

    public void setBlocked(Long blocked) {
        this.blocked = blocked;
    }

    public Long getDns() {
        return dns;
    }

    public void setDns(Long dns) {
        this.dns = dns;
    }

    public Long getConnect() {
        return connect;
    }

    public void setConnect(Long connect) {
        this.connect = connect;
    }

    public long getSend() {
        return send;
    }

    public void setSend(long send) {
        this.send = send;
    }

    public long getWait() {
        return wait;
    }

    public void setWait(long wait) {
        this.wait = wait;
    }

    public long getReceive() {
        return receive;
    }

    public void setReceive(long receive) {
        this.receive = receive;
    }

    public long getSsl() {
        return ssl;
    }

    public void setSsl(long ssl) {
        this.ssl = ssl;
    }

    public String getComment() {
        return comment;
   }

    public void setComment(String comment) {
        this.comment = comment;
    }

}
