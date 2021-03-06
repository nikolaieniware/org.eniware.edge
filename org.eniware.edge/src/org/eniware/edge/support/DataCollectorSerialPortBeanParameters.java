/* ==================================================================
 *  Eniware Open Source:Nikolai Manchev
 *  Apache License 2.0
 * ==================================================================
 */

package org.eniware.edge.support;

import java.util.List;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.eniware.edge.DataCollector;
import org.eniware.edge.settings.SettingSpecifier;
import org.eniware.edge.settings.support.BasicTextFieldSettingSpecifier;
import org.eniware.edge.settings.support.BasicToggleSettingSpecifier;

/**
 * Configuration bean for {@link DataCollector}.
 * 
 * @version 1.0
 */
public class DataCollectorSerialPortBeanParameters extends SerialPortBeanParameters {

	private static final DataCollectorSerialPortBeanParameters DEFAULTS = new DataCollectorSerialPortBeanParameters();

	private int bufferSize = 4096;
	private byte[] magic = new byte[] { 0x13 };
	private byte[] magicEOF = null;
	private int readSize = 8;
	private boolean toggleDtr = true;
	private boolean toggleRts = true;

	/**
	 * Get a list of setting specifiers for this bean.
	 * 
	 * @param prefix
	 *        the bean prefix to use
	 * @return setting specifiers
	 */
	public static List<SettingSpecifier> getDefaultSettingSpecifiers(String prefix) {
		return getDefaultSettingSpecifiers(DEFAULTS, prefix);
	}

	/**
	 * Get a list of setting specifiers for this bean, with fixed size message
	 * support.
	 * 
	 * @param defaults
	 *        the default values to use
	 * @param prefix
	 *        the bean prefix to use
	 * @return setting specifiers
	 */
	public static List<SettingSpecifier> getDefaultSettingSpecifiers(
			DataCollectorSerialPortBeanParameters defaults, String prefix) {
		List<SettingSpecifier> results = SerialPortBeanParameters.getDefaultSettingSpecifiers(defaults,
				prefix);
		results.add(new BasicTextFieldSettingSpecifier(prefix + "bufferSize", String.valueOf(defaults
				.getBufferSize())));
		results.add(new BasicTextFieldSettingSpecifier(prefix + "magicHex", defaults.getMagicHex()));
		results.add(new BasicTextFieldSettingSpecifier(prefix + "readSize", String.valueOf(defaults
				.getReadSize())));
		results.add(new BasicToggleSettingSpecifier(prefix + "toggleDtr", defaults.isToggleDtr()));
		results.add(new BasicToggleSettingSpecifier(prefix + "toggleRts", defaults.isToggleRts()));
		return results;
	}

	/**
	 * Get a list of setting specifiers for this bean, with variable message
	 * size support.
	 * 
	 * @param defaults
	 *        the default values to use
	 * @param prefix
	 *        the bean prefix to use
	 * @return setting specifiers
	 */
	public static List<SettingSpecifier> getDefaultVariableSettingSpecifiers(
			DataCollectorSerialPortBeanParameters defaults, String prefix) {
		List<SettingSpecifier> results = SerialPortBeanParameters.getDefaultSettingSpecifiers(defaults,
				prefix);
		results.add(new BasicTextFieldSettingSpecifier(prefix + "bufferSize", String.valueOf(defaults
				.getBufferSize())));
		results.add(new BasicTextFieldSettingSpecifier(prefix + "magicHex", defaults.getMagicHex()));
		results.add(new BasicTextFieldSettingSpecifier(prefix + "magicEOFHex", defaults.getMagicEOFHex()));
		results.add(new BasicToggleSettingSpecifier(prefix + "toggleDtr", defaults.isToggleDtr()));
		results.add(new BasicToggleSettingSpecifier(prefix + "toggleRts", defaults.isToggleRts()));
		return results;
	}

	public int getBufferSize() {
		return bufferSize;
	}

	public void setBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
	}

	public String getMagicHex() {
		if ( getMagic() == null ) {
			return null;
		}
		return Hex.encodeHexString(getMagic());
	}

	public void setMagicHex(String hex) {
		if ( hex == null || (hex.length() % 2) == 1 ) {
			setMagic(null);
		}
		try {
			setMagic(Hex.decodeHex(hex.toCharArray()));
		} catch ( DecoderException e ) {
			// fail silently, sorry
		}
	}

	public byte[] getMagic() {
		return magic;
	}

	public void setMagic(byte[] magic) {
		this.magic = magic;
	}

	public String getMagicEOFHex() {
		if ( getMagicEOF() == null ) {
			return null;
		}
		return Hex.encodeHexString(getMagicEOF());
	}

	public void setMagicEOFHex(String hex) {
		if ( hex == null || (hex.length() % 2) == 1 ) {
			setMagicEOF(null);
		}
		try {
			setMagicEOF(Hex.decodeHex(hex.toCharArray()));
		} catch ( DecoderException e ) {
			// fail silently, sorry
		}
	}

	public byte[] getMagicEOF() {
		return magicEOF;
	}

	public void setMagicEOF(byte[] magicEOF) {
		this.magicEOF = magicEOF;
	}

	public int getReadSize() {
		return readSize;
	}

	public void setReadSize(int readSize) {
		this.readSize = readSize;
	}

	public boolean isToggleDtr() {
		return toggleDtr;
	}

	public void setToggleDtr(boolean toggleDtr) {
		this.toggleDtr = toggleDtr;
	}

	public boolean isToggleRts() {
		return toggleRts;
	}

	public void setToggleRts(boolean toggleRts) {
		this.toggleRts = toggleRts;
	}

}
