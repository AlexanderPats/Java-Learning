package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;
import searchengine.services.ResultMessage;
import searchengine.services.crud.IndexCRUDService;
import searchengine.services.crud.LemmaCRUDService;
import searchengine.services.crud.PageCRUDService;
import searchengine.services.crud.SiteCRUDService;
import searchengine.services.morphology.MorphologyService;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    /**
     * If the number of site pages, on which the lemma occurs, is greater than this value,
     * and the search request contains more than one lemma,
     * then the lemma will be ignored in the search
     */
    private final int MAX_NUM_PAGES = 10_000;

    private final MorphologyService morphologyService;
    private final SiteCRUDService siteService;
    private final PageCRUDService pageService;
    private final LemmaCRUDService lemmaService;
    private final IndexCRUDService indexService;

    private Integer offset;
    private Integer limit;
    private String query;
    private boolean queryIsChanged = true;
    private String site;
    private Set<String> lemmas;
    private SiteEntity siteEntity;
    private List<IndexEntity> idxEntities = new ArrayList<>();
    private Map<Integer, Float> relevanceMap;
    private Integer[] pageIDs;
    private boolean isSingleSiteSearch;


    @Override
    public SearchResponse search(String site, String query, Integer offset, Integer limit) {
        if (query.isBlank()) {
            return new SearchResponse(false, 0, null, ResultMessage.EMPTY_REQUEST.toString(), HttpStatus.BAD_REQUEST);
        }
        if (offset == null) {
            offset = 0;
        }
        if (limit == null) {
            limit = 20;
        }
        this.offset = offset;
        this.limit = limit;
        if (!query.equals(this.query)) {
            queryIsChanged = true;
        }
        this.query = query;
        if (queryIsChanged) {
            lemmas = morphologyService.getUniqueLemmasFromText(query);
        }
        if (lemmas.isEmpty()) {
            return new SearchResponse(
                    false, 0, null, ResultMessage.RUS_WORDS_ARE_REQUIRED.toString(), HttpStatus.BAD_REQUEST);
        }
        if (site == null) {
            return searchAllSites();
        } else return searchSingleSite(site);
    }


    private SearchResponse searchSingleSite(String site) {
        isSingleSiteSearch = true;
        if (!site.equals(this.site)) {
            this.site = site;
            siteEntity = siteService.getByUrl(site);
            queryIsChanged = true;
        }
        if (siteEntity == null || siteEntity.getStatus() != SiteStatus.INDEXED) {
            return new SearchResponse(
                    false, null, null, ResultMessage.SITE_IS_NOT_INDEXED.toString(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (queryIsChanged) {
            List<LemmaEntity> lemmaEntities = lemmaService.getAllBySiteAndLemmas(siteEntity, lemmas);

            if (lemmaEntities.size() < lemmas.size()) {
                return makeSearchResponse(List.of());
            }

            delFrequentLemmasEntities(siteEntity, lemmaEntities);
            idxEntities = getIndexEntitiesWithPagesContainingAllLemmas(lemmaEntities);
        }
        return makeSearchResponse(idxEntities);
    }


    private SearchResponse searchAllSites() {
        if (isSingleSiteSearch) {
            queryIsChanged = true;
            isSingleSiteSearch = false;
        }
        if (queryIsChanged) {
            idxEntities.clear();
            List<LemmaEntity> lemmaEntities = new ArrayList<>();
            List<SiteEntity> siteEntities = siteService.getAllByStatus(SiteStatus.INDEXED);
            Set<Integer> siteIDs = new HashSet<>();
            for (SiteEntity currentSiteEntity : siteEntities) {
                List<LemmaEntity> currentLemmaEntities = lemmaService.getAllBySiteAndLemmas(currentSiteEntity, lemmas);
                if (currentLemmaEntities.size() < lemmas.size()) {
                    continue;
                }
                delFrequentLemmasEntities(currentSiteEntity, currentLemmaEntities);
                lemmaEntities.addAll(currentLemmaEntities);
                siteIDs.add(currentSiteEntity.getId());
            }
            siteIDs.forEach(siteId -> {
                List<LemmaEntity> currentLemmaEntities = lemmaEntities.stream().
                        filter(lemmaEntity -> lemmaEntity.getSiteEntity().getId().equals(siteId)).toList();
                idxEntities.addAll(getIndexEntitiesWithPagesContainingAllLemmas(currentLemmaEntities));
            });
        }
        return makeSearchResponse(idxEntities);
    }


    private SearchResponse makeSearchResponse(List<IndexEntity> idxEntities) {
        if (idxEntities.isEmpty()) {
            return new SearchResponse(true, 0, new SearchData[0], null, HttpStatus.OK);
        }

        if (queryIsChanged) {
            relevanceMap = calculatePagesRelevance(idxEntities);
        }

        if (offset > relevanceMap.size()) {
            return new SearchResponse(
                    false, null, null, ResultMessage.OFFSET_TOO_LARGE.toString(), HttpStatus.BAD_REQUEST);
        }
        if (queryIsChanged) {
            pageIDs = getPageIDsArrayFromRelevanceMap(relevanceMap);
        }

        if (offset + limit > pageIDs.length) {
            limit = pageIDs.length - offset;
        }

        int coresCount = Runtime.getRuntime().availableProcessors();
        int threadsCount = Math.min(coresCount, limit);

        ThreadPoolExecutor poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsCount);
        CountDownLatch cdLatch = new CountDownLatch(threadsCount);

        SearchData[] data = new SearchData[limit];
        for (int i = 0; i < limit; i++) {
            final int I = i;
            Future<SearchData> future = poolExecutor.submit(() -> {
                SearchData searchData = getSearchData(offset + I);
                cdLatch.countDown();
                return searchData;
            });
            try {
                data[i] = future.get();
            } catch (InterruptedException | ExecutionException e) {
                log.warn("Метод future.get() в экземпляре класса {} вызвал исключение: {}",
                        this.getClass(), e.toString());
                data[i] = new SearchData("???", "Не удалось получить сведения о сайте", "???", "", "", 0f);
            }
        }

        try {
            cdLatch.await();
        } catch (InterruptedException e) {
            log.warn("Метод cdLatch.await в экземпляре класса {} вызвал исключение: {}",
                    this.getClass(), e.toString());
        }
        poolExecutor.shutdown();

        Arrays.sort(data);
        queryIsChanged = false;

        return new SearchResponse(true, pageIDs.length, data, null, HttpStatus.OK);
    }


    private SearchData getSearchData(int i) {
        PageEntity pageEntity = pageService.getById(pageIDs[i]);
        String uri = pageEntity.getPath();
        Document htmlDoc = Jsoup.parse(pageEntity.getContent());

        TitleAndSnippet titleAndSnippet = new TitleAndSnippet(morphologyService);
        String title = titleAndSnippet.getPageTitle(htmlDoc, lemmas);
        String snippet = titleAndSnippet.getSnippetForPage(htmlDoc, lemmas);
        Float relevance = relevanceMap.get(pageIDs[i]);
        if (isSingleSiteSearch) {
            return new SearchData(this.site, this.siteEntity.getName(), uri, title, snippet, relevance);
        } else {
            SiteEntity siteEntity = siteService.getById(pageEntity.getSiteEntity().getId());
            return new SearchData(siteEntity.getUrl(), siteEntity.getName(), uri, title, snippet, relevance);
        }
    }


    /**
     * @param lemmaEntities Collection of lemmaEntities.
     * @return List of indexEntities with pages, containing at once all lemmas from param 'lemmaEntities'.
     */
    private List<IndexEntity> getIndexEntitiesWithPagesContainingAllLemmas(List<LemmaEntity> lemmaEntities) {
        List<IndexEntity> firstIdxEntities = indexService.getALLByLemmaEntity(lemmaEntities.get(0));
        Set<Integer> pageIDs = new HashSet<>();
        firstIdxEntities.forEach(idxEntity -> pageIDs.add(idxEntity.getPageEntity().getId()));

        for (int i = 1; i < lemmaEntities.size(); i++) {
            if (pageIDs.isEmpty()) {
                return List.of();
            }
            List<IndexEntity> nextIdxEntities = indexService.getALLByLemmaEntity(lemmaEntities.get(i));
            final Set<Integer> pageIDsOld = new HashSet<>(pageIDs);
            pageIDs.clear();
            nextIdxEntities.forEach(idxEntity -> {
                Integer id = idxEntity.getPageEntity().getId();
                if (pageIDsOld.contains(id)) {
                    pageIDs.add(id);
                }
            });
        }

        List<IndexEntity> idxEntities = new ArrayList<>();
        final Set<Integer> pageIDsFinal = pageIDs;
        firstIdxEntities.forEach(idxEntity -> {
            if (pageIDsFinal.contains(idxEntity.getPageEntity().getId())) {
                idxEntities.add(idxEntity);
            }
        });

        return idxEntities;
    }


    /**
     * @param idxEntities List of indexEntities.
     * @return Map of page's IDs and page's relative relevance values. The map is sorted by relevance values in reverse order.
     */
    private Map<Integer, Float> calculatePagesRelevance(List<IndexEntity> idxEntities) {

        Map<Integer, Integer> absoluteRelevanceMap = new HashMap<>();
        idxEntities.forEach(idxEntity ->
                absoluteRelevanceMap.merge(idxEntity.getPageEntity().getId(), idxEntity.getRank(), Integer::sum)
        );
        int maxRank = absoluteRelevanceMap.values().stream().max(Integer::compare).orElse(0);
        assert maxRank > 0;

        Map<Integer, Float> relativeRelevanceMap = new HashMap<>();
        absoluteRelevanceMap.forEach((pageId, absoluteRelevance) ->
                relativeRelevanceMap.put(pageId, (float) absoluteRelevance / maxRank));

        Map<Integer, Float> relevanceSortedMap = new LinkedHashMap<>();
        relativeRelevanceMap.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).
                forEach(entry -> relevanceSortedMap.put(entry.getKey(), entry.getValue()));

        return relevanceSortedMap;
    }


    private Integer[] getPageIDsArrayFromRelevanceMap(Map<Integer, Float> relevanceMap) {
        Integer[] pageIDs = new Integer[relevanceMap.size()];
        AtomicInteger i = new AtomicInteger();
        relevanceMap.forEach((pageId, relevance) -> {
            pageIDs[i.get()] = pageId;
            i.getAndIncrement();
        });
        return pageIDs;
    }


    /**
     * Deletes all entities of lemmas found on all pages of the specified site or on more the site pages
     * than specified in 'MAX_NUM_PAGES' property,
     * but leaves at least one entity for lemma with the least occurrence.
     */
    private void delFrequentLemmasEntities(SiteEntity siteEntity, List<LemmaEntity> lemmaEntities) {
        lemmaEntities.sort(Comparator.comparing(LemmaEntity::getFrequency).reversed());
        Iterator<LemmaEntity> iterator = lemmaEntities.iterator();
        while (lemmaEntities.size() > 1 && iterator.hasNext()) {
            LemmaEntity lemmaEntity = iterator.next();
            if (lemmaEntity.getFrequency() == pageService.getCountBySiteEntity(siteEntity) ||
                    lemmaEntity.getFrequency() > MAX_NUM_PAGES) {
                iterator.remove();
            }
        }
    }

}
