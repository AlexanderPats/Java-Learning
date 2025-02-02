package searchengine.services.statistics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.controllers.ApiController;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.services.crud.LemmaCRUDService;
import searchengine.services.crud.PageCRUDService;
import searchengine.services.crud.SiteCRUDService;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteCRUDService siteService;
    private final PageCRUDService pageService;
    private final LemmaCRUDService lemmaService;

    @Override
    public StatisticsResponse getStatistics() {
        StatisticsResponse response = new StatisticsResponse();
        try {
            StatisticsData data = getStatisticsData();
            response.setStatistics(data);
            response.setResult(true);
        } catch (Exception e) {
            response.setResult(false);
            log.warn("Метод getStatistics() вызвал исключение: {}", e.toString());            
        }
        return response;
    }

    private StatisticsData getStatisticsData() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(siteService.getCount());
        total.setPages(pageService.getCount());
        total.setLemmas(lemmaService.getCount());
        total.setIndexing(ApiController.isIndexingIsRunning());

        List<DetailedStatisticsItem> detailed = getDetailedStatisticsItems();

        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        return data;
    }

    private List<DetailedStatisticsItem> getDetailedStatisticsItems() {
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteEntity> sitesList = siteService.getAll();

        sitesList.forEach( siteEntity -> {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(siteEntity.getName());
            String siteUrl = siteEntity.getUrl();
            item.setUrl(siteUrl);
            item.setStatus(siteEntity.getStatus().toString());
            item.setStatusTime(siteEntity.getStatusTime().toEpochMilli());
            item.setError(siteEntity.getLastError());
            item.setPages(pageService.getCountBySiteEntity(siteEntity));
            item.setLemmas(lemmaService.getCountBySiteEntity(siteEntity));
            detailed.add(item);
        });
        return detailed;
    }

}
