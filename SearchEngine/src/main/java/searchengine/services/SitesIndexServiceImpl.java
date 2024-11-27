package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingSettings;
import searchengine.config.Site;
import searchengine.controllers.ApiController;
import searchengine.model.SiteEntity;
import searchengine.model.SiteStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;

@Service
@RequiredArgsConstructor
@Slf4j
public class SitesIndexServiceImpl implements SitesIndexService {

    private final SiteCRUDService siteService;
    private final PageCRUDService pageService;
    private final IndexingSettings indexingSettings;

    private List<ForkJoinPool> fjpList = new ArrayList<>();
    private volatile boolean stopIndexingFlag = false;

    public void startSitesIndexing() {
        log.info("Запущена индексация для сайтов: {}", indexingSettings.getSites());
        long startTime = System.currentTimeMillis();
        stopIndexingFlag = false;
        SiteParsing.setStoppingIndexing(false);
        int coresCount = Runtime.getRuntime().availableProcessors();
        int threadsCount = Math.min(coresCount, indexingSettings.getSites().size());
        if (threadsCount < 1) {
            ApiController.setIndexingIsRunning(false);
            log.warn("Задан пустой список сайтов для индексации.");
            return;
        }
        ThreadPoolExecutor poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsCount);
        CountDownLatch cdLatch = new CountDownLatch(threadsCount);

        for (Site site : indexingSettings.getSites()) {
            if (stopIndexingFlag) { break; }
            poolExecutor.execute(() -> {
                siteService.deleteByUrl(site.getUrl());
                SiteEntity siteEntity = new SiteEntity(SiteStatus.INDEXING, null, site.getUrl(), site.getName());
                siteService.save(siteEntity);
                IndexResultMessage indexResultMsg = parseSite(siteEntity);
                if (indexResultMsg == IndexResultMessage.INDEXING_IS_COMPLETED) {
                    siteService.changeSiteStatusByUrl(siteEntity.getUrl(), SiteStatus.INDEXED, null);
                } else {
                    if (!stopIndexingFlag) {
                    siteService.changeSiteStatusByUrl(siteEntity.getUrl(), SiteStatus.FAILED, indexResultMsg.toString());
                    }
                }
                cdLatch.countDown();
            });
        }

        try { cdLatch.await(); }
        catch (InterruptedException e) { log.warn("Метод cdLatch.await() вызвал исключение: {}", e.toString()); }
        fjpList.forEach(ForkJoinPool::shutdown);
        poolExecutor.shutdown();
        ApiController.setIndexingIsRunning(false);
        log.info("Индексация завершена. Затраченное время: {} с", (System.currentTimeMillis() - startTime) / 1000);

    }

    public void stopSiteIndexing() {
        log.warn("Индексация прервана пользователем!");
        stopIndexingFlag = true;
        SiteParsing.setStoppingIndexing(true);
        fjpList.forEach(ForkJoinPool::shutdown);
        indexingSettings.getSites().forEach(site -> {
            if (siteService.getByUrl(site.getUrl()).getStatus() == SiteStatus.INDEXING) {
                siteService.changeSiteStatusByUrl(
                        site.getUrl(),
                        SiteStatus.FAILED,
                        IndexResultMessage.INDEXING_IS_CANCELED.toString()
                );
            }
        });
    }

    private IndexResultMessage parseSite(SiteEntity siteEntity) {
        SiteParsing siteParsing = new SiteParsing(
                siteEntity, "/", indexingSettings, indexingSettings.getReferrer(), pageService);
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        fjpList.add(forkJoinPool);
        return forkJoinPool.invoke(siteParsing);
    }

}
