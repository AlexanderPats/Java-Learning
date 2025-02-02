package searchengine.services.indexing;

import org.jsoup.nodes.Document;
import searchengine.config.IndexingSettings;
import searchengine.model.SiteEntity;
import searchengine.services.ResultMessage;

public interface PageIndexService {

    void indexAndSavePage(SiteEntity siteEntity, String path, Document htmlDoc);

    void indexAndSaveSinglePage(SiteEntity siteEntity, String path, Document htmlDoc);

    Document getHtmlDocument(String url, String userAgent, String referrer);

    ResultMessage checkHTMLDocument(Document htmlDoc);

    /**
     * @throws RuntimeException if path to long or url has no content
     */
    String normalizePath(String path, String siteUrl, IndexingSettings indexingSettings) throws RuntimeException;

    boolean pageIsPresentInDB(SiteEntity siteEntity, String path);

}
