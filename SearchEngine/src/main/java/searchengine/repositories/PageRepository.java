package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.SiteEntity;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {

    Page getBySiteEntityAndPath(SiteEntity siteEntity, String path);
}
