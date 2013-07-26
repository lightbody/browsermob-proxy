package net.lightbody.bmp.core.har;

import net.lightbody.bmp.core.json.ISO8601DateFormatter;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import java.util.Date;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class HarPage {
    private String id;
    private Date startedDateTime;
    private String title = "";
    private HarPageTimings pageTimings = new HarPageTimings();
    private String comment;

    public HarPage() {
    }

    public HarPage(String id) {
        this.id = id;
        startedDateTime = new Date();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonSerialize(using = ISO8601DateFormatter.class)
    public Date getStartedDateTime() {
        return startedDateTime;
    }

    public void setStartedDateTime(Date startedDateTime) {
        this.startedDateTime = startedDateTime;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public HarPageTimings getPageTimings() {
        return pageTimings;
    }

    public void setPageTimings(HarPageTimings pageTimings) {
        this.pageTimings = pageTimings;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

}
