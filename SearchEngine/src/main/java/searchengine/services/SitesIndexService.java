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
public class SitesIndexService {

    private final SiteCRUDService siteService;
    private final PageCRUDService pageService;
    private final IndexingSettings indexingSettings;

    private List<ForkJoinPool> fjpList = new ArrayList<>();
    private boolean stopIndexingFlag = false;

    public void startSitesIndexing() {
        log.info("Start indexing for sites: {}", indexingSettings.getSites());
        stopIndexingFlag = false;
        SiteParsing.setStoppingIndexing(false);
        long startTime = System.currentTimeMillis();
        int coresCount = Runtime.getRuntime().availableProcessors();
        int threadsCount = Math.min(coresCount, indexingSettings.getSites().size());
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
                    siteService.changeSiteStatusByUrl(siteEntity.getUrl(), SiteStatus.FAILED, indexResultMsg.toString());
                }
                cdLatch.countDown();
            });
        }

        try { cdLatch.await(); }
        catch (InterruptedException e) {
            e.printStackTrace();
            log.warn("Метод cdLatch.await() вызвал исключение: {}", e.getMessage());
        }
        fjpList.forEach(ForkJoinPool::shutdown);
        poolExecutor.shutdown();
        ApiController.setIndexingIsRunning(false);
        System.out.println("Индексация завершена." + System.lineSeparator() +
                "Затраченное время: " + (System.currentTimeMillis() - startTime)/1000 + " с");

    }

    public void stopSiteIndexing() {
        log.info("Sites indexing is interrupted by user!");
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

    public IndexResultMessage parseSite(SiteEntity siteEntity) {
        SiteParsing siteParsing = new SiteParsing(
                siteEntity,
                siteEntity.getUrl(),
                indexingSettings,
                indexingSettings.getReferrer(),
                pageService
        );
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        fjpList.add(forkJoinPool);
        return forkJoinPool.invoke(siteParsing);
    }

}
