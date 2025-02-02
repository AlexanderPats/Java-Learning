package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {

    PageEntity findBySiteEntityAndPath(SiteEntity siteEntity, String path);

    Integer countBySiteEntity(SiteEntity siteEntity);

    @Query("SELECT id FROM PageEntity WHERE siteEntity = ?1 AND path = ?2")
    Integer findIdBySiteEntityAndPath(SiteEntity siteEntity, String path);

}
