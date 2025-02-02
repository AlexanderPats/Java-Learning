package searchengine.repositories;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    LemmaEntity findBySiteEntityAndLemma(SiteEntity siteEntity, String lemma);

    Integer countBySiteEntity(SiteEntity siteEntity);

    @Modifying
    @Transactional
    @Query( nativeQuery = true,
            value = "INSERT INTO lemma(site_id, lemma, frequency) VALUES (?1, ?2, 1) " +
                    "ON DUPLICATE KEY UPDATE frequency = frequency + 1")
    void saveOrUpdate(Integer siteId, String lemma);

    @Modifying
    @Transactional
    @Query("UPDATE LemmaEntity SET frequency = frequency - 1 WHERE siteEntity = ?1 and lemma = ?2")
    void decreaseFrequencyByOne(SiteEntity siteEntity, String lemma);

}
