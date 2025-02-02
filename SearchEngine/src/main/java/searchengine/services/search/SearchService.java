package searchengine.services.search;

import searchengine.dto.search.SearchResponse;

public interface SearchService {

    SearchResponse search(String site, String query, Integer offset, Integer limit);

}