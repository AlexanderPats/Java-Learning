package searchengine.services;

import jakarta.persistence.Table;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.model.SiteEntity;
import searchengine.model.SiteStatus;
import searchengine.repositories.SiteRepository;

import java.time.Instant;
import java.util.Collection;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteCRUDService implements CRUDService<SiteEntity, Integer> {

    private final SiteRepository siteRepository;

    private final String tableName = SiteEntity.class.getAnnotation(Table.class).name();

    @Override
    public SiteEntity getById(Integer id) {
        log.info("Get site by id {} from table '{}'", id, tableName);
        return siteRepository.findById(id).orElse(null);
    }

    public SiteEntity getByUrl(String url) {
        log.info("Get site '{}' from table '{}'", url, tableName);
        return siteRepository.getByUrl(url);
    }

    @Override
    public Collection<SiteEntity> getAll() {
        log.info("Get all rows from table '{}'", tableName);
        return siteRepository.findAll();
    }

    @Override
    public void save(SiteEntity entity) {
        log.info("Save site '{}' to table '{}'", entity.getUrl(), tableName);
        siteRepository.save(entity);
    }

    @Override
    public void saveAll(Iterable<SiteEntity> entities) {
        log.info("Save collection of sites to table '{}'", tableName);
        siteRepository.saveAll(entities);
    }

    @Override
    public void deleteById(Integer id) {
        log.info("Delete row with id {} from table '{}'", id, tableName);
        siteRepository.deleteById(id);
    }

    @Transactional
    public void changeSiteStatusByUrl(String url, SiteStatus newSiteStatus, String lastError) {
        log.info("Change site '{}' status to: '{}' in table '{}'", url, newSiteStatus, tableName);
        siteRepository.updateStatusByUrl(url, newSiteStatus, lastError, Instant.now());
    }

    @Transactional
    public void deleteByUrl(String url) {
        log.info("Delete site '{}' from table '{}'", url, tableName);
        siteRepository.deleteByUrl(url);
    }

}
