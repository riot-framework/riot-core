package riot.protocols;

import java.io.IOException;

import com.pi4j.io.spi.SpiDevice;

public class RawSPIProtocol implements SPIProtocol<byte[], byte[]> {

	@Override
	public ProtocolDescriptor<byte[], byte[]> getDescriptor() {
		return new ProtocolDescriptor<byte[], byte[]>(byte[].class, byte[].class);
	}

	@Override
	public void init(SpiDevice dev) throws IOException {
		// No initialisation
	}

	@Override
	public byte[] exec(SpiDevice dev, byte[] message) throws IOException {
		return dev.write(message);
	}

	@Override
	public void shutdown(SpiDevice dev) throws IOException {
		// No shutdown
	}

}
