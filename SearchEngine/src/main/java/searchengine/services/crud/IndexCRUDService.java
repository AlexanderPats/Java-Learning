package searchengine.services.crud;

import jakarta.persistence.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.repositories.IndexRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexCRUDService implements CRUDService<IndexEntity, Integer> {

    private final IndexRepository indexRepository;
    private final String tableName = IndexEntity.class.getAnnotation(Table.class).name();

    @Override
    public IndexEntity getById(Integer id) {
        log.debug("Getting index entity by id {} from table {}", id, tableName);
        return indexRepository.findById(id).orElse(null);
    }

    public List<IndexEntity> getALLByLemmaEntity(LemmaEntity lemmaEntity) {
        log.debug("Getting all index entities by lemma entity 'id: {}, lemma: {}' from table {}",
                lemmaEntity.getLemma(), lemmaEntity.getLemma(), tableName);
        return indexRepository.findALLByLemmaEntity(lemmaEntity);
    }

    @Override
    public IndexEntity save(IndexEntity indexEntity) {
        int pageId =  indexEntity.getPageEntity().getId();
        int lemmaId = indexEntity.getLemmaEntity().getId();
        log.info("Saving index entity with page_id {} and lemma_id {} to table {}",
               pageId,lemmaId, tableName);
        try { indexRepository.save(indexEntity); }
        catch (Exception e) {
            log.warn("Error while saving lemmaEntity with page_id {} and lemma_id {} to table {}: {}",
                    pageId, lemmaId, tableName, e.toString());
        }
        return indexEntity;
    }

    public void saveAll(Iterable<IndexEntity> indexEntities) {
        log.info("Saving all indexes to table {}", tableName);
        while (true) {
            try {
                indexRepository.saveAll(indexEntities);
                break;
            }
            catch (CannotAcquireLockException e) {
                log.warn("Error while saving indexes to table {}: {}", tableName, e.toString());
                log.warn("Restart transaction: save indexes due to the error: {}", e.toString());
            }
        }
    }

    @Override
    public void deleteById(Integer id) {
        log.info("Deleting row with id {} from table {}", id, tableName);
        indexRepository.deleteById(id);
    }
}
