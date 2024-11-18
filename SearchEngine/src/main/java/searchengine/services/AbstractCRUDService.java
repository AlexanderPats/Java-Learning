package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Collection;
import java.util.List;

/**
 * @param <T> Entity Type
 * @param <ID> PrimaryKey Type
 */
@Getter
@RequiredArgsConstructor
@Slf4j
public abstract class AbstractCRUDService<T, ID> implements CRUDService<T, ID> {

    private final JpaRepository<T, ID> repository;

    @Override
    public T getById(ID id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    public Collection<T> getAll() {
        log.info("Get all object from DB");
        return repository.findAll();
    }

    @Override
    public void save(T entity) {
        log.info("Save {}-type object to DB", entity.getClass().getName());
       repository.save(entity);
    }

    @Override
    public void saveAll(Iterable<T> entities) {
        log.info("Save {}-type collection to DB", entities.getClass().getName());
        repository.saveAll(entities);
    }

    @Override
    public void deleteById(ID id) {
        log.info("Delete row with id: {} from DB", id);
        repository.deleteById(id);
    }
}
