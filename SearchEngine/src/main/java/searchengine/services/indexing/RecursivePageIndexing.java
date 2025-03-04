package searchengine.services.indexing;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.IndexingSettings;
import searchengine.model.SiteEntity;
import searchengine.services.ResultMessage;

import java.util.*;
import java.util.concurrent.RecursiveTask;

@Slf4j
public class RecursivePageIndexing extends RecursiveTask<ResultMessage> {

    @Setter
    private static volatile boolean stoppingIndexing = false;
    private static PageIndexService pageIndexService;
    private static IndexingSettings indexingSettings;
    private static String userAgent;
    private static int checkVisitedPagesAlgorithm; // 0 - проверка в сете 'visitedPages', 1 - проверка запросом к БД

    private final SiteEntity siteEntity;
    private final String path;
    private Element htmlBody;
    private String referrer;
    private String siteUrl;
    private final Set<String> visitedPages;
    private final LinkedList<Integer> requestTimeout;
    private final boolean isMainPage;

    /**
     * Конструктор для главной страницы
     */
    public RecursivePageIndexing( PageIndexService pageIndexService,
                                  SiteEntity siteEntity,
                                  IndexingSettings indexingSettings ) {
        RecursivePageIndexing.pageIndexService = pageIndexService;
        RecursivePageIndexing.indexingSettings = indexingSettings;
        userAgent = indexingSettings.getUserAgent();
        checkVisitedPagesAlgorithm = indexingSettings.getCheckVisitedPagesAlgorithm();
        this.siteEntity = siteEntity;
        path = "/";
        referrer = indexingSettings.getReferrer();
        visitedPages = new HashSet<>();
        requestTimeout = new LinkedList<>();
        requestTimeout.add(indexingSettings.getRequestTimeout());
        this.isMainPage = true;
    }

    /**
     * Конструктор для всех страниц, кроме главной
     */
    public RecursivePageIndexing( SiteEntity siteEntity,
                                  String path,
                                  Element htmlBody,
                                  Set<String> visitedPages,
                                  LinkedList<Integer> requestTimeout) {
        this.siteEntity = siteEntity;
        this.path = path;
        this.htmlBody = htmlBody;
        this.visitedPages = visitedPages;
        this.requestTimeout = requestTimeout;
        this.isMainPage = false;
    }


    @Override
    protected ResultMessage compute() {

        if (stoppingIndexing) { return ResultMessage.INDEXING_IS_CANCELED; }

        siteUrl = siteEntity.getUrl();

        if (isMainPage) {
            Document htmlDoc = pageIndexService.getHtmlDocument( siteUrl, userAgent, referrer );

            ResultMessage resultMessage = pageIndexService.checkHTMLDocument(htmlDoc);
            if (resultMessage != ResultMessage.PAGE_IS_CHECKED) { return resultMessage; }

            visitedPages.add(path);
            pageIndexService.indexAndSavePage(siteEntity, path, htmlDoc);

            htmlBody = htmlDoc.body();
        }

        recursiveParseHtmlBody();

        if (stoppingIndexing) { return ResultMessage.INDEXING_IS_CANCELED; }
        else { return ResultMessage.INDEXING_IS_COMPLETED; }
    }


    private void recursiveParseHtmlBody() {

        String htmlElementPattern = "a[href^=/], a[href^=" + siteUrl + "]";
        Elements htmlElements = htmlBody.select(htmlElementPattern);

        List<RecursivePageIndexing> taskList = new LinkedList<>();

        referrer = siteUrl.concat(path);

        for (Element element : htmlElements) {
            if (stoppingIndexing) { break; }

            String elementPath = element.attr("href");

            try { elementPath = pageIndexService.normalizePath(elementPath, siteUrl, indexingSettings); }
            catch (RuntimeException e) { continue; }

            if ( checkVisitedPagesAlgorithm == 0 && visitedPages.contains(elementPath) ) { continue; }
            if ( checkVisitedPagesAlgorithm != 0 && (pageIndexService.pageIsPresentInDB(siteEntity, elementPath)) ) {
                continue;
            }
            if ( elementPath.concat("/").equals(path) || elementPath.equals(path.concat("/")) ) { continue; }

            if (stoppingIndexing) { break; }

            Document elementDoc = getHtmlDoc(elementPath);//

            if (stoppingIndexing) { break; }

            if (pageIndexService.checkHTMLDocument(elementDoc) != ResultMessage.PAGE_IS_CHECKED) {
                addToVisitedPageSet(elementPath);
                continue;
            }

            String elementTruePath = elementDoc.connection().response().url().toString();

            try {
                elementTruePath = pageIndexService.normalizePath(elementTruePath, siteUrl, indexingSettings);
            } catch (RuntimeException e) {
                addToVisitedPageSet(elementPath);
                continue;
            }

            if ( checkIsVisitedPage(elementPath, elementTruePath, checkVisitedPagesAlgorithm) ) { continue; }

            if (stoppingIndexing) { break; }

            pageIndexService.indexAndSavePage(siteEntity, elementTruePath, elementDoc);

            if (stoppingIndexing) { break; }

            /*
            Следующая строка убирает футеры для всех страниц, кроме главной.
            Внимание! Некоторые сайты могут содержать разные футеры на разных страницах (пример: https://sendel.ru/).
            Если вы уверены, что все индексируемые сайты содержат единый футер,
            строку можно раскомментировать и закомментировать следующую.
            Это может значительно сократить время индексации.
             */
//            Element elementBody = deleteFooter(elementDoc.body());
            Element elementBody = elementDoc.body();

            RecursivePageIndexing task = new RecursivePageIndexing(
                    siteEntity, elementTruePath, elementBody, visitedPages, requestTimeout);
            taskList.add(task);
            task.fork();
        }

        for (RecursivePageIndexing task : taskList) {
            if (stoppingIndexing) { task.cancel(true); }
            else { task.join(); }
        }
    }


    private Document getHtmlDoc(String elementPath) {
        synchronized (requestTimeout) { // что бы обеспечить паузу между запросами в многопоточном режиме
            if (requestTimeout.getFirst() > 0) {
                try { Thread.sleep(requestTimeout.getFirst()); }
                catch (InterruptedException e) {
                    log.warn("Метод Thread.sleep() для процесса {} вызвал исключение: {}",
                            Thread.currentThread().getName(), e.toString());
                }
            }
        }
        return pageIndexService.getHtmlDocument( siteUrl.concat(elementPath), userAgent, referrer );
    }


    private boolean checkIsVisitedPage(String elementPath, String elementTruePath, int checkAlgorithm) {
        if (checkAlgorithm == 0) {
            synchronized (visitedPages) {
                if ( !visitedPages.add(elementTruePath) ) {
                    visitedPages.add(elementPath);
                    return true;
                }
                visitedPages.add(elementPath);
            }
        }
        if (checkAlgorithm != 0) {
            if ( elementTruePath.equals(path) || elementTruePath.equals("/") ) { return  true; }
            return pageIndexService.pageIsPresentInDB(siteEntity, elementTruePath);
        }
        return false;
    }


    private void addToVisitedPageSet(String path) {
        if (checkVisitedPagesAlgorithm == 0) {
            synchronized (visitedPages) {
                visitedPages.add(path);
            }
        }
    }


    private Element deleteFooter(Element htmlBody) {
        String s = htmlBody.toString();
        s = s.replaceAll("<footer>[\\s\\S]+?</footer>", "<footer></footer>");
        return Jsoup.parse(s);
    }

}
