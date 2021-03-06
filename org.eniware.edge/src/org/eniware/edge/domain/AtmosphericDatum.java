/* ==================================================================
 *  Eniware Open Source:Nikolai Manchev
 *  Apache License 2.0
 * ==================================================================
 */

package org.eniware.edge.domain;

import java.math.BigDecimal;

/**
 * Standardized API for atmospheric related datum to implement.
 * 
 * @version 1.2
 */
public interface AtmosphericDatum extends Datum {

	/**
	 * A {@link org.eniware.domain.GeneralEdgeDatumSamples} instantaneous
	 * sample key for {@link AtmosphericDatum#getTemperature()} values.
	 */
	static final String TEMPERATURE_KEY = "temp";

	/**
	 * A {@link org.eniware.domain.GeneralDatumSamples} instantaneous
	 * sample key for {@link AtmosphericDatum#getHumidity()} values.
	 */
	static final String HUMIDITY_KEY = "humidity";

	/**
	 * A {@link org.eniware.domain.GeneralDatumSamples} instantaneous
	 * sample key for {@link AtmosphericDatum#getDewPoint()} values.
	 */
	static final String DEW_POINT_KEY = "dew";

	/**
	 * A {@link org.eniware.domain.GeneralDatumSamples} instantaneous
	 * sample key for {@link AtmosphericDatum#getAtmosphericPressure()} values.
	 */
	static final String ATMOSPHERIC_PRESSURE_KEY = "atm";

	/**
	 * A {@link org.eniware.domain.GeneralDatumSamples} instantaneous
	 * sample key for {@link AtmosphericDatum#getAtmosphericPressure()} values.
	 */
	static final String VISIBILITY_KEY = "visibility";

	/**
	 * A {@link org.eniware.domain.GeneralDatumSamples} status sample key
	 * for {@link AtmosphericDatum#getSkyConditions()} values.
	 */
	static final String SKY_CONDITIONS_KEY = "sky";

	/**
	 * A {@link org.eniware.domain.GeneralDatumSamples} status sample key
	 * for {@link AtmosphericDatum#getWindSpeed()} values.
	 * 
	 * @since 1.2
	 */
	static final String WIND_SPEED_KEY = "wspeed";

	/**
	 * A {@link org.eniware.domain.GeneralDatumSamples} status sample key
	 * for {@link AtmosphericDatum#getWindDirection()} values.
	 * 
	 * @since 1.2
	 */
	static final String WIND_DIRECTION_KEY = "wdir";

	/**
	 * A {@link org.eniware.domain.GeneralDatumSamples} status sample key
	 * for {@link AtmosphericDatum#getRain()} values.
	 * 
	 * @since 1.2
	 */
	static final String RAIN_KEY = "rain";

	/**
	 * A {@link org.eniware.domain.GeneralDatumSamples} status sample key
	 * for {@link AtmosphericDatum#getSnow()} values.
	 * 
	 * @since 1.2
	 */
	static final String SNOW_KEY = "snow";

	/** A tag for an "indoor" atmosphere sample. */
	static final String TAG_ATMOSPHERE_INDOOR = "indoor";

	/** A tag for an "outdoor" atmosphere sample. */
	static final String TAG_ATMOSPHERE_OUTDOOR = "outdoor";

	/**
	 * A tag for a forecast atmosphere sample, as opposed to an actual
	 * measurement.
	 * 
	 * @since 1.2
	 */
	static final String TAG_FORECAST = "forecast";

	/**
	 * Get the instantaneous temperature, in degrees Celsius.
	 * 
	 * @return the temperature, in degrees Celsius
	 */
	BigDecimal getTemperature();

	/**
	 * Get the instantaneous dew point, in degrees Celsius.
	 * 
	 * @return the dew point, in degrees celsius
	 */
	BigDecimal getDewPoint();

	/**
	 * Get the instantaneous humidity, as an integer percentage (where 100
	 * represents 100%).
	 * 
	 * @return the humidity, as an integer percentage
	 */
	Integer getHumidity();

	/**
	 * Get the instantaneous atmospheric pressure, in pascals.
	 * 
	 * @return the atmospheric pressure, in pascals
	 */
	Integer getAtmosphericPressure();

	/**
	 * Get the instantaneous visibility, in meters.
	 * 
	 * @return visibility, in meters
	 */
	Integer getVisibility();

	/**
	 * Get a textual description of the sky conditions, e.g. "clear", "cloudy",
	 * etc.
	 * 
	 * @return general sky conditions
	 */
	String getSkyConditions();

	/**
	 * Get the wind speed, in meters / second.
	 * 
	 * @return wind speed
	 * @since 1.2
	 */
	BigDecimal getWindSpeed();

	/**
	 * Get the wind direction, in degrees.
	 * 
	 * @return wind direction
	 * @since 1.2
	 */
	Integer getWindDirection();

	/**
	 * Get the rain accumulation, in millimeters.
	 * 
	 * @return rain accumulation
	 * @since 1.2
	 */
	Integer getRain();

	/**
	 * Get the snow accumulation, in millimeters.
	 * 
	 * @return snow accumulation
	 * @since 1.2
	 */
	Integer getSnow();

}
