package net.lightbody.bmp.core.har;

import org.codehaus.jackson.annotate.JsonWriteNullProperties;

import java.util.List;

@JsonWriteNullProperties(value=false)
public class HarPostData {
    private String mimeType;
    private List<HarPostDataParam> params;
    private String text;
    private String comment;

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public List<HarPostDataParam> getParams() {
        return params;
    }

    public void setParams(List<HarPostDataParam> params) {
        this.params = params;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
