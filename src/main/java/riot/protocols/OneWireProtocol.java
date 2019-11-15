package riot.protocols;

import com.pi4j.io.spi.SpiDevice;
import com.pi4j.io.w1.W1Device;

import java.io.IOException;
import java.util.List;

public interface OneWireProtocol<I, O> extends Protocol<I, O> {

    void init(List<W1Device> dev) throws IOException;

    O exec(List<W1Device> dev, I message) throws IOException;

    void shutdown(List<W1Device> dev) throws IOException;

}
