package searchengine.services;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.IndexingSettings;
import searchengine.model.Page;
import searchengine.model.SiteEntity;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RecursiveTask;

@Slf4j
public class SiteParsing extends RecursiveTask<IndexResultMessage> {

    private static final int[] SUCCESSFUL_HTTP_CODES = {200, 203};
    private static final String[] ACCEPTED_CONTENT_TYPES = {"text/html"}; // Узнать, с какими типами еще умеет работать лемматизатор

    // при необходимости дополнить/сократить
    private static final String[] REJECTED_FILE_EXTENSIONS =
            {".css", ".js", ".json", ".ico", ".webp", ".png", ".jpg", ".jpeg", ".tga", ".svg", ".bmp",
                    ".zip", ".rar", ".7z", ".mp3", ".mp4", ".mkv", ".avi", ".mpg", ".mpeg",
                    ".docx", ".xlsx", ".pptx",".doc", ".xls", ".ppt", ".odt", ".odf", ".odp", ".pdf"
            };
//    Пока не пригодилось. Удалить?
//    private static final String[] TEXT_FILE_EXTENSIONS = {".htm", ".html", ".php", ".jsp", ".txt", ".xml"};

    @Setter
    private static volatile boolean stoppingIndexing = false;
    private static Map<String, Set<String>> visitedPages;

    private final SiteEntity siteEntity;
    private final String path;
    private final IndexingSettings indexingSettings;
    private String referrer;
    private final PageCRUDService pageService;

    public SiteParsing(
            SiteEntity siteEntity,
            String path,
            IndexingSettings indexingSettings,
            String referrer,
            PageCRUDService pageService
    ) {
        this.siteEntity = siteEntity;
        this.path = path;
        this.indexingSettings = indexingSettings;
        this.referrer = referrer;
        this.pageService = pageService;
    }

    @Override
    protected IndexResultMessage compute() {

        if (stoppingIndexing) { return IndexResultMessage.INDEXING_IS_CANCELED; }

        String pageUrl = siteEntity.getUrl() + path;
        Document htmlDoc = getHtmlDocument(pageUrl, indexingSettings);

        if (htmlDoc == null) { return IndexResultMessage.SITE_IS_UNAVAILABLE; }

        int responseCode = htmlDoc.connection().response().statusCode();
        if (!isSuccessfulCode(responseCode)) { return IndexResultMessage.SITE_IS_UNAVAILABLE; }

        if (!hasAcceptedContent(htmlDoc)) { return IndexResultMessage.SITE_HAS_NO_CONTENT; }

        if (pageService.getBySiteAndPath(siteEntity, path) == null) {
            Page page = new Page(siteEntity, path, responseCode, htmlDoc.toString());
            try { pageService.save(page); }
            catch (Exception e) {
                log.warn("Ошибка при сохранении страницы в БД: {}", e.toString());
                e.printStackTrace();}
        }

        List<SiteParsing> taskList = new LinkedList<>();

        String siteUrl = siteEntity.getUrl();
        String htmlElementPattern = "a[href^=" + siteUrl + "],[href^=/]";
        Elements htmlElements = htmlDoc.select(htmlElementPattern);

        for (Element element : htmlElements) {
            if (stoppingIndexing) { break; }

            String elementPath = element.attr("href");
            if ( elementPath.startsWith("//") ) { continue; }
            if ( elementPath.length() > indexingSettings.getPathMaxLength()) { continue; }
            if ( elementPath.equals(siteUrl) || elementPath.equals("/") ) { continue; }
            if ( elementPath.equals(path) || elementPath.equals(siteUrl.concat(path)) ) { continue; }

            if ( elementPath.startsWith(siteUrl) ) { elementPath = elementPath.substring(siteUrl.length()); }
            if ( elementPath.indexOf('#') >= 0 ) { elementPath = elementPath.substring(0, elementPath.indexOf('#')); }

            String pathWoParams = elementPath;
            if (elementPath.indexOf('?') >= 0) { pathWoParams = elementPath.substring(0, elementPath.indexOf('?')); }
            if (indexingSettings.isExcludeUrlParameters()) { elementPath = pathWoParams; }
            if (isRejectedPath(pathWoParams)) { continue; }

            if (pageService.getBySiteAndPath(siteEntity, elementPath) != null) { continue; }

            referrer = pageUrl;
            SiteParsing task = new SiteParsing(siteEntity, elementPath, indexingSettings, referrer, pageService);
            taskList.add(task);
            task.fork();
            task.join();
        }

//        for (SiteParsing task : taskList) {
//            if (stoppingIndexing) { task.cancel(true); }
//            else { task.join(); }
//        }

        if (stoppingIndexing) { return IndexResultMessage.INDEXING_IS_CANCELED; }
        else { return IndexResultMessage.INDEXING_IS_COMPLETED; }

    }


    private Document getHtmlDocument(String url, IndexingSettings indexingSettings) {
        try {
            Thread.sleep(indexingSettings.getRequestTimeout());
        } catch (InterruptedException e) {
            e.printStackTrace();
            log.warn("Метод Thread.sleep() для процесса {} вызвал исключение: {}",
                    Thread.currentThread().getName(), e.toString());
        }
        Document htmlDoc = null;
        try {
            htmlDoc = Jsoup.
                    connect(url).
                    userAgent(indexingSettings.getUserAgent()).
                    referrer(referrer).
                    get();
        } catch (IOException e) {
            e.printStackTrace();
            log.warn("Ошибка при открытии сайта {}: {}", url, e.toString());
        }
        return htmlDoc;
    }

    private boolean isSuccessfulCode(int httpCode) {
        boolean codeStatus = false;
        for (int i : SUCCESSFUL_HTTP_CODES) {
            if (i == httpCode) {
                codeStatus = true;
                break;
            }
        }
        return codeStatus;
    }

    private boolean hasAcceptedContent(Document htmlDocument) {
        String contentType = htmlDocument.connection().response().contentType();
        boolean hasAcceptedContent = false;
        for (String s : ACCEPTED_CONTENT_TYPES) {
            if (contentType.startsWith(s)) {
                hasAcceptedContent = true;
                break;
            }
        }
        return hasAcceptedContent;
    }

    private boolean isRejectedPath(String path) {
        boolean isRejectedPath = false;
        for (String rejected_string : REJECTED_FILE_EXTENSIONS) {
            if (path.toLowerCase().endsWith(rejected_string)) {
                isRejectedPath = true;
                break;
            }
        }
        return isRejectedPath;
    }

}
