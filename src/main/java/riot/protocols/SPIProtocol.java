package riot.protocols;

import java.io.IOException;

import com.pi4j.io.spi.SpiDevice;

public interface SPIProtocol<I, O> extends Protocol<I, O> {

    void init(SpiDevice dev) throws IOException;

    O exec(SpiDevice dev, I message) throws IOException;

    void shutdown(SpiDevice dev) throws IOException;

}
