package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
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

@RequiredArgsConstructor
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
    private final PageCRUDService pageService;

    private String referrer;
    private IndexResultMessage resultMessage = IndexResultMessage.SITE_IS_UNAVAILABLE;

    @Override
    protected IndexResultMessage compute() {

        if (stoppingIndexing) { return IndexResultMessage.INDEXING_IS_CANCELED; }

        Document htmlDoc = null;
        try {
            htmlDoc = Jsoup.
                    connect(url).
                    userAgent(indexingSettings.getUserAgent()).
                    referrer(referrer == null ? indexingSettings.getReferrer() : referrer).
                    get();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (htmlDoc == null) { return resultMessage; }

        int responseCode = htmlDoc.connection().response().statusCode();
        boolean hasSuccessfulCode = false;
        for (int i : SUCCESSFUL_HTTP_CODES) {
            if (i == responseCode) {
                hasSuccessfulCode = true;
                break;
            }
        }
        if (!hasSuccessfulCode) { return resultMessage; }

        String contentType = htmlDoc.connection().response().contentType();
        boolean hasAcceptedContent = false;
        for (String s : ACCEPTED_CONTENT_TYPES) {
            if (contentType.startsWith(s)) {
                hasAcceptedContent = true;
                break;
            }
        }
        if (!hasAcceptedContent) {
            if (resultMessage == IndexResultMessage.SITE_IS_UNAVAILABLE) {
                resultMessage = IndexResultMessage.SITE_HAS_NO_CONTENT;
            }
            return resultMessage;
        }

        String mainPageUrl = siteEntity.getUrl();
        String path;
        if ( url.equals(mainPageUrl) ) { path = "/"; }
        else { path = url.substring(mainPageUrl.length()); }

        if (pageService.getBySiteAndPath(siteEntity, path) != null) { return resultMessage; }

        Page page = new Page(siteEntity, path, responseCode, htmlDoc.toString());
        pageService.save(page);
        resultMessage = IndexResultMessage.INDEXING_IS_COMPLETED;
//        System.out.println(path);
//        System.out.println(htmlDoc.connection().response().statusCode());
//        System.out.println(htmlDoc.connection().response().contentType());
//        System.out.println(referrer);

        List<SiteParsing> taskList = new LinkedList<>();

        String htmlElementPattern = "a[href^=" + mainPageUrl + "],[href^=/]";
        Elements htmlElements = htmlDoc.select(htmlElementPattern);

        for (Element element : htmlElements) {
            if (stoppingIndexing) { break; }

            path = element.attr("href");
            if ( path.startsWith("//") ) { continue; }
            if ( path.length() > indexingSettings.getPathMaxLength()) { continue; }
            if ( path.startsWith(mainPageUrl) ) { path = path.substring(mainPageUrl.length()); }
            if ( path.indexOf('#') >= 0 ) { path = path.substring(0, path.indexOf('#')); }

            String pathWoParams = path;
            if (path.indexOf('?') >= 0) {
                pathWoParams = path.substring(0, path.indexOf('?'));
            }
            if (indexingSettings.isExcludeUrlParameters()) {
                path = pathWoParams;
            }
            boolean isRejectedPath = false;
            for (String rejected_string : REJECTED_FILE_EXTENSIONS) {
                if (pathWoParams.toLowerCase().endsWith(rejected_string)) {
                    isRejectedPath = true;
                    break;
                }
            }
            if (isRejectedPath) { continue; }

            referrer = url;

            SiteParsing task = new SiteParsing(siteEntity, mainPageUrl + path, indexingSettings, pageService);
            try { Thread.sleep(indexingSettings.getRequestTimeout()); }
            catch (InterruptedException e) { e.printStackTrace(); }
            task.fork();
            taskList.add(task);
        }

        for (SiteParsing task : taskList) {
            if (stoppingIndexing) { task.cancel(true); }
            else { task.join(); }
        }

//            if (stoppingIndexing) {
////                taskList.forEach(t -> t.cancel(true)); // так задачи долго завершаются !
//                SitesIndexService.getFjpList().forEach(fjp -> {
//                    fjp.shutdown();
////                    try {
////                        fjp.awaitTermination(1, TimeUnit.NANOSECONDS);
////                    } catch (InterruptedException e) {
////                        throw new RuntimeException(e);
////                    }
////                    fjp.shutdownNow();
//                });
//                break;
//            }
//            else { task.join(); }
        if (stoppingIndexing) { resultMessage = IndexResultMessage.INDEXING_IS_CANCELED; }

        return resultMessage;
    }

}
