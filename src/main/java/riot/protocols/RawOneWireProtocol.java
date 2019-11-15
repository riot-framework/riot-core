package riot.protocols;

import com.pi4j.io.spi.SpiDevice;
import com.pi4j.io.w1.W1Device;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RawOneWireProtocol implements OneWireProtocol<RawOneWireProtocol.Command, Map> {

    public enum Command {READ}

    @Override
    public ProtocolDescriptor<RawOneWireProtocol.Command, Map> getDescriptor() {
        return new ProtocolDescriptor<RawOneWireProtocol.Command, Map>(RawOneWireProtocol.Command.class, Map.class);
    }

    @Override
    public void init(List<W1Device> dev) throws IOException {
        // No initialisation
    }

    @Override
    public Map<String, String> exec(List<W1Device> devices, RawOneWireProtocol.Command cmd) throws IOException {
        Map<String, String> results = new HashMap<String, String>(devices.size());
        for (W1Device device : devices) {
            results.put(device.getId(), device.getValue());
        }
        return results;
    }

    @Override
    public void shutdown(List<W1Device> dev) throws IOException {
        // No shutdown
    }

}
