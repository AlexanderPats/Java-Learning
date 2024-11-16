package searchengine.services;

import java.util.Collection;

/**
 * @param <T> Entity Type
 * @param <ID> PrimaryKey Type
 */

public interface CRUDService<T, ID> {

    T getById(ID id);
    Collection<T> getAll();
    void save(T item);
    void saveAll(Iterable<T> entities);
    void deleteById(ID id);

}
