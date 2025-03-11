package searchengine.services.crud;

import jakarta.persistence.Table;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LemmaCRUDService implements CRUDService<LemmaEntity, Integer> {

    private final LemmaRepository lemmaRepository;
    private final String tableName = LemmaEntity.class.getAnnotation(Table.class).name();

    @Override
    public LemmaEntity getById(Integer id) {
        log.info("Getting lemmaEntity by id {} from table '{}'", id, tableName);
        return lemmaRepository.findById(id).orElse(null);
    }

    @Transactional
    public List<LemmaEntity> getAllBySiteAndLemmas(SiteEntity siteEntity, Iterable<String> lemmas) {
        log.debug("Getting all lemmaEntities with site '{} and lemmas: {}' from table '{}'",
                siteEntity.getUrl(), lemmas, tableName);
        List<LemmaEntity> lemmaEntities = new ArrayList<>();
        lemmas.forEach(lemma -> {
            LemmaEntity lemmaEntity = lemmaRepository.findBySiteEntityAndLemma(siteEntity, lemma);
            if (lemmaEntity != null) {
                lemmaEntities.add(lemmaEntity);
            }
        });
        return lemmaEntities;
    }

    /**
     * Returns a Map where key is lemma and value is LemmaEntity
     */
    @Transactional
    public Map<String, LemmaEntity> getAllToMapBySiteAndLemmas(SiteEntity siteEntity, Iterable<String> lemmas) {
        log.debug("Getting to map all lemmaEntities with site '{} and lemmas: {}' from table '{}'",
                siteEntity.getUrl(), lemmas, tableName);
        Map<String, LemmaEntity> lemmaEntities = new HashMap<>();
        lemmas.forEach(lemma -> {
            LemmaEntity lemmaEntity = lemmaRepository.findBySiteEntityAndLemma(siteEntity, lemma);
            if (lemmaEntity != null) {
                lemmaEntities.put(lemma, lemmaEntity);
            }
        });
        return lemmaEntities;
    }

    public int getCount() {
        log.debug("Getting all rows count from table '{}'", tableName);
        return (int) lemmaRepository.count();
    }

    public int getCountBySiteEntity(SiteEntity siteEntity) {
        log.debug("Getting rows count with site_id {} from table '{}'", siteEntity.getId(), tableName);
        return lemmaRepository.countBySiteEntity(siteEntity);
    }

    @Override
    public LemmaEntity save(LemmaEntity lemmaEntity) {
        String siteUrl = lemmaEntity.getSiteEntity().getUrl();
        String lemmaWord = lemmaEntity.getLemma();
        log.info("Saving lemma '{}: {}' to table '{}'", siteUrl, lemmaWord, tableName);
        try {
            lemmaRepository.save(lemmaEntity);
        } catch (Exception e) {
            log.warn("Error while saving lemmaEntity '{}: {}' to table '{}': {}",
                    siteUrl, lemmaWord, tableName, e.toString());
        }
        return lemmaEntity;
    }

    @Transactional
    public void saveOrUpdateLemmas(Integer siteId, Iterable<String> lemmas) {
        log.info("Saving lemmas: '{}' to table '{}'", lemmas, tableName);
        try {
            lemmas.forEach(lemma -> lemmaRepository.saveOrUpdate(siteId, lemma));
        } catch (Exception e) {
            log.warn("Error while saving lemmas '{}' to table '{}': {}", lemmas, tableName, e.toString());
        }
    }

    public void saveOrUpdateLemmasWithoutDeadLocks(Integer siteId, Iterable<String> lemmas) {
        saveOrUpdateLemmas(siteId, lemmas);
    }

    public void decreaseLemmasFrequenciesByOne(SiteEntity siteEntity, Set<String> lemmas) {
        log.info("Decreasing counters by 1 for lemmas: '{}' in table '{}'", lemmas, tableName);
        try {
            lemmas.forEach(lemma -> lemmaRepository.decreaseFrequencyByOne(siteEntity, lemma));
        } catch (Exception e) {
            log.warn("Error while decreasing counters for lemmas: '{}' to table '{}': {}",
                    lemmas, tableName, e.toString());
        }
    }

    @Override
    public void deleteById(Integer id) {
        log.info("Deleting row with id {} from table '{}'", id, tableName);
        lemmaRepository.deleteById(id);

    }
}
