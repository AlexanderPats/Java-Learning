package searchengine.services.crud;

/**
 * @param <T> Entity Type
 * @param <ID> PrimaryKey Type
 */
public interface CRUDService<T, ID> {

    T getById(ID id);
    T save(T item);
    void deleteById(ID id);

}
