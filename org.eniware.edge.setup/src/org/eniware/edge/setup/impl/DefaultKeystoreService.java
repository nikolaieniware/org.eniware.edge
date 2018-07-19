/* ==================================================================
 *  Eniware Open sorce:Nikolai Manchev
 *  Apache License 2.0
 * ==================================================================
 */
package org.eniware.edge.setup.impl;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.x500.X500Principal;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.springframework.context.MessageSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.FileCopyUtils;
import org.eniware.edge.SSLService;
import org.eniware.edge.backup.BackupResource;
import org.eniware.edge.backup.BackupResourceInfo;
import org.eniware.edge.backup.BackupResourceProvider;
import org.eniware.edge.backup.BackupResourceProviderInfo;
import org.eniware.edge.backup.ResourceBackupResource;
import org.eniware.edge.backup.SimpleBackupResourceInfo;
import org.eniware.edge.backup.SimpleBackupResourceProviderInfo;
import org.eniware.edge.setup.PKIService;
import org.eniware.support.CertificateException;
import org.eniware.support.CertificateService;
import org.eniware.support.ConfigurableSSLService;

/**
 * Service for managing a {@link KeyStore}.
 * 
 * <p>
 * This implementation maintains a key store with two primary aliases:
 * {@code ca} and {@code Edge}. The key store is created as needed, and a random
 * password is generated and assigned to the key store. The password is stored
 * in the Settings database, using the {@link #KEY_PASSWORD} key. This key store
 * is then used to implement {@link SSLService} and is used as both the key and
 * trust store for SSL connections returned by that API.
 * </p>
 * 
 * @version 1.4
 */
public class DefaultKeystoreService extends ConfigurableSSLService
		implements PKIService, SSLService, BackupResourceProvider {

	private static final String BACKUP_RESOURCE_NAME_KEYSTORE = "Edge.jks";

	/** The default value for the {@code keyStorePath} property. */
	public static final String DEFAULT_KEY_STORE_PATH = "conf/tls/Edge.jks";

	private static final String PKCS12_KEYSTORE_TYPE = "pkcs12";
	private static final int PASSWORD_LENGTH = 20;

	private String EdgeAlias = "Edge";
	private String caAlias = "ca";
	private int keySize = 2048;
	private MessageSource messageSource;

	private final SetupIdentityDao setupIdentityDao;
	private final CertificateService certificateService;

	/**
	 * Default constructor.
	 */
	public DefaultKeystoreService(SetupIdentityDao setupIdentityDao,
			CertificateService certificateService) {
		super();
		this.setupIdentityDao = setupIdentityDao;
		this.certificateService = certificateService;
		setKeyStorePath(DefaultKeystoreService.DEFAULT_KEY_STORE_PATH);
		setTrustStorePassword("eniwareedge");
		setKeyStorePassword(null);
	}

	@Override
	public String getKey() {
		return DefaultKeystoreService.class.getName();
	}

	@Override
	public Iterable<BackupResource> getBackupResources() {
		File ksFile = new File(getKeyStorePath());
		if ( !(ksFile.isFile() && ksFile.canRead()) ) {
			return Collections.emptyList();
		}
		List<BackupResource> result = new ArrayList<BackupResource>(1);
		result.add(new ResourceBackupResource(new FileSystemResource(ksFile),
				BACKUP_RESOURCE_NAME_KEYSTORE, getKey()));
		return result;
	}

	@Override
	public boolean restoreBackupResource(BackupResource resource) {
		if ( resource != null
				&& BACKUP_RESOURCE_NAME_KEYSTORE.equalsIgnoreCase(resource.getBackupPath()) ) {
			final File ksFile = new File(getKeyStorePath());
			final File ksDir = ksFile.getParentFile();
			if ( !ksDir.isDirectory() ) {
				if ( !ksDir.mkdirs() ) {
					log.warn("Error creating keystore directory {}", ksDir.getAbsolutePath());
					return false;
				}
			}
			synchronized ( this ) {
				try {
					FileCopyUtils.copy(resource.getInputStream(), new FileOutputStream(ksFile));
					ksFile.setLastModified(resource.getModificationDate());
					return true;
				} catch ( IOException e ) {
					log.error("IO error restoring keystore resource {}: {}", ksFile.getAbsolutePath(),
							e.getMessage());
					return false;
				}
			}
		}
		return false;
	}

	@Override
	public BackupResourceProviderInfo providerInfo(Locale locale) {
		String name = "Certificate Backup Provider";
		String desc = "Backs up the EniwareEdge certificates.";
		MessageSource ms = messageSource;
		if ( ms != null ) {
			name = ms.getMessage("title", null, name, locale);
			desc = ms.getMessage("desc", null, desc, locale);
		}
		return new SimpleBackupResourceProviderInfo(getKey(), name, desc);
	}

	@Override
	public BackupResourceInfo resourceInfo(BackupResource resource, Locale locale) {
		return new SimpleBackupResourceInfo(resource.getProviderKey(), resource.getBackupPath(), null);
	}

	/**
	 * Get the keystore password.
	 * 
	 * <p>
	 * If a password has been configured via
	 * {@link #setKeyStorePassword(String)} this method will return that.
	 * Otherwise, the {@link SetupIdentityDao} is used get the existing
	 * password. If available, that password is returned. Otherwise, a new
	 * random password will be generated and persisted into
	 * {@code SetupIdentityDao}, and the generated password will be returned.
	 * </p>
	 */
	@Override
	protected String getKeyStorePassword() {
		String manualKeyStorePassword = super.getKeyStorePassword();
		if ( manualKeyStorePassword != null && manualKeyStorePassword.length() > 0 ) {
			return manualKeyStorePassword;
		}
		SetupIdentityInfo info = setupIdentityDao.getSetupIdentityInfo();
		String result = info.getKeyStorePassword();
		if ( result == null ) {
			// generate new random password and save
			result = generateNewKeyStorePassword();
			setupIdentityDao.saveSetupIdentityInfo(info.withKeyStorePassword(result));
		}
		return result;
	}

	private String generateNewKeyStorePassword() {
		String manualKeyStorePassword = super.getKeyStorePassword();
		if ( manualKeyStorePassword != null && manualKeyStorePassword.length() > 0 ) {
			return manualKeyStorePassword;
		}
		try {
			SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
			final int start = 32;
			final int end = 126;
			final int range = end - start;
			char[] passwd = new char[PASSWORD_LENGTH];
			for ( int i = 0; i < PASSWORD_LENGTH; i++ ) {
				passwd[i] = (char) (random.nextInt(range) + start);
			}
			return new String(passwd);
		} catch ( NoSuchAlgorithmException e ) {
			throw new CertificateException("Error creating random password", e);
		}
	}

	@Override
	public boolean isEdgeCertificateValid(String issuerDN) throws CertificateException {
		KeyStore keyStore = loadKeyStore();
		X509Certificate x509 = null;
		try {
			if ( keyStore == null || !keyStore.containsAlias(EdgeAlias) ) {
				return false;
			}
			Certificate cert = keyStore.getCertificate(EdgeAlias);
			if ( !(cert instanceof X509Certificate) ) {
				return false;
			}
			x509 = (X509Certificate) cert;
			x509.checkValidity();
			X500Principal issuer = new X500Principal(issuerDN);
			if ( !x509.getIssuerX500Principal().equals(issuer) ) {
				log.debug("Certificate issuer {} not same as expected {}",
						x509.getIssuerX500Principal().getName(), issuer.getName());
				return false;
			}
			return true;
		} catch ( KeyStoreException e ) {
			throw new CertificateException("Error checking for Edge certificate", e);
		} catch ( CertificateExpiredException e ) {
			log.debug("Certificate {} has expired", x509.getSubjectDN().getName());
		} catch ( CertificateNotYetValidException e ) {
			log.debug("Certificate {} not valid yet", x509.getSubjectDN().getName());
		}
		return false;
	}

	@Override
	public X509Certificate generateEdgeSelfSignedCertificate(String dn) throws CertificateException {
		KeyStore keyStore = null;
		try {
			keyStore = loadKeyStore();
		} catch ( CertificateException e ) {
			Throwable root = e;
			while ( root.getCause() != null ) {
				root = root.getCause();
			}
			if ( root instanceof UnrecoverableKeyException ) {
				// bad password... we shall assume here that a new Edge association is underway,
				// so delete the existing key store and re-create
				File ksFile = new File(getKeyStorePath());
				if ( ksFile.isFile() ) {
					log.info(
							"Deleting existing certificate store due to invalid password, will create new store");
					if ( ksFile.delete() ) {
						// clear out old key store password, so we generate a new one
						setupIdentityDao.saveSetupIdentityInfo(
								setupIdentityDao.getSetupIdentityInfo().withKeyStorePassword(null));
						keyStore = loadKeyStore();
					}
				}
			}
			if ( keyStore == null ) {
				// re-throw, we didn't handle it
				throw e;
			}
		}
		return createSelfSignedCertificate(keyStore, dn, EdgeAlias);
	}

	private X509Certificate createSelfSignedCertificate(KeyStore keyStore, String dn, String alias) {
		try {
			// create new key pair for the Edge
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			keyGen.initialize(keySize, new SecureRandom());
			KeyPair keypair = keyGen.generateKeyPair();
			PublicKey publicKey = keypair.getPublic();
			PrivateKey privateKey = keypair.getPrivate();

			Certificate cert = certificateService.generateCertificate(dn, publicKey, privateKey);
			keyStore.setKeyEntry(alias, privateKey, getKeyStorePassword().toCharArray(),
					new Certificate[] { cert });
			saveKeyStore(keyStore);
			return (X509Certificate) cert;
		} catch ( NoSuchAlgorithmException e ) {
			throw new CertificateException("Error setting up Edge key pair", e);
		} catch ( KeyStoreException e ) {
			throw new CertificateException("Error setting up Edge key pair", e);
		}
	}

	private void saveTrustedCertificate(X509Certificate cert, String alias) {
		KeyStore keyStore = loadKeyStore();
		try {
			log.info("Installing trusted CA certificate {}", cert.getSubjectDN());
			keyStore.setCertificateEntry(alias, cert);
			saveKeyStore(keyStore);
		} catch ( KeyStoreException e ) {
			throw new CertificateException("Error saving trusted certificate", e);
		}
	}

	@Override
	public void saveCACertificate(X509Certificate cert) throws CertificateException {
		saveTrustedCertificate(cert, caAlias);
	}

	@Override
	public String generateEdgePKCS10CertificateRequestString() throws CertificateException {
		KeyStore keyStore = loadKeyStore();
		Key key;
		try {
			key = keyStore.getKey(EdgeAlias, getKeyStorePassword().toCharArray());
		} catch ( UnrecoverableKeyException e ) {
			throw new CertificateException("Error opening Edge private key", e);
		} catch ( KeyStoreException e ) {
			throw new CertificateException("Error opening Edge private key", e);
		} catch ( NoSuchAlgorithmException e ) {
			throw new CertificateException("Error opening Edge private key", e);
		}
		assert key instanceof PrivateKey;
		Certificate cert;
		try {
			cert = keyStore.getCertificate(EdgeAlias);
		} catch ( KeyStoreException e ) {
			throw new CertificateException("Error opening Edge certificate", e);
		}
		assert cert instanceof X509Certificate;
		return certificateService.generatePKCS10CertificateRequestString((X509Certificate) cert,
				(PrivateKey) key);
	}

	@Override
	public String generateEdgePKCS7CertificateString() throws CertificateException {
		KeyStore keyStore = loadKeyStore();
		Key key;
		try {
			key = keyStore.getKey(EdgeAlias, getKeyStorePassword().toCharArray());
		} catch ( UnrecoverableKeyException e ) {
			throw new CertificateException("Error opening Edge private key", e);
		} catch ( KeyStoreException e ) {
			throw new CertificateException("Error opening Edge private key", e);
		} catch ( NoSuchAlgorithmException e ) {
			throw new CertificateException("Error opening Edge private key", e);
		}
		assert key instanceof PrivateKey;
		Certificate cert;
		try {
			cert = keyStore.getCertificate(EdgeAlias);
		} catch ( KeyStoreException e ) {
			throw new CertificateException("Error opening Edge certificate", e);
		}
		assert cert instanceof X509Certificate;
		return certificateService
				.generatePKCS7CertificateChainString(new X509Certificate[] { (X509Certificate) cert });
	}

	@Override
	public String generateEdgePKCS7CertificateChainString() throws CertificateException {
		KeyStore keyStore = loadKeyStore();
		Key key;
		try {
			key = keyStore.getKey(EdgeAlias, getKeyStorePassword().toCharArray());
		} catch ( UnrecoverableKeyException e ) {
			throw new CertificateException("Error opening Edge private key", e);
		} catch ( KeyStoreException e ) {
			throw new CertificateException("Error opening Edge private key", e);
		} catch ( NoSuchAlgorithmException e ) {
			throw new CertificateException("Error opening Edge private key", e);
		}
		assert key instanceof PrivateKey;
		Certificate[] chain;
		try {
			chain = keyStore.getCertificateChain(EdgeAlias);
		} catch ( KeyStoreException e ) {
			throw new CertificateException("Error opening Edge certificate", e);
		}
		X509Certificate[] x509Chain = new X509Certificate[chain.length];
		for ( int i = 0; i < chain.length; i++ ) {
			assert chain[i] instanceof X509Certificate;
			x509Chain[i] = (X509Certificate) chain[i];
		}
		return certificateService.generatePKCS7CertificateChainString(x509Chain);
	}

	@Override
	public X509Certificate getEdgeCertificate() throws CertificateException {
		return getEdgeCertificate(loadKeyStore());
	}

	private X509Certificate getEdgeCertificate(KeyStore keyStore) {
		X509Certificate EdgeCert;
		try {
			EdgeCert = (X509Certificate) keyStore.getCertificate(EdgeAlias);
		} catch ( KeyStoreException e ) {
			throw new CertificateException("Error opening Edge certificate", e);
		}
		return EdgeCert;
	}

	@Override
	public X509Certificate getCACertificate() throws CertificateException {
		return getCACertificate(loadKeyStore());
	}

	private X509Certificate getCACertificate(KeyStore keyStore) {
		X509Certificate EdgeCert;
		try {
			EdgeCert = (X509Certificate) keyStore.getCertificate(caAlias);
		} catch ( KeyStoreException e ) {
			throw new CertificateException("Error opening Edge certificate", e);
		}
		return EdgeCert;
	}

	@Override
	public void savePKCS12Keystore(String keystore, String password) throws CertificateException {
		KeyStore keyStore = loadKeyStore(PKCS12_KEYSTORE_TYPE,
				new ByteArrayInputStream(Base64.decodeBase64(keystore)), password);
		final String newPassword = generateNewKeyStorePassword();
		KeyStore newKeyStore = loadKeyStore(KeyStore.getDefaultType(), null, newPassword);

		// change the password to our local random one
		copyEdgeChain(keyStore, password, newKeyStore, newPassword);

		File ksFile = new File(getKeyStorePath());
		if ( ksFile.isFile() ) {
			ksFile.delete();
		}
		saveKeyStore(newKeyStore, newPassword);
		setupIdentityDao.saveSetupIdentityInfo(
				setupIdentityDao.getSetupIdentityInfo().withKeyStorePassword(newPassword));
	}

	private void copyEdgeChain(KeyStore keyStore, String password, KeyStore newKeyStore,
			String newPassword) {
		try {
			// change the password to our local random one
			Key key = keyStore.getKey(EdgeAlias, password.toCharArray());
			Certificate[] chain = keyStore.getCertificateChain(EdgeAlias);
			X509Certificate[] x509Chain = new X509Certificate[chain.length];
			for ( int i = 0; i < chain.length; i += 1 ) {
				x509Chain[i] = (X509Certificate) chain[i];
			}
			saveEdgeCertificateChain(newKeyStore, key, newPassword, x509Chain[0], x509Chain);
		} catch ( GeneralSecurityException e ) {
			throw new CertificateException(e);
		}
	}

	@Override
	public String generatePKCS12KeystoreString(String password) throws CertificateException {
		KeyStore keyStore = loadKeyStore();
		KeyStore newKeyStore = loadKeyStore(PKCS12_KEYSTORE_TYPE, null, password);
		copyEdgeChain(keyStore, getKeyStorePassword(), newKeyStore, password);

		ByteArrayOutputStream byos = new ByteArrayOutputStream();
		saveKeyStore(newKeyStore, password, new Base64OutputStream(byos));
		try {
			return byos.toString("US-ASCII");
		} catch ( UnsupportedEncodingException e ) {
			// should never get here
			throw new RuntimeException(e);
		}
	}

	@Override
	public void saveEdgeSignedCertificate(String pem) throws CertificateException {
		KeyStore keyStore = loadKeyStore();
		Key key;
		try {
			key = keyStore.getKey(EdgeAlias, getKeyStorePassword().toCharArray());
		} catch ( UnrecoverableKeyException e ) {
			throw new CertificateException("Error opening Edge private key", e);
		} catch ( KeyStoreException e ) {
			throw new CertificateException("Error opening Edge private key", e);
		} catch ( NoSuchAlgorithmException e ) {
			throw new CertificateException("Error opening Edge private key", e);
		}
		X509Certificate EdgeCert = getEdgeCertificate(keyStore);
		if ( EdgeCert == null ) {
			throw new CertificateException(
					"The Edge does not have a private key, start the association process over.");
		}

		X509Certificate[] chain = certificateService.parsePKCS7CertificateChainString(pem);

		saveEdgeCertificateChain(keyStore, key, getKeyStorePassword(), EdgeCert, chain);

		saveKeyStore(keyStore);
	}

	private void saveEdgeCertificateChain(KeyStore keyStore, Key key, String keyPassword,
			X509Certificate EdgeCert, X509Certificate[] chain) {
		if ( keyPassword == null ) {
			keyPassword = "";
		}
		X509Certificate caCert = getCACertificate(keyStore);

		if ( chain.length < 1 ) {
			throw new CertificateException("No certificates avaialble");
		}

		if ( chain.length > 1 ) {
			// we have to trust the parents... the end of the chain must be our CA
			try {
				final int caIdx = chain.length - 1;
				if ( caCert == null ) {
					// if we don't have a CA cert yet, install that now
					log.info("Installing trusted CA certificate {}", chain[caIdx].getSubjectDN());
					keyStore.setCertificateEntry(caAlias, chain[caIdx]);
					caCert = chain[caIdx];
				} else {
					// verify CA is the same... maybe we shouldn't do this?
					if ( !chain[caIdx].getSubjectDN().equals(caCert.getSubjectDN()) ) {
						throw new CertificateException(
								"Chain CA " + chain[caIdx].getSubjectDN().getName()
										+ " does not match expected " + caCert.getSubjectDN().getName());
					}
					if ( !chain[caIdx].getIssuerDN().equals(caCert.getIssuerDN()) ) {
						throw new CertificateException("Chain CA " + chain[caIdx].getIssuerDN().getName()
								+ " does not match expected " + caCert.getIssuerDN().getName());
					}
				}
				// install intermediate certs...
				for ( int i = caIdx - 1, j = 1; i > 0; i--, j++ ) {
					String alias = caAlias + "sub" + j;
					log.info("Installing trusted intermediate certificate {}", chain[i].getSubjectDN());
					keyStore.setCertificateEntry(alias, chain[i]);
				}
			} catch ( KeyStoreException e ) {
				throw new CertificateException("Error storing CA chain", e);
			}
		} else {
			// put CA at end of chain
			if ( caCert == null ) {
				throw new CertificateException("No CA certificate available");
			}
			chain = new X509Certificate[] { chain[0], caCert };
		}

		// the issuer must be our CA cert subject...
		if ( !chain[0].getIssuerDN().equals(chain[1].getSubjectDN()) ) {
			throw new CertificateException("Issuer " + chain[0].getIssuerDN().getName()
					+ " does not match expected " + chain[1].getSubjectDN().getName());
		}

		// the subject must be our Edge's existing subject...
		if ( !chain[0].getSubjectDN().equals(EdgeCert.getSubjectDN()) ) {
			throw new CertificateException("Subject " + chain[0].getIssuerDN().getName()
					+ " does not match expected " + EdgeCert.getSubjectDN().getName());
		}

		log.info("Installing Edge certificate {} reply {} issued by {}", chain[0].getSerialNumber(),
				chain[0].getSubjectDN().getName(), chain[0].getIssuerDN().getName());
		try {
			keyStore.setKeyEntry(EdgeAlias, key, keyPassword.toCharArray(), chain);
		} catch ( KeyStoreException e ) {
			throw new CertificateException("Error opening Edge certificate", e);
		}
	}

	@Override
	public synchronized SSLSocketFactory getEniwareInSocketFactory() {
		return getSSLSocketFactory();
	}

	private synchronized void saveKeyStore(KeyStore keyStore) {
		String passwd = getKeyStorePassword();
		saveKeyStore(keyStore, passwd);
	}

	private synchronized void saveKeyStore(final KeyStore keyStore, final String passwd) {
		if ( keyStore == null ) {
			return;
		}
		File ksFile = new File(getKeyStorePath());
		File ksDir = ksFile.getParentFile();
		if ( !ksDir.isDirectory() && !ksDir.mkdirs() ) {
			throw new RuntimeException("Unable to create KeyStore directory: " + ksFile.getParent());
		}

		try {
			saveKeyStore(keyStore, passwd, new BufferedOutputStream(new FileOutputStream(ksFile)));
			resetSocketFactory();
		} catch ( IOException e ) {
			throw new CertificateException("Error saving certificate key store to " + ksFile.getPath(),
					e);
		}
	}

	public void setEdgeAlias(String EdgeAlias) {
		this.EdgeAlias = EdgeAlias;
	}

	public void setCaAlias(String caAlias) {
		this.caAlias = caAlias;
	}

	public void setKeySize(int keySize) {
		this.keySize = keySize;
	}

	/**
	 * Set the manual keystore password to use.
	 * 
	 * @param manualKeyStorePassword
	 *        the password to use
	 * @deprecated use {@link #setKeyStorePassword(String)}
	 */
	@Deprecated
	public void setManualKeyStorePassword(String manualKeyStorePassword) {
		setKeyStorePassword(manualKeyStorePassword);
	}

	public void setMessageSource(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

}
