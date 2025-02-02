package searchengine.controllers;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.IndexingSettings;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.ResultMessage;
import searchengine.services.indexing.SitesIndexService;
import searchengine.services.search.SearchService;
import searchengine.services.statistics.StatisticsService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SitesIndexService sitesIndexService;
    private final SearchService searchService;
    private final IndexingSettings indexingSettings;

    @Getter
    @Setter
    private static volatile boolean indexingIsRunning = false;

    private Thread indexSitesThread;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        log.debug("Получен запрос статистики индексации сайтов");
        ResponseEntity<StatisticsResponse> responseEntity;
        StatisticsResponse response = statisticsService.getStatistics();
        if (response.isResult()) { responseEntity = ResponseEntity.ok(response); }
        else { responseEntity = new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR); }
        return responseEntity;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() {
        log.info("Получен запрос на индексацию сайтов");
        if (indexingIsRunning) {
            log.warn(ResultMessage.INDEXING_ALREADY_STARTED.toString());
            IndexingResponse response =
                    new IndexingResponse(false, ResultMessage.INDEXING_ALREADY_STARTED.toString());
            return new ResponseEntity<>(response, HttpStatus.NOT_ACCEPTABLE);
        }
        if (indexingSettings.getSites().isEmpty()) {
            log.warn(ResultMessage.EMPTY_SITES_LIST.toString());
            IndexingResponse response =
                    new IndexingResponse(false, ResultMessage.EMPTY_SITES_LIST.toString());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        indexingIsRunning = true;
        indexSitesThread = new Thread(sitesIndexService::startSitesIndexing);
        indexSitesThread.start();
        return ResponseEntity.ok(new IndexingResponse(true, null));
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        log.info("Получен запрос на остановку индексации сайтов");
        if (!indexingIsRunning) {
            log.warn(ResultMessage.INDEXING_NOT_STARTED.toString());
            IndexingResponse response =
                    new IndexingResponse(false, ResultMessage.INDEXING_NOT_STARTED.toString());
            return new ResponseEntity<>(response, HttpStatus.NOT_ACCEPTABLE);
        }
        sitesIndexService.stopSitesIndexing();
        indexingIsRunning = false;
        indexSitesThread.interrupt();
        return ResponseEntity.ok(new IndexingResponse(true, null));
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(@RequestParam String url) {
        log.info("Получен запрос на индексацию страницы '{}'", url);
        ResultMessage resultMsg = sitesIndexService.indexSinglePage(url);
        if ( resultMsg == ResultMessage.INDEXING_IS_COMPLETED ) {
            return ResponseEntity.ok( new IndexingResponse(true, null) );
        } else {
            return new ResponseEntity<>(new IndexingResponse(false, resultMsg.toString()), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam(required = false) String site,
                                                     @RequestParam String query,
                                                     @RequestParam(required = false) Integer offset,
                                                     @RequestParam(required = false) Integer limit ) {
        if (site == null) { log.debug("Получен поисковый запрос по всем сайтам: '{}'", query); }
        else  { log.debug("Получен поисковый запрос по сайту {}: '{}'", site, query); }
        SearchResponse searchResponse = searchService.search(site, query, offset, limit);
        return new ResponseEntity<>(searchResponse, searchResponse.getHttpStatus());
    }

    @GetMapping("/stopService")
    public void stopService() {
        if (indexingIsRunning) {
            sitesIndexService.stopSitesIndexing();
            indexSitesThread.interrupt();
        }
        log.warn("Работа сервиса прекращена пользователем");
        System.exit(0);
    }

}
