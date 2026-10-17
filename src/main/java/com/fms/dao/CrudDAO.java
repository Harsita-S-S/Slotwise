package com.fms.dao;

import java.util.List;
import java.util.Optional;

/**
 * Generic CRUD Data Access Object interface as specified in SRS Section 7.2.
 * Implemented by all entity DAOs across the system.
 *
 * @param <T> Entity type
 */
public interface CrudDAO<T> {

    /**
     * Finds an entity by its primary key identifier.
     *
     * @param id The primary key ID
     * @return Optional containing the entity if found, empty otherwise
     */
    Optional<T> findById(int id);

    /**
     * Retrieves all active/available entities of this type.
     *
     * @return List of entities
     */
    List<T> findAll();

    /**
     * Persists a new entity into the database.
     *
     * @param entity The entity to save
     * @return The persisted entity with generated primary key
     */
    T save(T entity);

    /**
     * Updates an existing entity record in the database.
     *
     * @param entity The entity to update
     * @return true if update affected 1 or more rows, false otherwise
     */
    boolean update(T entity);

    /**
     * Deletes or soft-deactivates an entity by its primary key.
     *
     * @param id The entity ID to delete
     * @return true if successful, false otherwise
     */
    boolean delete(int id);
}
