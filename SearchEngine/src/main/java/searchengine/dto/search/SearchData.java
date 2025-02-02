package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SearchData implements Comparable<SearchData> {

    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private Float relevance;

    @Override
    public int compareTo(SearchData o) {
        int result = o.relevance.compareTo(this.relevance);
        if (result == 0) { result = this.siteName.compareTo(o.siteName); }
        if (result == 0) { result = this.title.compareTo(o.title); }
        return result;
    }

}
