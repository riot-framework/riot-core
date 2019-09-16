package riot.protocols;

import java.io.IOException;

import com.pi4j.io.i2c.I2CDevice;

public interface I2CProtocol<I, O> {

	ProtocolDescriptor<I, O> getDescriptor();

	void init(I2CDevice dev) throws IOException;

	O exec(I2CDevice dev, I message) throws IOException;

	void shutdown(I2CDevice dev) throws IOException;

}
