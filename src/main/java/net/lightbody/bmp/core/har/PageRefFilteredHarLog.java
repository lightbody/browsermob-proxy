package net.lightbody.bmp.core.har;

import org.codehaus.jackson.map.annotate.JsonSerialize;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class PageRefFilteredHarLog extends HarLog {
    public PageRefFilteredHarLog(HarLog log, Set<String> pageRef) {
        super(log.getCreator());
        setVersion(log.getVersion());
        setBrowser(log.getBrowser());
        setPages(getFilteredPages(log.getPages(), pageRef));
        setEntries(getFilteredEntries(log.getEntries(), pageRef));
        setComment(log.getComment());
    }

    private static List<HarPage> getFilteredPages(List<HarPage> pages, Set<String> pageRef) {
        List<HarPage> filteredPages = new CopyOnWriteArrayList<HarPage>();
        for (HarPage page : pages) {
            if (pageRef.contains(page.getId())) {
                filteredPages.add(page);
            }
        }
        return filteredPages;
    }

    private static List<HarEntry> getFilteredEntries(List<HarEntry> entries, Set<String> pageRef) {
        List<HarEntry> filteredEntries = new CopyOnWriteArrayList<HarEntry>();
        for (HarEntry entry : entries) {
            if (pageRef.contains(entry.getPageref())) {
                filteredEntries.add(entry);
            }
        }
        return filteredEntries;
    }
}
