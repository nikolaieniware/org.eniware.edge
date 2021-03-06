/* ==================================================================
 *  Eniware Open Source:Nikolai Manchev
 *  Apache License 2.0
 * ==================================================================
 */

package org.eniware.edge.dao.jdbc;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.ICsvReader;

/**
 * Read a row of CSV data and set the values onto a {@link PreparedStatement}.
 * 
 * @version 1.0
 * @since 1.17
 */
public interface JdbcPreparedStatementCsvReader extends ICsvReader {

	/**
	 * Reads a row of CSV data into columns on a {@link PreparedStatement}.
	 * 
	 * @param stmt
	 *        The statement to use.
	 * @param csvColumns
	 *        The CSV column names with associated indicies. These must match
	 *        the JDBC column names.
	 * @param cellProcessors
	 *        An array of cell processors to handle each exported column. The
	 *        length of the array should match the number and order of columns
	 *        in the {@code csvColumns}. {@code null} values are permitted and
	 *        indicate no processing should be performed on that column.
	 * @param columnMetaData
	 *        The column names with associated metadata. The names should match
	 *        the column names in the {@code PreparedStatement}.
	 * @return {@code true} if a row of CSV data was read and values set on the
	 *         provided {@code PreparedStatement}.
	 * @throws SQLException
	 *         If any SQL error occurs.
	 * @throws IOException
	 *         If any IO error occurs.
	 */
	boolean read(PreparedStatement stmt, Map<String, Integer> csvColumns, CellProcessor[] cellProcessors,
			Map<String, ColumnCsvMetaData> columnMetaData) throws SQLException, IOException;

}
