package org.omi.gateway.bluetooth;

import java.util.Arrays;
import java.util.stream.Stream;

public class InitSequenceElement {

	private Characteristic characteristic;
	private Short[] value;
	
	public InitSequenceElement(Characteristic characteristic, String value)
	{
		this.characteristic = characteristic;
		setValue(value);
	}
	
	public void setValue(String value)
	{
		String[] digits = value.split(" ");
		if (digits != null)
		{
			Stream<Short> bytes = Arrays.stream(digits).map(s -> Short.parseShort(s, 16));
			this.value = bytes.toArray(Short[]::new);
		}
	}
	
	public String getValue()
	{
		String result = "";
		for (Short v : value)
		{
			String digit = String.format("%02x", v);
			result = result + digit;
		}
		return result;
	}
	
	public Characteristic getCharacteristic()
	{
		return this.characteristic;
	}
}
