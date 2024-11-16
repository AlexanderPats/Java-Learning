package searchengine.services;

import jakarta.persistence.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;

import java.util.Collection;

@Service
@RequiredArgsConstructor
@Slf4j
public class PageCRUDService implements CRUDService<Page, Integer> {

    private final PageRepository pageRepository;

    private final String tableName = Page.class.getAnnotation(Table.class).name();

    @Override
    public Page getById(Integer id) {
        log.info("Get page by id {} from table '{}'", id, tableName);
        return pageRepository.findById(id).orElse(null);
    }

    public Page getBySiteAndPath(SiteEntity siteEntity, String path) {
        log.info("Get page '{}' from table '{}'", siteEntity.getUrl() + path, tableName);
        return pageRepository.getBySiteEntityAndPath(siteEntity, path);
    }

    @Override
    public Collection<Page> getAll() {
        log.info("Get all rows from table '{}'", tableName);
        return pageRepository.findAll();
    }

    @Override
    public void save(Page entity) {
        log.info("Save page '{}' to table '{}'", entity.getSiteEntity().getUrl() + entity.getPath(), tableName);
        pageRepository.save(entity);
    }

    @Override
    public void saveAll(Iterable<Page> entities) {
        log.info("Save collection of pages to table '{}'", tableName);
        pageRepository.saveAll(entities);
    }

    @Override
    public void deleteById(Integer id) {
        log.info("Delete row with id {} from table '{}'", id, tableName);
        pageRepository.deleteById(id);
    }

}
