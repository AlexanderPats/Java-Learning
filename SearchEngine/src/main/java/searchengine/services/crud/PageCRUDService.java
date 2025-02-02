package searchengine.services.crud;

import jakarta.persistence.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class PageCRUDService implements CRUDService<PageEntity, Integer> {

    private final PageRepository pageRepository;
    private final String tableName = PageEntity.class.getAnnotation(Table.class).name();

    @Override
    public PageEntity getById(Integer id) {
        log.debug("Getting pageEntity by id {} from table '{}'", id, tableName);
        return pageRepository.findById(id).orElse(null);
    }

    public PageEntity getBySiteAndPath(SiteEntity siteEntity, String path) {
        log.info("Getting page '{}' from table '{}'", siteEntity.getUrl().concat(path), tableName);
        return pageRepository.findBySiteEntityAndPath(siteEntity, path);
    }

    public Integer getIdBySiteAndPath(SiteEntity siteEntity, String path) {
        log.debug("Getting page '{}' from table '{}'", siteEntity.getUrl().concat(path), tableName);
        return pageRepository.findIdBySiteEntityAndPath(siteEntity, path);
    }

    public int getCount() {
        log.debug("Getting all rows count from table '{}'", tableName);
        return (int) pageRepository.count();
    }

    public int getCountBySiteEntity(SiteEntity siteEntity) {
        log.debug("Getting rows count with site_id {} from table '{}'", siteEntity.getId(), tableName);
        return pageRepository.countBySiteEntity(siteEntity);
    }

    @Override
    public PageEntity save(PageEntity pageEntity) {
        String pageUrl = pageEntity.getSiteEntity().getUrl().concat( pageEntity.getPath() );
        log.info("Saving page '{}' to table '{}'", pageUrl, tableName);
        try { pageRepository.save(pageEntity); }
        catch (Exception e) {
            log.warn("Error while saving pageEntity '{}' to table '{}': {}", pageUrl, tableName, e.toString());
        }
        return pageEntity;
    }

    @Override
    public void deleteById(Integer id) {
        log.info("Deleting row with id {} from table '{}'", id, tableName);
        pageRepository.deleteById(id);
    }

}
