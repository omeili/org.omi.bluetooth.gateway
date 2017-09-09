package org.omi.gateway.bluetooth;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.bluetooth.BluetoothGatt;
import org.eclipse.vorto.repository.api.IModelResolver;
import org.eclipse.vorto.repository.api.ModelInfo;
import org.eclipse.vorto.repository.api.resolver.ResolveQuery;
import org.eclipse.vorto.repository.client.RepositoryClientBuilder;
import org.eclipse.vorto.service.mapping.IMappingSpecification;
import org.eclipse.vorto.service.mapping.MappingSpecificationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviceGattBuilder {

	private static final Logger s_logger = LoggerFactory.getLogger(DeviceGattBuilder.class);
	
	private static Map<String, IMappingSpecification> deviceModels;
	
	static DeviceGatt fromGatt(BluetoothGatt gatt, URI repo_url)
	{
		DeviceGatt deviceGatt = null;
		
		try {
			if (gatt.connect())
			{
				String modelNumber = gatt.readCharacteristicValueByUuid(UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb"));
				if ((modelNumber == null) || modelNumber.equals(""))
				{
					gatt.disconnect();
				}
				else
				{
					IMappingSpecification mappingSpec = null;
					try {
						mappingSpec = getMappingSpec(modelNumber, repo_url);
					} catch (Exception ex)
					{
						s_logger.error(ex.getMessage(), ex);
					}
					if (mappingSpec == null)
					{
						gatt.disconnect();
					}
					else
					{
						deviceGatt = new DeviceGatt(gatt, mappingSpec);
					}
				}
			}
		} catch (Exception e) {
			s_logger.error(e.getMessage(), e);
			try {
				if (gatt.checkConnection())
				{
					gatt.disconnect();
				}
			} catch (KuraException e1) {
				s_logger.error(e1.getMessage(), e1);
			}
		}
		
		return deviceGatt;
	}
	
	private static IMappingSpecification getMappingSpec(String modelNumber, URI repo_url)
	{
		ModelInfo model = null;
		
		if (deviceModels == null)
		{
			deviceModels = new HashMap<String, IMappingSpecification>();
		} else if (deviceModels.containsKey(modelNumber))
		{
			return deviceModels.get(modelNumber);
		}
		
		if (modelNumber == null)
		{
			return null;
		}
		else
		{
			modelNumber = modelNumber.replace(" ", "");
		}
		
		try {
			RepositoryClientBuilder builder = RepositoryClientBuilder.newBuilder().setBaseUrl(repo_url.toString());
			IModelResolver resolver = builder.buildModelResolverClient();
			model = resolver.resolve(new ResolveQuery("blegatt", "modelNumber", modelNumber, "DeviceInfoProfile")).get();
		
			if (model == null)
			{
				return null;
			}
		} catch (Exception e) {
			s_logger.error(e.getMessage(), e);
			return null;
		}
		
		MappingSpecificationBuilder builder = IMappingSpecification.newBuilder();
		builder = builder.modelId(model.getId().getPrettyFormat());
		builder = builder.key("blegatt");
		IMappingSpecification spec = builder.build();
		return spec;
	}

}
