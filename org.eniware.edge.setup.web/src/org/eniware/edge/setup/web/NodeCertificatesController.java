/* ==================================================================
 *  Eniware Open sorce:Nikolai Manchev
 *  Apache License 2.0
 * ==================================================================
 */

package org.eniware.edge.setup.web;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.eniware.edge.setup.web.support.ServiceAwareController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import net.solarnetwork.node.setup.PKIService;

/**
 * Controller for node certificate management.
 * 
 * @version 1.1
 */
@ServiceAwareController
@RequestMapping("/a/certs")
public class NodeCertificatesController extends BaseSetupController {

	@Autowired
	private PKIService pkiService;

	/**
	 * View the main certs page.
	 * 
	 * @param model
	 *        the view model
	 * @return
	 */
	@RequestMapping
	public String home(Model model) {
		X509Certificate nodeCert = pkiService.getNodeCertificate();
		final Date now = new Date();
		final boolean expired = (nodeCert != null && now.after(nodeCert.getNotAfter()));
		final boolean valid = (nodeCert != null
				&& (!nodeCert.getIssuerDN().equals(nodeCert.getSubjectDN())
						&& !now.before(nodeCert.getNotBefore()) && !expired));
		model.addAttribute("nodeCert", nodeCert);
		if ( nodeCert != null ) {
			model.addAttribute("nodeCertSerialNumber", "0x" + nodeCert.getSerialNumber().toString(16));
		}
		model.addAttribute("nodeCertExpired", expired);
		model.addAttribute("nodeCertValid", valid);
		return "certs/home";
	}

	/**
	 * Return a node's CSR based on its current certificate.
	 * 
	 * @return a map with the PEM encoded certificate on key {@code csr}
	 */
	@RequestMapping(value = "/nodeCSR", method = RequestMethod.GET)
	@ResponseBody
	public Map<String, Object> nodeCSR() {
		String csr = pkiService.generateNodePKCS10CertificateRequestString();
		Map<String, Object> result = new HashMap<String, Object>(1);
		result.put("csr", csr);
		return result;
	}

	/**
	 * Return a node's current certificate.
	 * 
	 * @return a map with the PEM encoded certificate on key {@code cert} if
	 *         {@code download} is not <em>true</em>, otherwise the content is
	 *         returned as a file attachment
	 */
	@RequestMapping(value = "/nodeCert", method = RequestMethod.GET)
	@ResponseBody
	public Object viewNodeCert(
			@RequestParam(value = "download", required = false) final Boolean download,
			@RequestParam(value = "chain", required = false) final Boolean asChain) {
		final String cert = (Boolean.TRUE.equals(asChain)
				? pkiService.generateNodePKCS7CertificateChainString()
				: pkiService.generateNodePKCS7CertificateString());

		if ( !Boolean.TRUE.equals(download) ) {
			Map<String, Object> result = new HashMap<String, Object>(1);
			result.put("cert", cert);
			return result;
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentLength(cert.length());
		headers.setContentType(MediaType.parseMediaType("application/x-pem-file"));
		headers.setLastModified(System.currentTimeMillis());
		headers.setCacheControl("no-cache");

		headers.set("Content-Disposition",
				"attachment; filename=solarnode-" + getIdentityService().getNodeId() + ".pem");

		return new ResponseEntity<String>(cert, headers, HttpStatus.OK);
	}

	/**
	 * Import a certificate reply (signed certificate chain).
	 * 
	 * @param file
	 *        the CSR file to import
	 * @return the destination view
	 * @throws IOException
	 *         if an IO error occurs
	 */
	@RequestMapping(value = "/import", method = RequestMethod.POST)
	public String importSettings(@RequestParam(value = "file", required = false) MultipartFile file,
			@RequestParam(value = "text", required = false) String text) throws IOException {
		String pem = text;
		if ( file != null && !file.isEmpty() ) {
			pem = FileCopyUtils.copyToString(new InputStreamReader(file.getInputStream(), "UTF-8"));
		}
		pkiService.saveNodeSignedCertificate(pem);
		return "redirect:/a/certs";
	}

	@RequestMapping(value = "/renew", method = RequestMethod.POST)
	@ResponseBody
	public Object renewCertificate(@RequestParam("password") String password) {
		Map<String, Object> result = new HashMap<String, Object>(1);
		boolean success = false;
		try {
			getSetupBiz().renewNetworkCertificate(password);
			success = true;
		} catch ( RuntimeException e ) {
			result.put("message", e.getMessage());
		}
		result.put("success", success);
		return result;
	}
}