package org.omi.gateway.bluetooth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Service {

	private UUID uuid;
	private Map<UUID, Characteristic> characteristics;

	public Service()
	{
		characteristics = new HashMap<UUID, Characteristic>();
	}
	
	public UUID getUuid() {
		return uuid;
	}

	public void setUuid(UUID uuid) {
		this.uuid = uuid;
	}

	public void addCharacteristics(Characteristic characteristic)
	{
		characteristics.put(characteristic.getUuid(), characteristic);
	}
	
	public Map<UUID, Characteristic> getCharacteristics() {
		return characteristics;
	}

	public void setCharacteristics(Map<UUID, Characteristic> characteristics) {
		this.characteristics = characteristics;
	}
}
