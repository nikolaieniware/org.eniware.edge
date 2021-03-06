/* ==================================================================
 *  Eniware Open Source:Nikolai Manchev
 *  Apache License 2.0
 * ==================================================================
 */

package org.eniware.edge.dao.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.eniware.dao.jdbc.SQLExceptionHandler;

/**
 * Recover from connection exceptions by shutting down.
 * 
 * @version 1.0
 */
public class ShutdownSQLExceptionHandler implements SQLExceptionHandler {

	private List<Pattern> sqlStatePatterns;

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Override
	public void handleGetConnectionException(SQLException e) {
		handleConnectionException(null, e);
	}

	@Override
	public void handleConnectionException(Connection conn, SQLException e) {
		SQLException root = e;
		while ( root.getNextException() != null ) {
			root = root.getNextException();
		}
		String state = root.getSQLState();
		if ( state == null ) {
			return;
		}
		List<Pattern> statePatterns = sqlStatePatterns;
		if ( statePatterns == null || statePatterns.isEmpty() ) {
			return;
		}
		for ( Pattern pat : statePatterns ) {
			if ( pat.matcher(state).matches() ) {
				shutdown(root.getMessage());
				return;
			}
		}
	}

	private void shutdown(String msg) {
		log.error("Shutting down now due to database connection error: {}", msg);
		// graceful would be bundleContext.getBundle(0).stop();, but we don't need to wait for that here
		System.exit(1);
	}

	/**
	 * Set a list of regular expressions that should trigger a restore from
	 * backup.
	 * 
	 * @param regexes
	 *        The regular expressions that should trigger a restore from backup.
	 */
	public void setSqlStatePatterns(List<Pattern> sqlStatePatterns) {
		this.sqlStatePatterns = sqlStatePatterns;
	}

	/**
	 * Set a comma-delimited list of regular expressions that should trigger a
	 * restore from backup.
	 * 
	 * @param regexes
	 *        A comma-delimited list of regular expressions that should trigger
	 *        a restore from backup.
	 * @see #setSqlStatePatterns(List)
	 */
	public void setSqlStateRegex(String regexes) {
		List<Pattern> pats = null;
		String[] list = StringUtils.delimitedListToStringArray(regexes, ",");
		if ( regexes != null && list.length > 0 ) {
			pats = new ArrayList<Pattern>();
			for ( String regex : list ) {
				pats.add(Pattern.compile(regex));
			}
		}
		setSqlStatePatterns(pats);
	}

}
