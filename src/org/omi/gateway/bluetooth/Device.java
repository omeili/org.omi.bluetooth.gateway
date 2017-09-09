package org.omi.gateway.bluetooth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

public class Device  {

	private Map<UUID, Service> services;
	private Map<String, Characteristic> characteristics;
	private String modelNumber;
	private List<InitSequenceElement> initSequence;

	public Device()
	{
		services = new HashMap<UUID, Service>();
		characteristics = new HashMap<String, Characteristic>();
		initSequence = new Vector<InitSequenceElement>();
	}

	public Map<UUID, Service> getServices() {
		return services;
	}

	public void addService(Service service) {
		this.services.put(service.getUuid(), service);
	}
	
	public void setServices(Map<UUID, Service> services) {
		this.services = services;
	}

	public String getModelNumber() {
		return modelNumber;
	}

	public void setModelNumber(String modelNumber) {
		this.modelNumber = modelNumber;
	}

	public void addCharacteristic(Characteristic characteristic)
	{
		this.characteristics.put(characteristic.getUuid().toString(), characteristic);
	}
	
	public Map<String, Characteristic> getCharacteristics() {
		return characteristics;
	}

	public void setCharacteristics(Map<String, Characteristic> characteristics) {
		this.characteristics = characteristics;
	}

	public void addInitSequenceElement(InitSequenceElement elt)
	{
		this.initSequence.add(elt);
	}
	
	public List<InitSequenceElement> getInitSequence()
	{
		return this.initSequence;
	}
	
	public boolean isNotificationComplete()
	{
		boolean result = true;
		
		for (Characteristic ch : this.characteristics.values())
		{
			if (ch.isNotified())
			{
				result &= ch.isUpdated();
			}
		}
		
		return result;
	}

}
