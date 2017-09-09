package org.omi.gateway.bluetooth;

import java.util.Map;

public class GatewayConfiguration {

	private final static String PROPERTY_INAME = "iname";
	private final static String PROPERTY_SCAN = "scan_enable";
	private final static String PROPERTY_SCANTIME = "scan_time";
	private final static String PROPERTY_PERIOD = "period";
	private final static String PROPERTY_REPO_URL = "repo_url";
	
	public String iname = "hci0";
	public boolean enableScan = false;
	public int scantime = 5;
	public int period = 10;
	public String repo_url = "http://vorto.eclipse.org";
		
	public static GatewayConfiguration newConfiguration() {
		return new GatewayConfiguration ();
	}
	
	public static GatewayConfiguration configurationFrom(Map<String, Object> properties) {
		GatewayConfiguration configuration = new GatewayConfiguration ();
		
		if (properties != null) {
			if (properties.get(GatewayConfiguration.PROPERTY_SCAN) != null) {
				configuration.enableScan = (Boolean) properties.get(GatewayConfiguration.PROPERTY_SCAN);
			}
			if (properties.get(GatewayConfiguration.PROPERTY_SCANTIME) != null) {
				configuration.scantime = (Integer) properties.get(GatewayConfiguration.PROPERTY_SCANTIME);
			}
			if (properties.get(GatewayConfiguration.PROPERTY_PERIOD) != null) {
				configuration.period = (Integer) properties.get(GatewayConfiguration.PROPERTY_PERIOD);
			}
			if (properties.get(GatewayConfiguration.PROPERTY_INAME) != null) {
				configuration.iname = (String) properties.get(GatewayConfiguration.PROPERTY_INAME);
			}
			if (properties.get(GatewayConfiguration.PROPERTY_REPO_URL) != null) {
				configuration.repo_url = (String) properties.get(GatewayConfiguration.PROPERTY_REPO_URL);
			}
		}
		return configuration;
	}
	
}
