package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteEntity;
import searchengine.model.SiteStatus;

import java.time.Instant;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    @Modifying
    @Query("UPDATE SiteEntity SET status = ?2, lastError = ?3, statusTime = ?4 WHERE url = ?1")
    void updateStatusByUrl(String url, SiteStatus status, String lastError, Instant statusTime);

    SiteEntity getByUrl(String url);

    void deleteByUrl(String url);

}
