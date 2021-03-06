/* ==================================================================
 *  Eniware Open Source:Nikolai Manchev
 *  Apache License 2.0
 * ==================================================================
 */

package org.eniware.edge.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;

import org.eniware.edge.settings.SettingSpecifierProvider;

/**
 * Manager API for Edge-level backups.
 * 
 * @version 1.2
 */
public interface BackupManager extends SettingSpecifierProvider {

	/**
	 * A property key for a comma-delimited list of
	 * {@link BackupResourceProvider#getKey()} values to limit the backup
	 * operation to. If not specified, all providers are included.
	 * 
	 * @since 1.1
	 */
	String RESOURCE_PROVIDER_FILTER = "ResourceProviderFilter";

	/**
	 * A property key a {@link Backup} {@code key} value.
	 * 
	 * @since 1.2
	 */
	String BACKUP_KEY = "BackupKey";

	/**
	 * Get the active {@link BackupService}.
	 * 
	 * @return the BackupService, or <em>null</em> if none configured
	 */
	BackupService activeBackupService();

	/**
	 * Get a {@link Iterator} of {@link BackupResource} needing to be backed up.
	 * 
	 * @return
	 */
	Iterable<BackupResource> resourcesForBackup();

	/**
	 * Create a new Backup, using the active backup service.
	 * 
	 * @return the backup, or <em>null</em> if none could be created
	 */
	Backup createBackup();

	/**
	 * Create a new Backup, using the active backup service.
	 * 
	 * @param props
	 *        An optional set of properties to customize the backup with.
	 * @return the backup, or <em>null</em> if none could be created
	 * @since 1.1
	 */
	Backup createBackup(Map<String, String> props);

	/**
	 * Create a new Backup, using the active backup service, in the background.
	 * This method will immediately return a Future where you can track the
	 * status of the background backup, if desired.
	 * 
	 * @return the backup, or <em>null</em> if none could be created
	 */
	Future<Backup> createAsynchronousBackup();

	/**
	 * Create a new Backup, using the active backup service, in the background.
	 * This method will immediately return a Future where you can track the
	 * status of the background backup, if desired.
	 * 
	 * @param props
	 *        An optional set of properties to customize the backup with.
	 * @return the backup, or <em>null</em> if none could be created
	 * @since 1.1
	 */
	Future<Backup> createAsynchronousBackup(Map<String, String> props);

	/**
	 * Restore all resources from a given backup.
	 * 
	 * @param backup
	 *        the backup to restore
	 */
	void restoreBackup(Backup backup);

	/**
	 * Restore all resources from a given backup.
	 * 
	 * @param backup
	 *        the backup to restore
	 * @param props
	 *        An optional set of properties to customize the backup with.
	 * @since 1.1
	 */
	void restoreBackup(Backup backup, Map<String, String> props);

	/**
	 * Export a backup zip archive.
	 * 
	 * @param backupKey
	 *        the backup to export
	 * @param out
	 *        the output stream to export to
	 * @throws IOException
	 *         if any IO error occurs
	 */
	void exportBackupArchive(String backupKey, OutputStream out) throws IOException;

	/**
	 * Export a backup zip archive.
	 * 
	 * @param backupKey
	 *        the backup to export
	 * @param out
	 *        the output stream to export to
	 * @param props
	 *        An optional set of properties to customize the backup with.
	 * @throws IOException
	 *         if any IO error occurs
	 * @since 1.1
	 */
	void exportBackupArchive(String backupKey, OutputStream out, Map<String, String> props)
			throws IOException;

	/**
	 * Import a backup archive into the active backup service.
	 * 
	 * This method can import an archive exported via
	 * {@link #exportBackupArchive(String, OutputStream)}. Once imported, the
	 * backup will appear as a new backup in the active backup service.
	 * 
	 * @param archive
	 *        the archive input stream to import
	 * @throws IOException
	 *         if any IO error occurs
	 */
	Future<Backup> importBackupArchive(InputStream archive) throws IOException;

	/**
	 * Import a backup archive with properties.
	 * 
	 * This method can import an archive exported via
	 * {@link #exportBackupArchive(String, OutputStream)}. The
	 * {@link #RESOURCE_PROVIDER_FILTER} property can be used to filter which
	 * provider resources are included in the imported backup. The
	 * {@link #BACKUP_KEY} can be used to provide a hint of the original backup
	 * key (and possibly date). Once imported, the backup will appear as a new
	 * backup in the active backup service.
	 * 
	 * @param archive
	 *        the archive input stream to import
	 * @param props
	 *        An optional set of properties to customize the backup with.
	 * @throws IOException
	 *         if any IO error occurs
	 * @since 1.1
	 */
	Future<Backup> importBackupArchive(InputStream archive, Map<String, String> props)
			throws IOException;

	/**
	 * Get metadata about a particular backup.
	 * 
	 * @param key
	 *        The key of the backup to get the information for.
	 * @param locale
	 *        The desired locale of the information, or {@code null} for the
	 *        system locale.
	 * @return The backup info, or {@code null} if no backup is available for
	 *         the given key.
	 * @since 1.2
	 */
	BackupInfo infoForBackup(String key, Locale locale);
}
