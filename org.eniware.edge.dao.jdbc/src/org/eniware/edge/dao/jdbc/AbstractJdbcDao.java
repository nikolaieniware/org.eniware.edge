/* ===================================================================
 * AbstractJdbcDao.java
 * 
 * Created Jul 15, 2008 8:36:00 AM
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
 * ===================================================================
 */

package org.eniware.edge.dao.jdbc;

import static org.eniware.edge.dao.jdbc.JdbcDaoConstants.SCHEMA_NAME;
import static org.eniware.edge.dao.jdbc.JdbcDaoConstants.TABLE_SETTINGS;

import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import net.solarnetwork.util.OptionalService;

/**
 * Base class for JDBC based DAO implementations.
 * 
 * <p>
 * This class extends {@link JdbcDaoSupport} with methods for handling upgrade
 * maintenance of the table(s) managed by this DAO over time, e.g. creating
 * tables if they don't exist, running DDL update scripts to upgrade to a new
 * version, etc.
 * </p>
 * 
 * @author matt
 * @version 1.5
 * @param <T>
 *        the domain object type managed by this DAO
 */
public abstract class AbstractJdbcDao<T> extends JdbcDaoSupport implements JdbcDao {

	/** A class-level Logger. */
	protected final Logger log = LoggerFactory.getLogger(getClass());

	private String sqlGetTablesVersion = null;
	private String sqlResourcePrefix = null;
	private int tablesVersion = 1;
	private boolean useAutogeneratedKeys = false;
	private Resource initSqlResource = null;
	private String schemaName = SCHEMA_NAME;
	private String tableName = TABLE_SETTINGS;
	private MessageSource messageSource = null;
	private String sqlForUpdateSuffix = " FOR UPDATE";
	private OptionalService<EventAdmin> eventAdmin;

	private final Map<String, String> sqlResourceCache = new HashMap<String, String>(10);

	/**
	 * Initialize this class after properties are set.
	 */
	public void init() {
		// verify database table exists, and if not create it
		verifyDatabaseExists(this.schemaName, this.tableName, this.initSqlResource);

		// now veryify database tables version is up-to-date
		try {
			upgradeTablesVersion();
		} catch ( IOException e ) {
			throw new RuntimeException("Unable to upgrade tables to version " + getTablesVersion(), e);
		}

		if ( messageSource == null ) {
			ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
			ms.setBasename(getClass().getName());
			ms.setBundleClassLoader(getClass().getClassLoader());
			setMessageSource(ms);
		}
	}

	/**
	 * Insert a new domain object.
	 * 
	 * @param obj
	 *        the domain object to insert
	 * @param sqlInsert
	 *        the SQL to persist the object with
	 */
	protected void insertDomainObject(final T obj, final String sqlInsert) {
		getJdbcTemplate().update(new PreparedStatementCreator() {

			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				PreparedStatement ps = con.prepareStatement(sqlInsert);
				setStoreStatementValues(obj, ps);
				return ps;
			}
		});
	}

	/**
	 * Update a domain object.
	 * 
	 * @param obj
	 *        the domain object to update
	 * @param sqlUpdate
	 *        the SQL to persist the object with
	 * @since 1.2
	 */
	protected int updateDomainObject(final T obj, final String sqlUpdate) {
		return getJdbcTemplate().update(new PreparedStatementCreator() {

			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				PreparedStatement ps = con.prepareStatement(sqlUpdate);
				setUpdateStatementValues(obj, ps);
				return ps;
			}
		});
	}

	/**
	 * Set {@link PreparedStatement} values for updating a domain object.
	 * 
	 * <p>
	 * Called from {@link #updateDomainObject(T, String)} to persist changed
	 * values of a domain object.
	 * </p>
	 * 
	 * <p>
	 * This implementation does not do anything. Extending classes should
	 * override this and set values on the {@code PreparedStatement} object as
	 * needed to persist the domain object.
	 * </p>
	 * 
	 * @param obj
	 *        the domain object to persist
	 * @param ps
	 *        the PreparedStatement to persist with
	 * @throws SQLException
	 *         if any SQL error occurs
	 * @since 1.2
	 */
	protected void setUpdateStatementValues(T obj, PreparedStatement ps) throws SQLException {
		// this is a no-op method, override to do something useful
	}

	/**
	 * Store (insert) a new domain object.
	 * 
	 * <p>
	 * If {@link #isUseAutogeneratedKeys()} is <em>true</em> then this method
	 * will use JDBC's {@link Statement#RETURN_GENERATED_KEYS} to obtain the
	 * auto-generated primary key for the newly inserted object. Otherwise, this
	 * method will call the
	 * {@link #storeDomainObjectWithoutAutogeneratedKeys(T, String)} method.
	 * </p>
	 * 
	 * @param obj
	 *        the domain object to persist
	 * @param sqlInsert
	 *        the SQL to persist the object with
	 * @return the primary key created for the domain object
	 */
	protected Long storeDomainObject(final T obj, final String sqlInsert) {
		if ( !useAutogeneratedKeys ) {
			return storeDomainObjectWithoutAutogeneratedKeys(obj, sqlInsert);
		}
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		getJdbcTemplate().update(new PreparedStatementCreator() {

			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				PreparedStatement ps = con.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
				setStoreStatementValues(obj, ps);
				return ps;
			}
		}, keyHolder);
		if ( keyHolder.getKey() != null ) {
			return Long.valueOf(keyHolder.getKey().longValue());
		}
		return null;
	}

	/**
	 * Set {@link PreparedStatement} values for storing a domain object.
	 * 
	 * <p>
	 * Called from {@link #storeDomainObject(T, String)} and
	 * {@link #storeDomainObjectWithoutAutogeneratedKeys(T, String)} to persist
	 * values of a domain object.
	 * </p>
	 * 
	 * <p>
	 * This implementation does not do anything. Extending classes should
	 * override this and set values on the {@code PreparedStatement} object as
	 * needed to persist the domain object.
	 * </p>
	 * 
	 * @param obj
	 *        the domain object to persist
	 * @param ps
	 *        the PreparedStatement to persist with
	 * @throws SQLException
	 *         if any SQL error occurs
	 */
	protected void setStoreStatementValues(T obj, PreparedStatement ps) throws SQLException {
		// this is a no-op method, override to do something useful
	}

	/**
	 * Persist a domain object, without using auto-generated keys.
	 * 
	 * @param obj
	 *        the domain object to persist
	 * @param sqlInsert
	 *        the SQL insert statement to use
	 * @return the primary key created for the domain object
	 */
	protected Long storeDomainObjectWithoutAutogeneratedKeys(final T obj, final String sqlInsert) {
		Object result = getJdbcTemplate().execute(new PreparedStatementCreator() {

			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				PreparedStatement ps = con.prepareStatement(sqlInsert);
				setStoreStatementValues(obj, ps);
				return ps;
			}
		}, new PreparedStatementCallback<Object>() {

			@Override
			public Object doInPreparedStatement(PreparedStatement ps)
					throws SQLException, DataAccessException {
				ps.execute();
				int count = ps.getUpdateCount();
				if ( count == 1 && ps.getMoreResults() ) {
					ResultSet rs = ps.getResultSet();
					if ( rs.next() ) {
						return rs.getObject(1);
					}
				}
				return null;
			}
		});
		if ( result instanceof Long ) {
			return (Long) result;
		} else if ( result instanceof Number ) {
			return Long.valueOf(((Number) result).longValue());
		}
		if ( log.isWarnEnabled() ) {
			log.warn("Unexpected (non-number) primary key returned: " + result);
		}
		return null;
	}

	/**
	 * Verify a database table exists, and if not initialize the database with
	 * the SQL in the provided {@code initSqlResource}.
	 * 
	 * @param schema
	 *        the schema to check
	 * @param table
	 *        the table to check
	 * @param initSql
	 *        the init SQL resource
	 */
	protected void verifyDatabaseExists(final String schema, final String table,
			final Resource initSql) {
		getJdbcTemplate().execute(new ConnectionCallback<Object>() {

			@Override
			public Object doInConnection(Connection con) throws SQLException, DataAccessException {
				if ( !tableExists(con, schema, table) ) {
					String[] initSqlStatements = getBatchSqlResource(initSql);
					if ( initSqlStatements != null ) {
						if ( log.isInfoEnabled() ) {
							log.info("Initializing database from [" + initSql + ']');
						}
						getJdbcTemplate().batchUpdate(initSqlStatements);
					}
				}
				return null;
			}
		});
	}

	/**
	 * Test if a schema exists in the database.
	 * 
	 * @param conn
	 *        the connection
	 * @param aSchemaName
	 *        the schema name to look for
	 * @return {@literal true} if the schema is found
	 * @throws SQLException
	 *         if any SQL error occurs
	 * @since 1.3
	 */
	protected boolean schemaExists(Connection conn, final String aSchemaName) throws SQLException {
		DatabaseMetaData dbMeta = conn.getMetaData();
		ResultSet rs = null;
		try {
			rs = dbMeta.getSchemas();
			while ( rs.next() ) {
				String schema = rs.getString(1);
				if ( (aSchemaName == null || (aSchemaName.equalsIgnoreCase(schema))) ) {
					if ( log.isDebugEnabled() ) {
						log.debug("Found schema " + schema);
					}
					return true;
				}
			}
			return false;
		} finally {
			if ( rs != null ) {
				try {
					rs.close();
				} catch ( SQLException e ) {
					// ignore this
				}
			}
		}
	}

	/**
	 * Test if a table exists in the database.
	 * 
	 * @param conn
	 *        the connection
	 * @param aSchemaName
	 *        the schema name to look for (or <em>null</em> for any schema)
	 * @param aTableName
	 *        the table name to look for
	 * @return boolean if table is found
	 * @throws SQLException
	 *         if any SQL error occurs
	 */
	protected boolean tableExists(Connection conn, String aSchemaName, String aTableName)
			throws SQLException {
		DatabaseMetaData dbMeta = conn.getMetaData();
		ResultSet rs = null;
		try {
			rs = dbMeta.getTables(null, null, null, null);
			while ( rs.next() ) {
				String schema = rs.getString(2);
				String table = rs.getString(3);
				if ( (aSchemaName == null || (aSchemaName.equalsIgnoreCase(schema)))
						&& aTableName.equalsIgnoreCase(table) ) {
					if ( log.isDebugEnabled() ) {
						log.debug("Found table " + schema + '.' + table);
					}
					return true;
				}
			}
			return false;
		} finally {
			if ( rs != null ) {
				try {
					rs.close();
				} catch ( SQLException e ) {
					// ignore this
				}
			}
		}
	}

	/**
	 * Upgrade the database tables to the configured version, if the database
	 * version is less than the configured version.
	 * 
	 * <p>
	 * This method uses the {@link #getSqlGetTablesVersion()} SQL statement to
	 * query for the current database table version. If the version found there
	 * is less than the {@link #getTablesVersion()} value then a sequence of SQL
	 * resources, relative to the {@link #getInitSqlResource()} resource, are
	 * loaded and executed. The resources are assumed to have a file name patter
	 * like <code><em>[tablesUpdatePrefix]</em>-update-<em>[#]</em>.sql</code>
	 * where <em>[tablesUpdatePrefix]</em> is the
	 * {@link #getTablesUpdatePrefix()} value and <em>[#]</em> is the version
	 * number, starting at the currently found table version + 1 up through and
	 * including {@link #getTablesVersion()}. If no version value is found when
	 * querying, the current version is assumed to be {@code 0}.
	 * </p>
	 * 
	 * <p>
	 * For example, if by querying the current version is reported as {@code 1}
	 * and the {@link #getTablesVersion()} value is {@code 3}, and the
	 * {@link #getTablesUpdatePrefix()} is {@code derby-mytable}, the following
	 * SQL resources will be exectued:
	 * </p>
	 * 
	 * <ol>
	 * <li>derby-mytable-update-2.sql</li>
	 * <li>derby-mytable-update-3.sql</li>
	 * </ol>
	 * 
	 * <p>
	 * Each update SQL is expected, therefor, to also update the "current" table
	 * version so that after running the update subsequent calls to this method
	 * where {@link #getTablesVersion()} has not changed will not do anything.
	 * </p>
	 * 
	 * @throws IOException
	 *         if update resources cannot be found
	 */
	protected void upgradeTablesVersion() throws IOException {
		String currVersion = "0";
		try {
			currVersion = getJdbcTemplate().queryForObject(sqlGetTablesVersion, String.class);
		} catch ( EmptyResultDataAccessException e ) {
			if ( log.isInfoEnabled() ) {
				log.info("Table version setting not found, assuming version 0.");
			}
		}
		int curr = Integer.parseInt(currVersion);
		while ( curr < this.tablesVersion ) {
			if ( log.isInfoEnabled() ) {
				log.info("Updating database tables version from " + curr + " to " + (curr + 1));
			}
			Resource sql = this.initSqlResource
					.createRelative(this.sqlResourcePrefix + "-update-" + (curr + 1) + ".sql");
			String[] batch = getBatchSqlResource(sql);
			int[] result = getJdbcTemplate().batchUpdate(batch);
			if ( log.isDebugEnabled() ) {
				log.debug("Database tables updated to version " + (curr + 1) + " update results: "
						+ Arrays.toString(result));
			}
			curr++;
		}
	}

	/**
	 * Load a classpath SQL resource into a String.
	 * 
	 * <p>
	 * The classpath resource is taken as the {@link #getSqlResourcePrefix()}
	 * value and {@code -} and the {@code classPathResource} combined with a
	 * {@code .sql} suffix. If that resoruce is not found, then the prefix is
	 * split into components separated by a {@code -} character, and the last
	 * component is dropped and then combined with {@code -} and
	 * {@code classPathResource} again to try to find a match, until there is no
	 * prefix left and just the {@code classPathResource} itself is tried.
	 * </p>
	 * 
	 * <p>
	 * This method will cache the SQL resource in-memory for quick future
	 * access.
	 * </p>
	 * 
	 * @param string
	 *        the classpath resource to load as a SQL string
	 * @return the String
	 */
	protected String getSqlResource(String classPathResource) {
		Class<?> myClass = getClass();
		String resourceName = getSqlResourcePrefix() + "-" + classPathResource + ".sql";
		String key = myClass.getName() + ";" + classPathResource;
		if ( sqlResourceCache.containsKey(key) ) {
			return sqlResourceCache.get(key);
		}
		String[] prefixes = getSqlResourcePrefix().split("-");
		int prefixEndIndex = prefixes.length - 1;
		try {
			Resource r = new ClassPathResource(resourceName, myClass);
			while ( !r.exists() && prefixEndIndex >= 0 ) {
				// try by chopping down prefix, which we split on a dash character
				String subName;
				if ( prefixEndIndex > 0 ) {
					String[] subPrefixes = new String[prefixEndIndex];
					System.arraycopy(prefixes, prefixEndIndex, subPrefixes, 0, prefixEndIndex);
					subName = StringUtils.arrayToDelimitedString(subPrefixes, "-") + "-"
							+ classPathResource;
				} else {
					subName = classPathResource;
				}
				subName += ".sql";
				r = new ClassPathResource(subName, myClass);
				prefixEndIndex--;
			}
			if ( !r.exists() ) {
				throw new RuntimeException("SQL resource " + resourceName + " not found");
			}
			String result = FileCopyUtils.copyToString(new InputStreamReader(r.getInputStream()));
			if ( result != null && result.length() > 0 ) {
				sqlResourceCache.put(key, result);
			}
			return result;
		} catch ( IOException e ) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Load a SQL resource into a String.
	 * 
	 * @param resource
	 *        the SQL resource to load
	 * @return the String
	 */
	protected String getSqlResource(Resource resource) {
		try {
			return FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream()));
		} catch ( IOException e ) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Get batch SQL statements, split into multiple statements on the
	 * {@literal ;} character.
	 * 
	 * @param sqlResource
	 *        the SQL resource to load
	 * @return split SQL
	 */
	protected String[] getBatchSqlResource(Resource sqlResource) {
		String sql = getSqlResource(sqlResource);
		if ( sql == null ) {
			return null;
		}
		return sql.split(";\\s*");
	}

	/**
	 * Post an {@link Event}.
	 * 
	 * <p>
	 * This method only works if a {@link EventAdmin} has been configured via
	 * {@link #setEventAdmin(OptionalService)}. Otherwise the event is silently
	 * ignored.
	 * </p>
	 * 
	 * @param event
	 *        the event to post
	 * @since 1.5
	 */
	protected final void postEvent(Event event) {
		EventAdmin ea = (eventAdmin == null ? null : eventAdmin.service());
		if ( ea == null || event == null ) {
			return;
		}
		ea.postEvent(event);
	}

	/**
	 * This implementation simply returns a new array with a single value:
	 * {@link #getTableName()}.
	 * 
	 * @see org.eniware.edge.dao.jdbc.JdbcDao#getTableNames()
	 */
	@Override
	public String[] getTableNames() {
		return new String[] { getTableName() };
	}

	public String getSqlGetTablesVersion() {
		return sqlGetTablesVersion;
	}

	public void setSqlGetTablesVersion(String sqlGetTablesVersion) {
		this.sqlGetTablesVersion = sqlGetTablesVersion;
	}

	/**
	 * @return the tablesUpdatePrefix
	 * @deprecated use {@link #getSqlResourcePrefix()}
	 */
	@Deprecated
	public String getTablesUpdatePrefix() {
		return getSqlResourcePrefix();
	}

	/**
	 * @param tablesUpdatePrefix
	 *        the tablesUpdatePrefix to set
	 * @deprecated use {@link #setSqlResourcePrefix(String)}
	 */
	@Deprecated
	public void setTablesUpdatePrefix(String tablesUpdatePrefix) {
		setSqlResourcePrefix(tablesUpdatePrefix);
	}

	public String getSqlResourcePrefix() {
		return sqlResourcePrefix;
	}

	public void setSqlResourcePrefix(String sqlResourcePrefix) {
		this.sqlResourcePrefix = sqlResourcePrefix;
	}

	public int getTablesVersion() {
		return tablesVersion;
	}

	/**
	 * @param tablesVersion
	 *        the tablesVersion to set
	 */
	public void setTablesVersion(int tablesVersion) {
		this.tablesVersion = tablesVersion;
	}

	public boolean isUseAutogeneratedKeys() {
		return useAutogeneratedKeys;
	}

	public void setUseAutogeneratedKeys(boolean useAutogeneratedKeys) {
		this.useAutogeneratedKeys = useAutogeneratedKeys;
	}

	public Resource getInitSqlResource() {
		return initSqlResource;
	}

	public void setInitSqlResource(Resource initSqlResource) {
		this.initSqlResource = initSqlResource;
	}

	@Override
	public String getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	@Override
	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	@Override
	public MessageSource getMessageSource() {
		return messageSource;
	}

	public void setMessageSource(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	/**
	 * Set a SQL fragment to append to SQL statements where an updatable result
	 * set is desired.
	 * 
	 * @return the SQL suffix, or {@literal null} if not desired
	 * @since 1.4
	 */
	public String getSqlForUpdateSuffix() {
		return sqlForUpdateSuffix;
	}

	/**
	 * Set a SQL fragment to append to SQL statements where an updatable result
	 * set is desired.
	 * 
	 * <p>
	 * This defaults to {@literal FOR UPDATE}. <b>Note</b> a space must be
	 * included at the beginning. Set to {@literal null} to disable.
	 * </p>
	 * 
	 * @param sqlForUpdateSuffix
	 *        the suffix to set
	 * @since 1.4
	 */
	public void setSqlForUpdateSuffix(String sqlForUpdateSuffix) {
		this.sqlForUpdateSuffix = sqlForUpdateSuffix;
	}

	/**
	 * Get the {@link EventAdmin} service.
	 * 
	 * @return the EventAdmin service
	 * @since 1.5
	 */
	public OptionalService<EventAdmin> getEventAdmin() {
		return eventAdmin;
	}

	/**
	 * Set an {@link EventAdmin} service to use.
	 * 
	 * @param eventAdmin
	 *        the EventAdmin to use
	 * @since 1.5
	 */
	public void setEventAdmin(OptionalService<EventAdmin> eventAdmin) {
		this.eventAdmin = eventAdmin;
	}

}
