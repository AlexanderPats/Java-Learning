package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingSettings;
import searchengine.config.Site;
import searchengine.model.SiteEntity;
import searchengine.model.SiteStatus;

import java.util.ArrayList;
import java.util.List;
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
    private ThreadPoolExecutor poolExecutor;

//    private static final IndexResultMessage indexResultMessage;

    public void startSitesIndexing() {
        log.info("Start indexing for sites: {}", indexingSettings.getSites());
        stopIndexingFlag = false;
        SiteParsing.setStoppingIndexing(false);
        long startTime = System.currentTimeMillis();
        int threadsCount = Runtime.getRuntime().availableProcessors();
        poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsCount);

        for (Site site : indexingSettings.getSites()) {
            if (stopIndexingFlag) { break; }
            poolExecutor.execute(() -> {
                siteService.deleteByUrl(site.getUrl());
                SiteEntity siteEntity = new SiteEntity(SiteStatus.INDEXING, null, site.getUrl(), site.getName());
                siteService.save(siteEntity);
                IndexResultMessage indexResultMsg = parseSite(siteEntity);
                if (stopIndexingFlag) { Thread.currentThread().interrupt(); }
                if (indexResultMsg == IndexResultMessage.INDEXING_IS_COMPLETED) {
                    siteService.changeSiteStatusByUrl(siteEntity.getUrl(), SiteStatus.INDEXED, null);
                } else {
                    siteService.changeSiteStatusByUrl(siteEntity.getUrl(), SiteStatus.FAILED, indexResultMsg.toString());
                }
            });
        }
        poolExecutor.shutdown();
        System.out.println("Индексация завершена." + System.lineSeparator() +
                "Затраченное время: " + (System.currentTimeMillis() - startTime) + " мс");

//        indexingSettings.getSites().parallelStream().forEach(site -> {
//            siteService.deleteByUrl(site.getUrl());
//            SiteEntity siteEntity = new SiteEntity(SiteStatus.INDEXING, null, site.getUrl(), site.getName());
//            siteService.save(siteEntity);
//            IndexResultMessage indexResultMsg = parseSite(siteEntity);
//            if (indexResultMsg == IndexResultMessage.INDEXING_IS_COMPLETED) {
//                siteService.changeSiteStatusByUrl(siteEntity.getUrl(), SiteStatus.INDEXED, null);
//            } else {
//                siteService.changeSiteStatusByUrl(siteEntity.getUrl(), SiteStatus.FAILED, indexResultMsg.toString());
//            }
//        });
    }

    public void stopSiteIndexing() {
        log.info("Sites indexing is interrupted by user!");
        stopIndexingFlag = true;
        SiteParsing.setStoppingIndexing(true);
        fjpList.forEach(ForkJoinPool::shutdown);
        poolExecutor.shutdown();
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
        SiteParsing siteParsing = new SiteParsing(siteEntity, siteEntity.getUrl(), indexingSettings, pageService);
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        fjpList.add(forkJoinPool);
        return forkJoinPool.invoke(siteParsing);
    }

}
