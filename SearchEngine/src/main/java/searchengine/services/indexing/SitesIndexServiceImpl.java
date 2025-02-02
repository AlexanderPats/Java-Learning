package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingSettings;
import searchengine.config.Site;
import searchengine.controllers.ApiController;
import searchengine.model.SiteEntity;
import searchengine.model.SiteStatus;
import searchengine.services.ResultMessage;
import searchengine.services.crud.SiteCRUDService;

import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SitesIndexServiceImpl implements SitesIndexService {

    private final SiteCRUDService siteService;
    private final PageIndexService pageIndexService;
    private final IndexingSettings indexingSettings;

    private ThreadPoolExecutor poolExecutor;
    private List<ForkJoinPool> fjpList = new ArrayList<>();
    private volatile boolean stopIndexingFlag = false;


    @Override
    public void startSitesIndexing() {
        log.info("Запущена индексация для сайтов: {}", indexingSettings.getSites());
        long startTime = System.currentTimeMillis();
        stopIndexingFlag = false;
        RecursivePageIndexing.setStoppingIndexing(false);
        int coresCount = Runtime.getRuntime().availableProcessors();
        int threadsCount = Math.min(coresCount, indexingSettings.getSites().size());
        poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsCount);
        CountDownLatch cdLatch = new CountDownLatch(threadsCount);

        for (Site site : indexingSettings.getSites()) {
            if (stopIndexingFlag) { break; }
            poolExecutor.execute( () -> {
                indexSiteTask(site);
                cdLatch.countDown();
            } );
        }
        try { cdLatch.await(); }
        catch (InterruptedException e) {
            log.warn("Метод cdLatch.await в экземпляре класса {} вызвал исключение: {}",
                    this.getClass(), e.toString());
        }
        fjpList.forEach(ForkJoinPool::shutdown);
        poolExecutor.shutdown();
        ApiController.setIndexingIsRunning(false);
        log.info("КОНЕЦ! Индексация завершена. Затраченное время: {} с", (System.currentTimeMillis() - startTime) / 1000);
    }


    @Override
    public void stopSitesIndexing() {
        log.warn(ResultMessage.INDEXING_IS_CANCELED.toString());
        stopIndexingFlag = true;
        RecursivePageIndexing.setStoppingIndexing(true);
        fjpList.forEach(ForkJoinPool::shutdown);
        poolExecutor.shutdown();
        try { poolExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS); }
        catch (InterruptedException e) {
            log.warn("Метод awaitTermination в экземпляре класса {} вызвал исключение: {}",
                    this.getClass(), e.toString());
        }
        indexingSettings.getSites().forEach( site -> {
            SiteEntity siteEntity = siteService.getByUrl(site.getUrl());
            if (siteEntity !=null && siteEntity.getStatus() == SiteStatus.INDEXING) {
                siteService.updateStatusByUrl(
                        site.getUrl(), SiteStatus.FAILED, ResultMessage.INDEXING_IS_CANCELED.toString() );
            }
        } );
    }


    @Override
    public ResultMessage indexSinglePage(String pageUrl) {
        if (pageUrl == null || pageUrl.isBlank()) {
            log.warn(ResultMessage.EMPTY_REQUEST.toString());
            return ResultMessage.EMPTY_REQUEST;
        }
        if (!pageUrl.startsWith("https://") && !pageUrl.startsWith("http://")) { return ResultMessage.NO_PROTOCOL; }

        Document htmlDoc = pageIndexService.getHtmlDocument(
                pageUrl, indexingSettings.getUserAgent(), indexingSettings.getReferrer() );

        ResultMessage checkPageMsg = pageIndexService.checkHTMLDocument(htmlDoc);
        if (checkPageMsg != ResultMessage.PAGE_IS_CHECKED) { return checkPageMsg; }

        pageUrl = htmlDoc.connection().response().url().toString();

        String[] siteParams = getSiteParams(pageUrl);

        String siteUrl = siteParams[0];
        if (siteUrl.isEmpty()) {
            log.warn(ResultMessage.PAGE_IS_OUTSIDE_THE_SITES.toString().concat(". URL: {}"), pageUrl);
            return ResultMessage.PAGE_IS_OUTSIDE_THE_SITES;
        }
        String path = siteParams[2];
        try { path = pageIndexService.normalizePath(path, siteUrl, indexingSettings); }
        catch (RuntimeException e) { return ResultMessage.getByValue(e.getMessage()); }

        SiteEntity siteEntity = siteService.getByUrl(siteUrl);
        String siteName = siteParams[1];
        if (siteEntity == null) {
            siteEntity = new SiteEntity(SiteStatus.FAILED,
                    ResultMessage.SITE_IS_NOT_INDEXED.toString(),
                    siteUrl, siteName);
            siteService.save(siteEntity);
        }
        log.info("Запущена индексация отдельной страницы: {}", pageUrl);
        pageIndexService.indexAndSaveSinglePage(siteEntity, path, htmlDoc);
        log.info("Индексация страницы: {} завершена", pageUrl);

        return ResultMessage.INDEXING_IS_COMPLETED;
    }


    private void indexSiteTask(Site site) {
        String siteUrl = site.getUrl();
        log.info("Старт индексации для сайта: {}", siteUrl);
        siteService.deleteByUrl(siteUrl);
        SiteEntity siteEntity = new SiteEntity(SiteStatus.INDEXING, null, siteUrl, site.getName());
        siteService.save(siteEntity);

        ResultMessage indexResultMsg = indexSite(siteEntity);

        if (indexResultMsg == ResultMessage.INDEXING_IS_COMPLETED) {
            siteService.updateStatusByUrl(siteUrl, SiteStatus.INDEXED, null);
        } else {
            if (!stopIndexingFlag) {
                if (indexResultMsg == ResultMessage.PAGE_NOT_FOUND) {
                    indexResultMsg = ResultMessage.SITE_IS_UNAVAILABLE;
                }
                siteService.updateStatusByUrl(siteUrl, SiteStatus.FAILED, indexResultMsg.toString());
            }
        }
        log.info("Индексация завершена для сайта: {} с результатом: {}", siteUrl, indexResultMsg.toString());
    }


    private ResultMessage indexSite(SiteEntity siteEntity) {

        RecursivePageIndexing recursivePageIndexing =
                new RecursivePageIndexing( pageIndexService, siteEntity, indexingSettings);

        ForkJoinPool forkJoinPool = new ForkJoinPool();
        fjpList.add(forkJoinPool);

        return forkJoinPool.invoke(recursivePageIndexing);
    }


    /**
     * @param pageUrl page URL
     * @return String[0] - site URL,
     * String[1] - siteName from application.yml,
     * String[2] - page path (full path without site URL)
     */
    private String[] getSiteParams(String pageUrl) {
        pageUrl = pageUrl.toLowerCase();
        String[] result = {"", "", ""};
        String siteUrl;
        String pagePath;
        String tmp = pageUrl.replace("://", "");
        int index = tmp.indexOf("/");
        if (index == -1) {
            siteUrl = pageUrl;
            pagePath = "/";
        }
        else {
            index += 3;
            siteUrl = pageUrl.substring(0, index);
            pagePath = pageUrl.substring(index);
        }
        result[2] = pagePath;
        siteUrl = indexingSettings.getResponseSiteUrl(siteUrl);

        for (Site site : indexingSettings.getSites()) {
            if ( siteUrl.equals(site.getUrl()) ) {
                result[0] = site.getUrl();
                result[1] = site.getName();
                return result;
            }
        }
        return result;
    }

}
