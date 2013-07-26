package net.lightbody.bmp.core.har;

import org.codehaus.jackson.annotate.JsonWriteNullProperties;

@JsonWriteNullProperties(value=false)
public class HarPageTimings {
    private Long onContentLoad;
    private Long onLoad;
    private String comment;

    public HarPageTimings() {
    }

    public HarPageTimings(Long onContentLoad, Long onLoad) {
        this.onContentLoad = onContentLoad;
        this.onLoad = onLoad;
    }

    public Long getOnContentLoad() {
        return onContentLoad;
    }

    public void setOnContentLoad(Long onContentLoad) {
        this.onContentLoad = onContentLoad;
    }

    public Long getOnLoad() {
        return onLoad;
    }

    public void setOnLoad(Long onLoad) {
        this.onLoad = onLoad;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

}
