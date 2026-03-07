package tech.yesboss.memory.manager;

import tech.yesboss.memory.model.Preference;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;

import java.util.List;

/**
 * MemoryManager - Three-layer association management interface.
 *
 * <p>Responsible for coordinating the associations between Resource, Snippet, and Preference layers,
 * providing unified creation, update, and vectorization management capabilities.</p>
 *
 * <p>Core responsibilities:
 * <ol>
 *   <li>Manage creation and updates of three-layer data</li>
 *   <li>Maintain associations between layers</li>
 *   <li>Coordinate batch and vectorization operations</li>
 * </ol>
 */
public interface MemoryManager {

    // ==================== Resource Layer Operations ====================

    /**
     * Save a single Resource.
     * Creates a conversation resource and generates vector index.
     *
     * @param resource Resource object
     * @return Saved Resource object
     * @throws MemoryManagerException if save operation fails
     */
    Resource saveResource(Resource resource);

    /**
     * Batch save Resources.
     * Creates multiple conversation resources and generates vector indices.
     *
     * @param resources List of Resource objects
     * @return List of saved Resource objects
     * @throws MemoryManagerException if batch save operation fails
     */
    List<Resource> saveResources(List<Resource> resources);

    // ==================== Snippet Layer Operations ====================

    /**
     * Save a single Snippet.
     * Extracts structured memory from Resource and associates with Preferences.
     *
     * @param snippet Snippet object
     * @return Saved Snippet object
     * @throws MemoryManagerException if save operation fails
     */
    Snippet saveSnippet(Snippet snippet);

    /**
     * Batch save Snippets.
     * Creates multiple structured memories and associates with Preferences.
     *
     * @param snippets List of Snippet objects
     * @return List of saved Snippet objects
     * @throws MemoryManagerException if batch save operation fails
     */
    List<Snippet> saveSnippets(List<Snippet> snippets);

    // ==================== Preference Layer Operations ====================

    /**
     * Save a single Preference (for creation).
     *
     * @param preference Preference object
     * @return Saved Preference object
     * @throws MemoryManagerException if save operation fails
     */
    Preference savePreference(Preference preference);

    /**
     * Update Preference's summary and embedding.
     * Updates Preference's aggregated information when associated Snippets change.
     *
     * @param preferenceId Preference ID
     * @param summary Updated summary
     * @param embedding Vector representation of the summary
     * @throws MemoryManagerException if update operation fails
     */
    void updatePreferenceSummary(String preferenceId, String summary, float[] embedding);

    // ==================== Batch Vectorization Operations ====================

    /**
     * Batch update Resources' vectors.
     * Generates or updates vector indices for Resources.
     *
     * @param resources List of Resource objects
     * @throws MemoryManagerException if batch update fails
     */
    void batchUpdateResourceEmbeddings(List<Resource> resources);

    /**
     * Batch update Snippets' vectors.
     * Generates or updates vector indices for Snippets.
     *
     * @param snippets List of Snippet objects
     * @throws MemoryManagerException if batch update fails
     */
    void batchUpdateSnippetEmbeddings(List<Snippet> snippets);

    /**
     * Batch update Preferences' vectors.
     * Generates or updates vector indices for Preferences.
     *
     * @param preferences List of Preference objects
     * @throws MemoryManagerException if batch update fails
     */
    void batchUpdatePreferenceEmbeddings(List<Preference> preferences);

    /**
     * Check if the MemoryManager is available and properly initialized.
     *
     * @return true if operational, false otherwise
     */
    boolean isAvailable();
}
