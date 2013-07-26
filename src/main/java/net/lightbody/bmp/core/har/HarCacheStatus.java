package net.lightbody.bmp.core.har;

import net.lightbody.bmp.core.json.ISO8601DateFormatter;
import org.codehaus.jackson.annotate.JsonWriteNullProperties;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import java.util.Date;

@JsonWriteNullProperties(value=false)
public class HarCacheStatus {
    private Date expires;
    private Date lastAccess;
    private String eTag;
    private int hitCount;
    private String comment;

    @JsonSerialize(using = ISO8601DateFormatter.class)
    public Date getExpires() {
        return expires;
    }

    public void setExpires(Date expires) {
        this.expires = expires;
    }

    @JsonSerialize(using = ISO8601DateFormatter.class)
    public Date getLastAccess() {
        return lastAccess;
    }

    public void setLastAccess(Date lastAccess) {
        this.lastAccess = lastAccess;
    }

    public String geteTag() {
        return eTag;
    }

    public void seteTag(String eTag) {
        this.eTag = eTag;
    }

    public int getHitCount() {
        return hitCount;
    }

    public void setHitCount(int hitCount) {
        this.hitCount = hitCount;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
