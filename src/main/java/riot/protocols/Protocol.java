package riot.protocols;

public interface Protocol<P extends Protocol<P>> {
	
	public interface Command<P extends Protocol<P>> {
		
	}
	
	public interface Responce<C extends Command<P>, P extends Protocol<P>> {
		
	}
	
	public Class<? extends Command<P>> getCommandClass();
}
