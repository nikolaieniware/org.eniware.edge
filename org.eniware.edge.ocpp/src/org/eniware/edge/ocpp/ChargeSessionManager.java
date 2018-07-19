/* ==================================================================
 *  Eniware Open sorce:Nikolai Manchev
 *  Apache License 2.0
 * ==================================================================
 */

package org.eniware.edge.ocpp;

import java.util.Collection;
import java.util.List;
import org.eniware.edge.Identifiable;

/**
 * API for managing charge sessions. A <em>charge session</em> is the process of
 * charging an electric device, e.g. plugging the device into a socket,
 * authorizing the use of the socket with the OCPP central system, monitoring
 * the energy used while charging, and unplugging the device from the socket,
 * and finally confirming that charging is complete.
 * 
 * @version 1.2
 */
public interface ChargeSessionManager extends Identifiable {

	/**
	 * The EventAdmin topic used to post events when a socket has been
	 * activated, that is when a plug is plugged into a device to start
	 * charging.
	 */
	String EVENT_TOPIC_SOCKET_ACTIVATED = "net/eniwarenetwork/Edge/ocpp/SOCKET_ACTIVATED";

	/**
	 * The EventAdmin topic used to post events when a socket has been
	 * deactivated, that is when a plug is unplugged from a device when charging
	 * ends.
	 */
	String EVENT_TOPIC_SOCKET_DEACTIVATED = "net/eniwarenetwork/Edge/ocpp/SOCKET_DEACTIVATED";

	/**
	 * The EventAdmin topic used to post events when a charge session has been
	 * initiated.
	 * 
	 * @since 1.1
	 */
	String EVENT_TOPIC_SESSION_STARTED = "net/eniwarenetwork/Edge/ocpp/SESSION_STARTED";

	/**
	 * The EventAdmin topic used to post events when a charge session has ended.
	 * 
	 * @since 1.1
	 */
	String EVENT_TOPIC_SESSION_ENDED = "net/eniwarenetwork/Edge/ocpp/SESSION_ENDED";

	/**
	 * The EventAdmin topic used to post events for captured meter reading data
	 * related to a charge session.
	 * 
	 * @since 1.1
	 */
	String EVENT_TOPIC_SESSION_METER_READING = "net/eniwarenetwork/Edge/ocpp/METER_READING";

	/**
	 * The Event property used to convey a String socket ID.
	 */
	String EVENT_PROPERTY_SOCKET_ID = "socketId";

	/**
	 * The Event property used to convey a String charge session ID.
	 * 
	 * @since 1.1
	 */
	String EVENT_PROPERTY_SESSION_ID = "sessionId";

	/**
	 * The Event property used to convey a date expressed as an epoch Long.
	 * 
	 * @since 1.1
	 */
	String EVENT_PROPERTY_DATE = "date";

	/**
	 * The Event property used to convey a Number instantaneous power reading,
	 * in watts.
	 * 
	 * @since 1.1
	 */
	String EVENT_PROPERTY_METER_READING_POWER = "power";

	/**
	 * The Event property used to convey a Number instantaneous energy reading,
	 * in watt hours.
	 * 
	 * @since 1.1
	 */
	String EVENT_PROPERTY_METER_READING_ENERGY = "energy";

	/**
	 * Initiate a new charge session.
	 * 
	 * @param idTag
	 *        The ID to request authorization with.
	 * @param socketId
	 *        The ID of the physical socket to enable
	 * @param reservationId
	 *        An optional OCPP reservation ID. The reservation will be
	 *        considered satisfied upon successful return from this method.
	 * @return A unique charge session ID.
	 * @throws OCPPException
	 *         If an active session already exists for the given
	 *         {@code socketId}.
	 */
	String initiateChargeSession(String idTag, String socketId, Integer reservationId);

	/**
	 * Get an <em>active</em> charge session, if available. A charge session is
	 * considered <em>active</em> if one exists from a previous call to
	 * {@link #initiateChargeSession(String, String, Integer)} without any
	 * corresponding call to {@link #completeChargeSession(String, String)}
	 * passing the same {@code idTag} and {@code socketId} values.
	 * 
	 * @param socketId
	 *        The ID of the physical socket to get the session for.
	 * @return The active charge session, or <em>null</em> if there is no active
	 *         charge session for the given socket ID.
	 */
	ChargeSession activeChargeSession(String socketId);

	/**
	 * Get all available meter readings for a charge session.
	 * 
	 * @param sessionId
	 *        The ID of the charge session to get meter readings for.
	 * @return The meter reading, or {@code null} if none available.
	 * @since 1.1
	 */
	List<ChargeSessionMeterReading> meterReadingsForChargeSession(String sessionId);

	/**
	 * Get an <em>active</em> charge session for an OCPP transaction ID, if
	 * available. A charge session is considered <em>active</em> if one exists
	 * from a previous call to
	 * {@link #initiateChargeSession(String, String, Integer)} without any
	 * corresponding call to {@link #completeChargeSession(String, String)}.
	 * This method will only work if the charge session has successfully
	 * obtained a transaction ID from the central system.
	 * 
	 * @param transactionId
	 *        The ID of the transaction to get the session for.
	 * @return The active charge session, or <em>null</em> if there is no active
	 *         charge session for the given transaction ID.
	 */
	ChargeSession activeChargeSession(Number transactionId);

	/**
	 * Complete a charge session. This method must be called at some point after
	 * {@link #initiateChargeSession(String, String, Integer)}, passing in the
	 * return value of that method as the {@code sessionId}.
	 * 
	 * @param idTag
	 *        The ID to request authorization with.
	 * @param sessionId
	 *        The session ID returned from a previous call to
	 *        {@link #initiateChargeSession(String, String, Integer)}.
	 */
	void completeChargeSession(String idTag, String sessionId);

	/**
	 * Get a socket ID for a specific OCPP connector ID.
	 * 
	 * @param connectorId
	 *        The connector ID to get the equivalent socket ID for.
	 * @return The socket ID, or <em>null</em> if not known.
	 */
	String socketIdForConnectorId(Number connectorId);

	/**
	 * Get a collection of all available socket IDs.
	 * 
	 * @return A collection of socket IDs, never <em>null</em>.
	 */
	Collection<String> availableSocketIds();

	/**
	 * Configure the enabled state of one or more sockets.
	 * 
	 * @param socketIds
	 *        A collection of socket IDs to update the state of.
	 * @param enabled
	 *        The enabled state to set.
	 */
	void configureSocketEnabledState(Collection<String> socketIds, boolean enabled);

	/**
	 * Attempt to post any completed charge sessions that have not be posted to
	 * the OCPP central system.
	 * 
	 * @param max
	 *        The maximum number of offline sessions to post.
	 * @return The number of sessions posted.
	 */
	int postCompleteOfflineSessions(int max);

	/**
	 * Attempt to post collected meter values for all active charge sessions.
	 * 
	 * @since 1.2
	 */
	void postActiveChargeSessionsMeterValues();

}
