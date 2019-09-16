package riot.protocols;

import riot.protocols.Raw.RawOp;
import riot.protocols.Raw.RawOp.Result;

public class Raw implements Protocol<RawOp, RawOp.Result> {

	public interface RawOp {
		public interface Result {
		}
	}

	public static class Read implements RawOp {

	}

	public static class Write implements RawOp {

	}

	@Override
	public Class<RawOp> getInputMessageClass() {
		return RawOp.class;
	}

	@Override
	public Class<Result> getOutputMessageClass() {
		return RawOp.Result.class;
	}

}
