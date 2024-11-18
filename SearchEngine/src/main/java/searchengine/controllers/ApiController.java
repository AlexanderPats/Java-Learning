package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.indexing.ErrMessage;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.SitesIndexService;
import searchengine.services.StatisticsService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SitesIndexService sitesIndexService;

    boolean indexingIsRunning = false;
    Thread indexSitesThread;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() {
        if (indexingIsRunning) {
            return ResponseEntity.ok(new IndexingResponse(false, ErrMessage.INDEXING_ALREADY_STARTED.toString()));
        }
        indexingIsRunning = true;
        indexSitesThread = new Thread(sitesIndexService::startSitesIndexing);
        indexSitesThread.start();
//        sitesIndexService.startSitesIndexing();
        return ResponseEntity.ok(new IndexingResponse(true, null));
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        if (!indexingIsRunning) {
            return ResponseEntity.ok(new IndexingResponse(false, ErrMessage.INDEXING_NOT_STARTED.toString()));
        }
        sitesIndexService.stopSiteIndexing();
        indexingIsRunning = false;
        indexSitesThread.interrupt();
        return ResponseEntity.ok(new IndexingResponse(true, null));
    }

}
