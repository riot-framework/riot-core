package riot.protocols;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.pi4j.io.i2c.I2CDevice;

import riot.protocols.RawI2CProtocol.Command;
import riot.protocols.RawI2CProtocol.Result;

public class RawI2CProtocol implements I2CProtocol<Command, Result> {

	public static class Command {
		private enum CommandType {
			READ, WRITE
		};

		private final CommandType type;
		private final int address;
		private final int length;
		private final byte[] payload;

		private Command(CommandType type, int address, int length, byte[] payload) {
			this.type = type;
			this.address = address;
			this.length = length;
			this.payload = payload;
		}

		public static Command read(int address, int length) {
			return new Command(CommandType.READ, address, length, null);
		}

		public static Command write(int address, byte... data) {
			return new Command(CommandType.WRITE, address, data.length, data);
		}
	}

	public static class Result {
		private final byte[] payload;

		private Result() {
			this.payload = new byte[0];
		}

		private Result(int length) {
			this.payload = new byte[length];
		}

		public InputStream getData() {
			return new ByteArrayInputStream(payload);
		}
	}

	@Override
	public ProtocolDescriptor<Command, Result> getDescriptor() {
		return new ProtocolDescriptor<Command, Result>(Command.class, Result.class);
	}

	@Override
	public void init(I2CDevice dev) throws IOException {
		// No initialisation
	}

	@Override
	public Result exec(I2CDevice dev, Command message) throws IOException {
		switch (message.type) {
		case READ:
			Result res = new Result(message.length);
			dev.read(message.address, res.payload, 0, message.length);
			return res;
		case WRITE:
			dev.write(message.address, message.payload);
			return new Result();
		default:
			throw new AssertionError(); // Unreacheable!
		}
	}

	@Override
	public void shutdown(I2CDevice dev) throws IOException {
		// No shutdown
	}

}
