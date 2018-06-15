/* ==================================================================
 *  Eniware Open sorce:Nikolai Manchev
 *  Apache License 2.0
 * ==================================================================
 */
package org.eniware.edge.job;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.eniware.edge.UploadService;
import org.eniware.edge.dao.DatumDao;
import org.eniware.edge.domain.Datum;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.PersistJobDataAfterExecution;

/**
 * Job to query a {@link DatumDao} for data to upload via an
 * {@link UploadService}.
 * 
 * <p>
 * This job will call {@link DatumDao#getDatumNotUploaded(String)} and for each
 * {@link Datum} returned pass that to {@link UploadService#uploadDatum(Datum)}.
 * If that returns a non-null tracking ID, then that will be passed to
 * {@link DatumDao#storeDatumUpload(Datum, String, Long)}.
 * </p>
 * 
 * <p>
 * The configurable properties of this class are:
 * </p>
 * 
 * <dl class="class-properties">
 * <dt>datumDao</dt>
 * <dd>The {@link DatumDao} to use to query for {@link Datum} to upload.</dd>
 * 
 * <dt>uploadService</dt>
 * <dd>The {@link UploadService} implementation to use to upload the datum
 * to.</dd>
 * </dl>
 * 
 * @param <T>
 *        the Datum type for this job
 * @version 2.0
 */
@PersistJobDataAfterExecution
@DisallowConcurrentExecution
public class DatumDaoUploadJob<T extends Datum> extends AbstractJob {

	private Collection<DatumDao<Datum>> daos;
	private UploadService uploadService = null;

	@Override
	protected void executeInternal(JobExecutionContext jobContext) throws Exception {
		for ( DatumDao<Datum> datumDao : daos ) {
			if ( log.isInfoEnabled() ) {
				log.info("Collecting [" + datumDao.getDatumType().getSimpleName()
						+ "] data to bulk upload to [" + uploadService.getKey() + ']');
			}

			if ( log.isInfoEnabled() ) {
				log.info("Uploading [" + datumDao.getDatumType().getSimpleName() + "] data to ["
						+ uploadService.getKey() + ']');
			}

			List<Datum> toUpload = datumDao.getDatumNotUploaded(uploadService.getKey());
			int count = 0;

			if ( log.isDebugEnabled() ) {
				log.debug("Uploading " + toUpload.size() + " [" + datumDao.getDatumType().getSimpleName()
						+ "] data to [" + uploadService.getKey() + ']');
			}
			final Date uploadDate = new Date();
			try {
				for ( Datum datum : toUpload ) {
					String tid = uploadService.uploadDatum(datum);
					if ( log.isTraceEnabled() ) {
						log.trace("Just uploaded [" + datumDao.getDatumType().getSimpleName() + "] ["
								+ datum.getCreated().getTime() + " " + datum.getSourceId()
								+ "] and received tid [" + tid + "]");
					}

					if ( tid != null ) {
						datumDao.setDatumUploaded(datum, uploadDate, uploadService.getKey(), tid);
						count++;
					}
				}
			} catch ( RuntimeException e ) {
				Throwable root = e;
				while ( root.getCause() != null ) {
					root = root.getCause();
				}
				if ( root instanceof IOException ) {
					if ( log.isWarnEnabled() ) {
						log.warn("Network problem posting data: " + root.getMessage());
					}
				} else {
					if ( log.isErrorEnabled() ) {
						log.error("Exception posting data", root);
					}
				}
			}
			if ( log.isInfoEnabled() ) {
				log.info("Uploaded " + count + " [" + datumDao.getDatumType().getSimpleName()
						+ "] objects to [" + uploadService.getKey() + ']');
			}
		}

	}

	/**
	 * @param daos
	 *        the daos to set
	 */
	public void setDaos(Collection<DatumDao<Datum>> daos) {
		this.daos = daos;
	}

	/**
	 * @param uploadService
	 *        the uploadService to set
	 */
	public void setUploadService(UploadService uploadService) {
		this.uploadService = uploadService;
	}

}
