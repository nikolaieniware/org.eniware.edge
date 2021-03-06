/* ==================================================================
 *  Eniware Open Source:Nikolai Manchev
 *  Apache License 2.0
 * ==================================================================
 */

package org.eniware.edge.setup.web;

import static org.eniware.web.domain.Response.response;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Resource;

import org.eniware.edge.setup.web.support.ServiceAwareController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.eniware.edge.setup.Plugin;
import org.eniware.edge.setup.PluginProvisionStatus;
import org.eniware.edge.setup.PluginService;
import org.eniware.edge.setup.SimplePluginQuery;
import org.eniware.util.OptionalService;
import org.eniware.web.domain.Response;

/**
 * Controller to manage the installed bundles via an OBR.
 * 
 * @version 1.1
 */
@ServiceAwareController
@RequestMapping("/a/plugins")
public class PluginController {

	public static class PluginDetails {

		private final List<Plugin> availablePlugins;
		private final List<Plugin> installedPlugins;

		public PluginDetails() {
			super();
			this.availablePlugins = Collections.emptyList();
			this.installedPlugins = Collections.emptyList();
		}

		public PluginDetails(List<Plugin> availablePlugins, List<Plugin> installedPlugins) {
			super();
			this.availablePlugins = availablePlugins;
			this.installedPlugins = installedPlugins;
		}

		public List<Plugin> getAvailablePlugins() {
			return availablePlugins;
		}

		public List<Plugin> getInstalledPlugins() {
			return installedPlugins;
		}

	}

	@Resource(name = "pluginService")
	private OptionalService<PluginService> pluginService;

	@Autowired(required = true)
	private MessageSource messageSource;

	private long statusPollTimeoutMs = 1000L * 15L;

	private final Logger log = LoggerFactory.getLogger(getClass());

	public static final String ERROR_UNKNOWN_PROVISION_ID = "unknown.provisionID";

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseBody
	public Response<Object> unknownProvisionID(IllegalArgumentException e, Locale locale) {
		return new Response<Object>(Boolean.FALSE, ERROR_UNKNOWN_PROVISION_ID,
				messageSource.getMessage("plugins.error.unknown-provisionID", null, locale), null);
	}

	@RequestMapping(value = "", method = RequestMethod.GET)
	public String home() {
		return "plugins/list";
	}

	@RequestMapping(value = "/provisionStatus", method = RequestMethod.GET)
	@ResponseBody
	public Response<PluginProvisionStatus> status(@RequestParam(value = "id") final String provisionID,
			@RequestParam(value = "p", required = false) final Integer knownProgress,
			final Locale locale) {
		PluginService service = pluginService.service();
		if ( service == null ) {
			throw new UnsupportedOperationException("PluginService not available");
		}
		log.debug("Looking up provision status {}", provisionID);
		// we assume a long-poll request here, so wait until the status changes
		PluginProvisionStatus status;
		int progress = (knownProgress != null ? knownProgress.intValue() : 0);
		final long maxTime = System.currentTimeMillis() + statusPollTimeoutMs;
		while ( true ) {
			status = service.statusForProvisioningOperation(provisionID, locale);
			if ( status == null ) {
				// the provision ID is not available
				throw new IllegalArgumentException(provisionID);
			}
			int newProgress = Math.round(status.getOverallProgress() * 100f);
			if ( newProgress > progress || System.currentTimeMillis() > maxTime ) {
				return response(status);
			}
			try {
				Thread.sleep(1000);
			} catch ( InterruptedException e ) {
				// ignore
			}
		}
	}

	private PluginDetails pluginDetails(final String filter, final Boolean latestOnly,
			final Locale locale) {
		PluginService service = pluginService.service();
		if ( service == null ) {
			return new PluginDetails();
		}
		SimplePluginQuery query = new SimplePluginQuery();
		query.setSimpleQuery(filter);
		query.setLatestVersionOnly(latestOnly == null ? true : latestOnly.booleanValue());
		List<Plugin> available = service.availablePlugins(query, locale);
		List<Plugin> installed = service.installedPlugins(locale);
		return new PluginDetails(available, installed);
	}

	@RequestMapping(value = "/list", method = RequestMethod.GET)
	@ResponseBody
	public Response<PluginDetails> list(
			@RequestParam(value = "filter", required = false) final String filter,
			@RequestParam(value = "latestOnly", required = false) final Boolean latestOnly,
			final Locale locale) {
		return response(pluginDetails(filter, latestOnly, locale));
	}

	@RequestMapping(value = "/refresh", method = RequestMethod.GET)
	@ResponseBody
	public Response<Boolean> refresh() {
		PluginService service = pluginService.service();
		if ( service == null ) {
			return response(Boolean.FALSE);
		}
		service.refreshAvailablePlugins();
		return response(Boolean.TRUE);
	}

	@RequestMapping(value = "/install", method = RequestMethod.GET)
	@ResponseBody
	public Response<PluginProvisionStatus> previewInstall(
			@RequestParam(value = "uid") final String[] uid, final Locale locale) {
		PluginService service = pluginService.service();
		if ( service == null ) {
			throw new UnsupportedOperationException("PluginService not available");
		}
		List<String> uids = Arrays.asList(uid);
		return response(service.previewInstallPlugins(uids, locale));
	}

	/**
	 * Install the latest available version of one or more plugins.
	 * 
	 * @param uid
	 *        the UIDs of the plugins to install or upgrade to the latest
	 *        version; if not provided then upgrade all installed plugins to
	 *        their latest versions
	 * @param locale
	 *        the active locale
	 * @return the provision status
	 */
	@RequestMapping(value = "/install", method = RequestMethod.POST)
	@ResponseBody
	public Response<PluginProvisionStatus> install(
			@RequestParam(value = "uid", required = false) final String[] uid, final Locale locale) {
		PluginService service = pluginService.service();
		if ( service == null ) {
			throw new UnsupportedOperationException("PluginService not available");
		}
		Collection<String> uids;
		if ( uid != null && uid.length > 0 && !"".equals(uid[0]) ) {
			uids = Arrays.asList(uid);
		} else {
			uids = upgradablePluginUids(locale);
		}
		return response(service.installPlugins(uids, locale));
	}

	private Set<String> upgradablePluginUids(Locale locale) {
		PluginService service = pluginService.service();
		if ( service == null ) {
			throw new UnsupportedOperationException("PluginService not available");
		}
		PluginDetails plugins = pluginDetails(null, true, locale);

		// extract all upgradable plugins
		Set<String> upgradableUids = new LinkedHashSet<String>();
		if ( plugins != null && plugins.getInstalledPlugins() != null ) {
			Map<String, Plugin> availablePlugins = new HashMap<String, Plugin>(
					plugins.getAvailablePlugins().size());
			for ( Plugin plugin : plugins.getAvailablePlugins() ) {
				availablePlugins.put(plugin.getUID(), plugin);
			}
			for ( Plugin plugin : plugins.getInstalledPlugins() ) {
				Plugin candidate = availablePlugins.get(plugin.getUID());
				if ( candidate != null && candidate.getVersion().compareTo(plugin.getVersion()) > 0 ) {
					upgradableUids.add(plugin.getUID());
				}
			}
		}
		return upgradableUids;
	}

	/**
	 * Preview an "upgrade all" operation, where all installed plugins are
	 * updated to their latest available version.
	 * 
	 * @param locale
	 *        the active locale
	 * @return the provision status
	 * @since 1.1
	 */
	@RequestMapping(value = "/upgradeAll", method = RequestMethod.GET)
	@ResponseBody
	public Response<PluginProvisionStatus> previewUpgradeAll(final Locale locale) {
		// extract all upgradable plugins
		Set<String> upgradableUids = upgradablePluginUids(locale);

		PluginService service = pluginService.service();
		if ( service == null ) {
			throw new UnsupportedOperationException("PluginService not available");
		}

		return response(service.previewInstallPlugins(upgradableUids, locale));
	}

	@RequestMapping(value = "/remove", method = RequestMethod.POST)
	@ResponseBody
	public Response<PluginProvisionStatus> remove(@RequestParam(value = "uid") final String uid,
			final Locale locale) {
		PluginService service = pluginService.service();
		if ( service == null ) {
			throw new UnsupportedOperationException("PluginService not available");
		}
		Collection<String> uids = Collections.singleton(uid);
		return response(service.removePlugins(uids, locale));
	}

	public void setPluginService(OptionalService<PluginService> pluginService) {
		this.pluginService = pluginService;
	}

	public void setStatusPollTimeoutMs(long statusPollTimeoutMs) {
		this.statusPollTimeoutMs = statusPollTimeoutMs;
	}

	public void setMessageSource(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

}
