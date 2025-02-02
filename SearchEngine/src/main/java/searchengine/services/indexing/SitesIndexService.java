package searchengine.services.indexing;

import searchengine.services.ResultMessage;

public interface SitesIndexService {
    void startSitesIndexing();
    void stopSitesIndexing();
    ResultMessage indexSinglePage(String pageUrl);
}
