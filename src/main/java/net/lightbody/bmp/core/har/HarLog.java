package net.lightbody.bmp.core.har;

import org.codehaus.jackson.map.annotate.JsonSerialize;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class HarLog {
    private String version = "1.2";
    private HarNameVersion creator;
    private HarNameVersion browser;
    private List<HarPage> pages = new CopyOnWriteArrayList<HarPage>();
    private List<HarEntry> entries = new CopyOnWriteArrayList<HarEntry>();
    private String comment;

    public HarLog() {
    }

    public HarLog(HarNameVersion creator) {
        this.creator = creator;
    }

    public void addPage(HarPage page) {
        if (pages == null) {
            pages = new CopyOnWriteArrayList<HarPage>();
        }

        pages.add(page);
    }

    public void addEntry(HarEntry entry) {
        if (entries == null) {
            entries = new CopyOnWriteArrayList<HarEntry>();
        }

        entries.add(entry);
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public HarNameVersion getCreator() {
        return creator;
    }

    public void setCreator(HarNameVersion creator) {
        this.creator = creator;
    }

    public HarNameVersion getBrowser() {
        return browser;
    }

    public void setBrowser(HarNameVersion browser) {
        this.browser = browser;
    }

    public List<HarPage> getPages() {
        return pages;
    }

    public void setPages(List<HarPage> pages) {
        this.pages = pages;
    }

    public List<HarEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<HarEntry> entries) {
        this.entries = entries;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
