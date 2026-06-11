package pumpking.lib.database

abstract class Repository<K, V>(protected val provider: DatabaseProvider) {
    
    /**
     * Initializes the repository (e.g., creating tables).
     */
    abstract fun init()

    /**
     * Loads an entity by its key.
     */
    abstract fun load(id: K): V?

    /**
     * Saves an entity to the database.
     */
    abstract fun save(entity: V)

    /**
     * Deletes an entity by its key.
     */
    abstract fun delete(id: K)
}
