package searchengine.repositories;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteEntity;
import searchengine.model.SiteStatus;

import java.time.Instant;
import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    SiteEntity findByUrl(String url);

    List<SiteEntity> findAllByStatus(SiteStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE SiteEntity SET status = ?2, lastError = ?3, statusTime = ?4 WHERE url = ?1")
    void updateStatusByUrl(String url, SiteStatus status, String lastError, Instant statusTime);

    @Transactional
    void deleteByUrl(String url);

}
