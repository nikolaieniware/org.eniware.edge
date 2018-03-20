/* ==================================================================
 * Backup.java - Mar 27, 2013 7:10:38 AM
 * 
 * Copyright 2007-2013 SolarNetwork.net Dev Team
 * 
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation; either version 2 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 * 02111-1307 USA
 * ==================================================================
 */

package org.eniware.edge.backup;

/**
 * An individual backup.
 * 
 * @author matt
 * @version 1.2
 */
public interface Backup extends BackupIdentity {

	/**
	 * Boolean flag indicating if this backup is complete.
	 * 
	 * @return <em>true</em> if the backup is finished, <em>false</em> otherwise
	 */
	boolean isComplete();

	/**
	 * Get the size, in bytes, of this backup.
	 * 
	 * @return the size in bytes, or <em>null</em> if not known
	 */
	Long getSize();
}
