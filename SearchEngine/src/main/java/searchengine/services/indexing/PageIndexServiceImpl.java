package searchengine.services.indexing;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.UnexpectedRollbackException;
import searchengine.config.IndexingSettings;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.services.ResultMessage;
import searchengine.services.crud.IndexCRUDService;
import searchengine.services.crud.LemmaCRUDService;
import searchengine.services.crud.PageCRUDService;
import searchengine.services.morphology.MorphologyService;

import java.util.*;

@Service
@Slf4j
public class PageIndexServiceImpl implements PageIndexService {

    // нижеследующие статические константы при необходимости дополнить/сократить
    static final int[] SUCCESSFUL_HTTP_CODES = {200, 203};
    static final String[] TEXT_CONTENT_TYPES = {"text/html"};
    static final String[] REJECTED_FILE_EXTENSIONS =
            {".css", ".js", ".json", ".ico", ".webp", ".png", ".jpg", ".jpeg", ".tga", ".svg", ".bmp",
                    ".zip", ".rar", ".7z", ".mp3", ".mp4", ".mkv", ".avi", ".mpg", ".mpeg",
                    ".docx", ".xlsx", ".pptx", ".doc", ".xls", ".ppt", ".odt", ".odf", ".odp", ".pdf"
            };

    private final PageCRUDService pageService;
    private final LemmaCRUDService lemmaService;
    private final IndexCRUDService indexService;
    private final MorphologyService morphologyService;

    public PageIndexServiceImpl(PageCRUDService pageService,
                                LemmaCRUDService lemmaService,
                                IndexCRUDService indexService,
                                MorphologyService morphologyService) {
        this.pageService = pageService;
        this.lemmaService = lemmaService;
        this.indexService = indexService;
        this.morphologyService = morphologyService;
    }

    public void indexAndSavePage(SiteEntity siteEntity, String path, Document htmlDoc) {
        PageEntity pageEntity = savePage(siteEntity, path, htmlDoc);
        if (pageEntity.getId() == null) {
            return;
        } // Если объект не связался с персистент контекстом, значит он уже есть в БД

        Map<String, Integer> lemmasMentionsOnPage = getLemmasMentionsOnPage(htmlDoc);
        Set<String> lemmas = lemmasMentionsOnPage.keySet();

        saveLemmas(siteEntity.getId(), lemmas);

        saveIndexes(siteEntity, pageEntity, lemmasMentionsOnPage);
    }


    @Override
    public void indexAndSaveSinglePage(SiteEntity siteEntity, String path, Document htmlDoc) {
        PageEntity oldPageEntity = pageService.getBySiteAndPath(siteEntity, path);
        if (oldPageEntity != null) {
            Document oldHtmlDoc = Jsoup.parse(oldPageEntity.getContent());
            String oldPageText = oldHtmlDoc.body().text();
            Set<String> oldPageLemmas = morphologyService.getUniqueLemmasFromText(oldPageText);
            pageService.deleteById(oldPageEntity.getId());
            lemmaService.decreaseLemmasFrequenciesByOne(siteEntity, oldPageLemmas);
        }
        indexAndSavePage(siteEntity, path, htmlDoc);
    }


    private PageEntity savePage(SiteEntity siteEntity, String path, Document htmlDoc) {
        int responseCode = htmlDoc.connection().response().statusCode();
        String htmlPage = htmlDoc.toString();
        PageEntity pageEntity = new PageEntity(siteEntity, path, responseCode, htmlPage);
        return pageService.save(pageEntity);
    }


    private void saveLemmas(Integer siteId, Iterable<String> lemmas) {
//        1) Вариант с дедлоками, более производительный.
        while (true) {
            try {
                lemmaService.saveOrUpdateLemmas(siteId, lemmas);
                break;
            } catch (UnexpectedRollbackException e) {
                log.warn("Restart transaction: save lemmas, due to the error: {}", e.toString());
            }
        }
//        2) Вариант без дедлоков, более медленный.
//        Если использовать этот метод, то также пропадают дэдлоки в методе indexService.saveAll(indexEntities)
//        lemmaService.saveOrUpdateLemmasWithoutDeadLocks(siteId, lemmas);
    }


    private void saveIndexes(SiteEntity siteEntity, PageEntity pageEntity, Map<String, Integer> lemmasMentionsOnPage) {
        List<IndexEntity> indexEntities = new LinkedList<>();
        Set<String> lemmas = lemmasMentionsOnPage.keySet();
        Map<String, LemmaEntity> lemmaEntities = lemmaService.getAllToMapBySiteAndLemmas(siteEntity, lemmas);

        lemmasMentionsOnPage.forEach((lemma, rank) -> {
            LemmaEntity lemmaEntity = lemmaEntities.get(lemma);
            IndexEntity indexEntity = new IndexEntity(pageEntity, lemmaEntity, rank);
            indexEntities.add(indexEntity);
        });
        indexService.saveAll(indexEntities);
    }


    @Override
    public boolean pageIsPresentInDB(SiteEntity siteEntity, String path) {
        return pageService.getIdBySiteAndPath(siteEntity, path) != null;
    }


    @Override
    public Document getHtmlDocument(String url, String userAgent, String referrer) {
        Document htmlDoc = null;
        try {
            htmlDoc = Jsoup.connect(url).userAgent(userAgent).referrer(referrer).get();
        } catch (Exception e) {
            log.warn("Ошибка при открытии страницы {}: {}", url, e.toString());
        }
        return htmlDoc;
    }


    @Override
    public ResultMessage checkHTMLDocument(Document htmlDoc) {
        if (htmlDoc == null) {
            return ResultMessage.PAGE_NOT_FOUND;
        }

        int responseCode = htmlDoc.connection().response().statusCode();
        if (!isSuccessfulCode(responseCode)) {
            return ResultMessage.PAGE_NOT_FOUND;
        }

        String contentType = htmlDoc.connection().response().contentType();
        if (!hasAcceptedContent(contentType)) {
            return ResultMessage.URL_HAS_NO_TEXT_CONTENT;
        }

        return ResultMessage.PAGE_IS_CHECKED;
    }


    /**
     * @return path in lower case without siteUrl, symbol '#' (if exists) and substring following it.
     * If param 'exclude-url-parameters' in settings-file is true,
     * also will be cut symbol '?' (if exists) and substring following it.
     * @throws RuntimeException if path is too long (according param 'path-max-length' in settings-file)
     *                          or url has no text content.
     */
    @Override
    public String normalizePath(String path, String siteUrl, IndexingSettings indexingSettings)
            throws RuntimeException {

        if (path.equals("/") || path.equals(siteUrl)) {
            return "/";
        }

        String normalizedPath = path.toLowerCase();

        if (normalizedPath.startsWith(siteUrl)) {
            normalizedPath = normalizedPath.substring(siteUrl.length());
        }
        if (normalizedPath.indexOf('#') >= 0) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.indexOf('#'));
        }
        String pathWoParams = normalizedPath;
        if (normalizedPath.indexOf('?') >= 0) {
            pathWoParams = normalizedPath.substring(0, normalizedPath.indexOf('?'));
        }
        if (isRejectedPath(pathWoParams)) {
            log.debug(ResultMessage.URL_HAS_NO_TEXT_CONTENT.toString().
                    concat(". URL: {}"), siteUrl.concat(normalizedPath));
            throw new RuntimeException(ResultMessage.URL_HAS_NO_TEXT_CONTENT.toString());
        }
        if (indexingSettings.isExcludeUrlParameters()) {
            normalizedPath = pathWoParams;
        }

        if (normalizedPath.length() > indexingSettings.getPathMaxLength()) {
            log.warn("Индексация страницы невозможна, т.к. длина ее относительного пути ({} символов) превышает " +
                            "максимально допустимое значение ({} символов). URL: {}",
                    normalizedPath.length(), indexingSettings.getPathMaxLength(), siteUrl.concat(normalizedPath));
            throw new RuntimeException(ResultMessage.URL_TOO_LONG.toString());
        }
        return normalizedPath;
    }


    private Map<String, Integer> getLemmasMentionsOnPage(Document htmlDoc) {
        String text = htmlDoc.body().text();
        return morphologyService.getMapOfLemmasMentions(text);
    }


    private boolean isSuccessfulCode(int httpCode) {
        boolean codeStatus = false;
        for (int i : SUCCESSFUL_HTTP_CODES) {
            if (i == httpCode) {
                codeStatus = true;
                break;
            }
        }
        return codeStatus;
    }


    private boolean hasAcceptedContent(String contentType) {
        boolean hasAcceptedContent = false;
        for (String s : TEXT_CONTENT_TYPES) {
            assert contentType != null;
            if (contentType.startsWith(s)) {
                hasAcceptedContent = true;
                break;
            }
        }
        return hasAcceptedContent;
    }


    private boolean isRejectedPath(String pathWoParams) {
        boolean isRejectedPath = false;
        for (String rejected_string : REJECTED_FILE_EXTENSIONS) {
            if (pathWoParams.toLowerCase().endsWith(rejected_string)) {
                isRejectedPath = true;
                break;
            }
        }
        return isRejectedPath;
    }

}
