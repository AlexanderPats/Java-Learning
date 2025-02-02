package searchengine.services.crud;

import jakarta.persistence.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.model.SiteEntity;
import searchengine.model.SiteStatus;
import searchengine.repositories.SiteRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteCRUDService implements CRUDService<SiteEntity, Integer> {

    private final SiteRepository siteRepository;
    private final String tableName = SiteEntity.class.getAnnotation(Table.class).name();

    @Override
    public SiteEntity getById(Integer id) {
        log.debug("Getting site by id {} from table '{}'", id, tableName);
        return siteRepository.findById(id).orElse(null);
    }

    public SiteEntity getByUrl(String url) {
        log.debug("Getting site '{}' from table '{}'", url, tableName);
        return siteRepository.findByUrl(url);
    }

    public List<SiteEntity> getAll() {
        log.debug("Getting all sites from table '{}'", tableName);
        return siteRepository.findAll();
    }

    public List<SiteEntity> getAllByStatus(SiteStatus status) {
        log.debug("Getting all sites with status {} from table '{}'", status, tableName);
        return siteRepository.findAllByStatus(status);
    }

    public int getCount() {
        log.debug("Getting all rows count from table '{}'", tableName);
        return (int) siteRepository.count();
    }

    @Override
    public SiteEntity save(SiteEntity siteEntity) {
        String siteUrl = siteEntity.getUrl();
        log.info("Saving site '{}' to table '{}'", siteUrl, tableName);
        try { siteRepository.save(siteEntity); }
        catch (Exception e) {
            log.warn("Error while saving siteEntity '{}' to table '{}': {}", siteUrl, tableName, e.toString());
        }
        return siteEntity;
    }

    public void updateStatusByUrl(String url, SiteStatus newSiteStatus, String lastError) {
        log.info("Changing site status for site '{}' to: '{}' in table '{}'", url, newSiteStatus, tableName);
        siteRepository.updateStatusByUrl( url, newSiteStatus, lastError, Instant.now() );
    }

    @Override
    public void deleteById(Integer id) {
        log.info("Deleting row with id {} from table '{}'", id, tableName);
        siteRepository.deleteById(id);
    }

    public void deleteByUrl(String url) {
        log.info("Deleting site '{}' from table '{}'", url, tableName);
        siteRepository.deleteByUrl(url);
    }

}
