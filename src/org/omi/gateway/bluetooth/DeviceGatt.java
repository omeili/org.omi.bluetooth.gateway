package org.omi.gateway.bluetooth;

import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.bluetooth.BluetoothGatt;
import org.eclipse.kura.bluetooth.BluetoothGattCharacteristic;
import org.eclipse.kura.bluetooth.BluetoothLeNotificationListener;
import org.eclipse.kura.data.DataService;
import org.eclipse.vorto.repository.api.ModelId;
import org.eclipse.vorto.repository.api.content.BooleanAttributeProperty;
import org.eclipse.vorto.repository.api.content.BooleanAttributePropertyType;
import org.eclipse.vorto.repository.api.content.FunctionblockModel;
import org.eclipse.vorto.repository.api.content.IPropertyAttribute;
import org.eclipse.vorto.repository.api.content.Infomodel;
import org.eclipse.vorto.repository.api.content.ModelProperty;
import org.eclipse.vorto.repository.api.content.Stereotype;
import org.eclipse.vorto.service.mapping.DataInput;
import org.eclipse.vorto.service.mapping.DataMapperBuilder;
import org.eclipse.vorto.service.mapping.IDataMapper;
import org.eclipse.vorto.service.mapping.IMappingSpecification;
import org.eclipse.vorto.service.mapping.MappingContext;
import org.eclipse.vorto.service.mapping.ditto.DittoMapper;
import org.eclipse.vorto.service.mapping.ditto.DittoOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviceGatt implements BluetoothLeNotificationListener {

	private static final Logger s_logger = LoggerFactory.getLogger(DeviceGatt.class);
	
	private Device device;
	private IMappingSpecification mappingSpec;
	private BluetoothGatt gatt;
	private DataService dataService;
	private ScheduledExecutorService pollThread;
	private ScheduledFuture<?> pollThreadHandle;

	
	public DeviceGatt(BluetoothGatt gatt, IMappingSpecification mappingSpec, DataService dataService)
	{
		this.gatt = gatt;
		this.mappingSpec = mappingSpec;
		createDeviceModel(mappingSpec);
		this.dataService = dataService; 
		
		this.pollThread = Executors.newScheduledThreadPool(1); 
	}
	
	private void createDeviceModel(IMappingSpecification mappingSpec)
	{
		this.device = new Device();
		
		Infomodel infomodel = mappingSpec.getInfoModel();

		for (ModelProperty fbprop : infomodel.getFunctionblocks())
		{
			Service service = new Service();
			ModelId fbid = (ModelId) fbprop.getType();
			FunctionblockModel fb = mappingSpec.getFunctionBlock(fbid);
			s_logger.debug("### Found function block: " + fb.getDisplayName() + " of stereotypes ");
			
			for (Stereotype stereotype : fb.getStereotypes())
			{
				s_logger.debug(stereotype.getName() + ", ");
				if (stereotype.getAttributes().containsKey("uuid"))
				{
					String uuid = stereotype.getAttributes().get("uuid");
					
					s_logger.debug(fbprop.getName() + ": found service uuid = " + uuid);
					
					service.setUuid(UUID.fromString(uuid));
				}
			}
			
			for (ModelProperty prop : fb.getConfigurationProperties())
			{
				s_logger.debug("###### Found function block config prop " + prop.getName() + ": ");
				
				Characteristic ch = new Characteristic();
				
				for (Stereotype stereotype : prop.getStereotypes())
				{
					if (stereotype.getAttributes().containsKey("uuid"))
					{
						ch.setUuid(UUID.fromString(stereotype.getAttributes().get("uuid").toString()));
						service.addCharacteristics(ch);
					}
					if (stereotype.getAttributes().containsKey("onConnect"))
					{
						InitSequenceElement elt = new InitSequenceElement(ch, stereotype.getAttributes().get("onConnect"));
						device.addInitSequenceElement(elt);
						s_logger.debug("ON CONNECT: " + elt.getCharacteristic().getUuid() + " = " + elt.getValue());
					}
				}
			}
			for (ModelProperty prop : fb.getStatusProperties())
			{
				s_logger.debug("###### Found function block status prop " + prop.getName() + ": ");
				
				Characteristic ch = new Characteristic();
				
				for (IPropertyAttribute attr : prop.getAttributes())
				{
					s_logger.debug(" --- Attr  " + attr.toString());
					if (attr instanceof BooleanAttributeProperty)
					{
						BooleanAttributeProperty attrprop = (BooleanAttributeProperty)attr;
						if ((attrprop.getType() == BooleanAttributePropertyType.EVENTABLE) && (attrprop.isValue()))
						{
							ch.setNotified(true);
							s_logger.debug("    -> notification");
						}
					}
				}
				for (Stereotype stereotype : prop.getStereotypes())
				{
					if (stereotype.getAttributes().containsKey("uuid"))
					{
						ch.setUuid(UUID.fromString(stereotype.getAttributes().get("uuid").toString()));
						service.addCharacteristics(ch);
					}
					for (String key : stereotype.getAttributes().keySet())
					{
						s_logger.debug(" --- [" + stereotype.getName() + "] " + key + " to " + stereotype.getAttributes().get(key));
					}
				}
			}	
			
			device.addService(service);
		}
		setupDeviceModel();
	}
	
	private void setupDeviceModel()
	{
		for (BluetoothGattCharacteristic gattCh : this.gatt.getCharacteristics("0x0001", "0xFFFF"))
		{
			for (Entry<UUID, Service> entry : device.getServices().entrySet())
			{
				if (entry.getValue().getCharacteristics().containsKey(gattCh.getUuid())) {
					Characteristic ch = entry.getValue().getCharacteristics().get(gattCh.getUuid());
					ch.setHandle(gattCh.getValueHandle());
					this.device.addCharacteristic(ch);
					s_logger.debug("Adding characteristic " + ch.getUuid() + " and handle " + ch.getHandle());
				}
			}
		}
		
		this.gatt.setBluetoothLeNotificationListener(this);
	}

	protected void poll()
	{
		try {
			for (Entry<String, Characteristic> entry : this.device.getCharacteristics().entrySet())
			{
				String handle = entry.getValue().getHandle();
				Characteristic ch = entry.getValue();
				
				String value = "";
				try {
					value = this.gatt.readCharacteristicValue(handle);
					ch.setData(value);
					s_logger.debug("Set " + ch.getData().length + " bytes of data to " + ch.getUuid());
				} catch (Exception e) {
					s_logger.error(e.getMessage(), e);
				}
			}

			DataMapperBuilder builder = IDataMapper.newBuilder();
			builder.withSpecification(this.mappingSpec);
			DittoMapper mapper = builder.buildDittoMapper();
			DittoOutput mappedDittoOutput = mapper.map(DataInput.newInstance().fromObject(this.device), MappingContext.empty());		
			s_logger.info("Payload mapper output: " + mappedDittoOutput.toJson());
			
			dataService.publish("telemetry/#account-name/#client-id", mappedDittoOutput.toJson().getBytes(), 0, false, 5);
			
		} catch (Exception e)
		{
			s_logger.error(e.getMessage(), e);
		}
	}
	
	public void startPolling()
	{
		s_logger.info("Starting polling thread");
		
		s_logger.info("ON CONNECT");
		List<InitSequenceElement> initSequence = device.getInitSequence();
		for (InitSequenceElement elt : initSequence)
		{
			s_logger.info("ON CONNECT: setting " + elt.getCharacteristic().getUuid() + " to " + elt.getValue());
			gatt.writeCharacteristicValue(elt.getCharacteristic().getHandle(), elt.getValue());
		}
		
		this.pollThreadHandle = this.pollThread.scheduleAtFixedRate(new Runnable() {

			public void run() {
				poll();
			}
		}, 5, 30, TimeUnit.SECONDS);
	}
	
	@Override
	public void onDataReceived(String handle, String value) {

		s_logger.info("Notification for handle " + handle +  " with value " + value);
		
		if (this.device.getCharacteristics().containsKey(handle))
		{
			Characteristic ch = this.device.getCharacteristics().get(handle);
			
			s_logger.info("Setting characteristic " + ch.getUuid() + " to " + value);
			ch.setData(value);
		}
	}

	public void finalize()
	{
		try {
			pollThreadHandle.cancel(false);
			
			if (gatt.checkConnection() == true)
			{
				gatt.disconnect();
			}
		} catch (KuraException e) {
			s_logger.error(e.getMessage());
		}
	}
}
