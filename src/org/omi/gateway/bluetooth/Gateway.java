package org.omi.gateway.bluetooth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.kura.bluetooth.BluetoothAdapter;
import org.eclipse.kura.bluetooth.BluetoothDevice;
import org.eclipse.kura.bluetooth.BluetoothGatt;
import org.eclipse.kura.bluetooth.BluetoothLeScanListener;
import org.eclipse.kura.bluetooth.BluetoothService;
import org.eclipse.kura.cloud.CloudService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.data.DataService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Gateway implements ConfigurableComponent, BluetoothLeScanListener {

	private static final Logger s_logger = LoggerFactory.getLogger(Gateway.class);
    private static final String APP_ID = "org.omi.gateway.bluetooth.Gateway";

    private GatewayConfiguration configuration;
        
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothService bluetoothService;
    private Map<String, DeviceGatt> bluetoothDevices;
    
	private ScheduledExecutorService worker;
	private ScheduledFuture<?> handle;
	private long startTime;
	
	private DataService dataService;
	
	public void setBluetoothService(BluetoothService bluetoothService) {
		this.bluetoothService = bluetoothService;
	}
	
	public void unsetBluetoothService(BluetoothService bluetoothService) {
		this.bluetoothService = null;
	}
	
	public void setDataService(DataService dataService) {
		this.dataService = dataService;
	}
	
	public void unsetDataService(DataService dataService) {
		this.dataService = null;
	}
	
    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        s_logger.info("Bundle " + APP_ID + " has started!");
        
        readProperties(properties);
		runApplication();
    }

    protected void deactivate(ComponentContext componentContext) {
    	
    	cleanup();
    	
        s_logger.info("Bundle " + APP_ID + " has stopped!");
    }
    
	protected void updated(Map<String, Object> properties) {
		
		readProperties(properties);
		cleanup();						
		runApplication();
		
		s_logger.debug("Bundle " + APP_ID + " is updatig the configuration");
	}

	private void runApplication() {
		
		bluetoothDevices = new HashMap<String, DeviceGatt>();
					
		if (configuration.enableScan) {
			
			this.worker = Executors.newScheduledThreadPool(1); /*.newSingleThreadScheduledExecutor(); */
			try {
				// Get Bluetooth adapter and ensure it is enabled
				this.bluetoothAdapter = this.bluetoothService.getBluetoothAdapter(configuration.iname);
				if (this.bluetoothAdapter != null) {
					s_logger.info("Bluetooth adapter interface => " + configuration.iname);
					s_logger.info("Bluetooth adapter address => " + this.bluetoothAdapter.getAddress());
					s_logger.info("Bluetooth adapter le enabled => " + this.bluetoothAdapter.isLeReady());

					if (!this.bluetoothAdapter.isEnabled()) {
						s_logger.info("Enabling bluetooth adapter...");
						this.bluetoothAdapter.enable();
						s_logger.info("Bluetooth adapter address => " + this.bluetoothAdapter.getAddress());
					}
					this.startTime = 0;
					this.handle = this.worker.scheduleAtFixedRate(new Runnable() {

						public void run() {
							checkScan();
						}
					}, 0, 1, TimeUnit.SECONDS);
				} else {
					s_logger.warn("No Bluetooth adapter found ...");
				}
			} catch (Exception e) {
				s_logger.error("Error starting component", e);
				throw new ComponentException(e);
			}
		}
	}
	
	private void cleanup() {
		s_logger.debug("Cleaning up Gateway App...");
		if (this.bluetoothAdapter != null && this.bluetoothAdapter.isScanning()) {
			s_logger.debug("m_bluetoothAdapter.isScanning");
			this.bluetoothAdapter.killLeScan();
		}
		
		for (DeviceGatt devGatt : bluetoothDevices.values())
		{
			if (devGatt != null)
			{
				devGatt.finalize();
			}
		}

		// cancel a current worker handle if one if active
		if (this.handle != null) {
			this.handle.cancel(true);
		}

		// shutting down the worker and cleaning up the properties
		if (this.worker != null) {
			this.worker.shutdown();
		}

		// cancel bluetoothAdapter
		this.bluetoothAdapter = null;
	}

	// --------------------------------------------------------------------
	//
	// Main task executed every second
	//
	// --------------------------------------------------------------------

	void checkScan() {

		// Scan for Bluetooth devices
		if (this.bluetoothAdapter.isScanning()) {
			s_logger.debug("m_bluetoothAdapter.isScanning");
			if (System.currentTimeMillis() - this.startTime >= configuration.scantime * 1000) {
				this.bluetoothAdapter.killLeScan();
			}
		} else {
			if (System.currentTimeMillis() - this.startTime >= configuration.period * 1000) {
				s_logger.debug("startLeScan");
				this.bluetoothAdapter.startLeScan(this);
				this.startTime = System.currentTimeMillis();
			}
		}

	}

	// --------------------------------------------------------------------
	//
	// Private Methods
	//
	// --------------------------------------------------------------------

	private void readProperties(Map<String, Object> properties) {
		if (properties != null) {
			configuration = GatewayConfiguration.configurationFrom(properties);
		}
	}

	// --------------------------------------------------------------------
	//
	// BluetoothLeScanListener APIs
	//
	// --------------------------------------------------------------------
	public void onScanFailed(int errorCode) {
		s_logger.error(APP_ID + ": Error during Bluetooth scan");

	}

	public void onScanResults(List<BluetoothDevice> scanResults) {
		for (BluetoothDevice device : scanResults) {
			try {
				if (device.getName().contains("Sensor") && !bluetoothDevices.containsKey(device.getAdress()))
				{
					BluetoothGatt gatt = device.getBluetoothGatt();
					s_logger.info("Connecting to device with address " +  device.getAdress() + " and name " + device.getName());

					DeviceGatt deviceGatt = DeviceGattBuilder.fromGatt(gatt, new URI(configuration.repo_url), this.dataService);
					bluetoothDevices.put(device.getAdress(), deviceGatt);
					
					deviceGatt.startPolling();
				}
			} catch (Exception e) {
				bluetoothDevices.put(device.getAdress(), null);
				s_logger.error(e.getMessage());
			}
		}
	}
}
