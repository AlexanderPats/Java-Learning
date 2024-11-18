package searchengine.services;

import lombok.RequiredArgsConstructor;
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
import java.util.concurrent.RecursiveTask;

//@RequiredArgsConstructor
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
    // Пока не пригодилось. Удалить?
    private static final String[] TEXT_FILE_EXTENSIONS = {".htm", ".html", ".php", ".jsp", ".txt", ".xml"};

    @Setter
    private static boolean stoppingIndexing = false;

    private final SiteEntity siteEntity;
    private final String url;
    private final IndexingSettings indexingSettings;
    private String referrer;
    private final PageCRUDService pageService;

    public SiteParsing(
            SiteEntity siteEntity,
            String url,
            IndexingSettings indexingSettings,
            String referrer,
            PageCRUDService pageService) {
        this.siteEntity = siteEntity;
        this.url = url;
        this.indexingSettings = indexingSettings;
        this.referrer = referrer;
        this.pageService = pageService;
    }

    @Override
    protected IndexResultMessage compute() {

        if (stoppingIndexing) { return IndexResultMessage.INDEXING_IS_CANCELED; }

        Document htmlDoc = getHtmlDocument(url,indexingSettings);
        if (htmlDoc == null) { return IndexResultMessage.SITE_IS_UNAVAILABLE; }

        int responseCode = htmlDoc.connection().response().statusCode();
        if (!isSuccessfulCode(responseCode)) { return IndexResultMessage.SITE_IS_UNAVAILABLE; }

        if (!hasAcceptedContent(htmlDoc)) { return IndexResultMessage.SITE_HAS_NO_CONTENT; }

        List<SiteParsing> taskList = new LinkedList<>();

        String mainPageUrl = siteEntity.getUrl();
        String htmlElementPattern = "a[href^=" + mainPageUrl + "],[href^=/]";
        Elements htmlElements = htmlDoc.select(htmlElementPattern);

        for (Element element : htmlElements) {
            if (stoppingIndexing) { break; }

            String path = element.attr("href");
            if ( path.startsWith("//") ) { continue; }
            if ( path.length() > indexingSettings.getPathMaxLength()) { continue; }

            if ( path.equals(mainPageUrl) ) { path = "/"; }
            if ( path.startsWith(mainPageUrl) ) { path = path.substring(mainPageUrl.length()); }
            if ( path.indexOf('#') >= 0 ) { path = path.substring(0, path.indexOf('#')); }

            String pathWoParams = path;
            if (path.indexOf('?') >= 0) { pathWoParams = path.substring(0, path.indexOf('?')); }
            if (indexingSettings.isExcludeUrlParameters()) { path = pathWoParams; }
            if (isRejectedPath(pathWoParams)) { continue; }

            if (pageService.getBySiteAndPath(siteEntity, path) != null) { continue; }

            Page page = new Page(siteEntity, path, responseCode, htmlDoc.toString());
            try { pageService.save(page); }
            catch (Exception e) {e.printStackTrace();}

            referrer = url.equals(mainPageUrl) ? url.concat("/") : url;

            SiteParsing task = new SiteParsing(siteEntity, mainPageUrl + path, indexingSettings, referrer, pageService);
            try { Thread.sleep(indexingSettings.getRequestTimeout()); }
            catch (InterruptedException e) {
                e.printStackTrace();
                log.warn("Метод Thread.sleep({}) вызвал исключение: {}",
                        Thread.currentThread().getName(), e.getMessage());
            }
            task.fork();
            taskList.add(task);
        }

        for (SiteParsing task : taskList) {
            if (stoppingIndexing) { task.cancel(true); }
            else { task.join(); }
        }

        if (stoppingIndexing) { return IndexResultMessage.INDEXING_IS_CANCELED; }
        else { return IndexResultMessage.INDEXING_IS_COMPLETED; }

    }


    private Document getHtmlDocument(String url, IndexingSettings indexingSettings) {
        Document htmlDoc = null;
        try {
            htmlDoc = Jsoup.
                    connect(url).
                    userAgent(indexingSettings.getUserAgent()).
                    referrer(referrer).
                    get();
        } catch (IOException e) {
            e.printStackTrace();
            log.warn("Ошибка при открытии сайта {}: {}", url, e.getMessage());
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
