package riot.protocols;

public class Raw implements Protocol<Raw> {

	public interface RawCommand extends Protocol.Command<Raw> {
	}

	public static class Read implements RawCommand {

	}

	public static class Write implements RawCommand {

	}

	@Override
	public Class<RawCommand> getCommandClass() {
		return RawCommand.class;
	}
}
