package riot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.Timeout;
import riot.actors.GPIOInActor;
import riot.actors.GPIOOutActor;

/**
 * A builder object that allows the configuration of a GPIO pin. After a pin has
 * been configured, utility methods allows to create Akka Streams sources,
 * sinks, flows, or just an akka props object that can be used to instantiate an
 * Actor. Start by specifying the pin type and number by calling one of the In
 * or Out methods, then configure the pin, then create an Akka Streams or Props
 * object, e.g.: <br>
 * 
 * <pre>
 * <br>
 * ActorSystem system = ActorSystem.create("blinkenlights"); <br>
 * Materializer mat = ActorMaterializer.create(system);<br>
 * Flow&lt;State, State, NotUsed&gt; gpio7 =
 * <b>GPIO.out(7).initiallyLow().shuttingDownLow().asFlow(system);</b><br>
 * Source&lt;GPIO.State, ?&gt; timerSource = Source.tick(Duration.ZERO,
 * Duration.ofMillis(500), <b>GPIO.State.TOGGLE</b>);<br>
 * timerSource.via(gpio7).run(mat);
 * </pre>
 *
 * @param <T>
 *            the type of GPIO configuration, IN or OUT.
 */
public abstract class GPIO<T extends GPIO<T>> {
	private static final Timeout ASK_TIMEOUT = Timeout.apply(1, TimeUnit.SECONDS);

	/**
	 * Models the state of a digital GPIO pin: ON (high), OFF (low), or TOGGLE (will
	 * become ON if it was OFF, and vice-versa).
	 */
	public enum State {
		HIGH, LOW, TOGGLE
	}

	/**
	 * "Get" command for input GPIO
	 */
	public static class Get {
	}

	/*
	 * Settings
	 */
	private Pin pin;

	private PinMode pinMode;

	private PinPullResistance pullResistance = PinPullResistance.OFF;

	private String name;

	/**
	 * @return this pin's PI4J Pin object.
	 * @see Pin
	 */
	public Pin getPin() {
		return pin;
	}

	/**
	 * @return a Props object that can be used to create an Akka Actor
	 */
	public abstract Props asProps();

	/**
	 * The constructed GPIO pin will be analog.
	 * 
	 * @return this GPIO Builder instance (for chaining).
	 */
	public abstract T analog();

	/**
	 * The constructed GPIO pin will be digital.
	 * 
	 * @return this GPIO Builder instance (for chaining).
	 */
	public abstract T digital();

	// This is only used internally to make chaining work
	protected abstract T getThis();

	/**
	 * @return this pin's mode as per the PI4J library's <code>PinMode</code> enum.
	 */
	public PinMode getPinMode() {
		return pinMode;
	}

	/**
	 * The constructed GPIO pin will have an internal pullup resistor. This is
	 * useful for input pins, for example connected via a switch to ground.
	 * 
	 * @return this GPIO Builder instance (for chaining).
	 */
	public T withPullupResistor() {
		pullResistance = PinPullResistance.PULL_UP;
		return getThis();
	}

	/**
	 * The constructed GPIO pin will have an internal pulldown resistor. This is
	 * useful for input pins, for example connected via a switch to 3.3v.
	 * 
	 * @return this GPIO Builder instance (for chaining).
	 */
	public T withPulldownResistor() {
		pullResistance = PinPullResistance.PULL_DOWN;
		return getThis();
	}

	/**
	 * @return this pin's pull resistance as per the PI4J library's
	 *         <code>PinPullResistance</code> enum.
	 */
	public PinPullResistance getPullResistance() {
		return pullResistance;
	}

	/**
	 * The constructed GPIO pin will be named as specified. This is only used in
	 * logging and debugging.
	 * 
	 * @param name
	 *            this pin's name
	 * 
	 * @return this GPIO Builder instance (for chaining).
	 */
	public T named(String name) {
		this.name = name;
		return getThis();
	}

	/**
	 * @return this pin's name
	 */
	public String getName() {
		return name;
	}

	/*
	 * Output Pin
	 */
	/**
	 * The constructed GPIO pin will be an output pin.
	 * 
	 * @param pin
	 *            the pin number on the device (this is the device-specific number,
	 *            NOT the Broadcom internal numbering).
	 * @return a GPIO Builder instance.
	 */
	public static Out out(int pin) {
		return new Out(Utils.asPin(pin));
	}

	/**
	 * The constructed GPIO pin will be an output pin.
	 * 
	 * @param pin
	 *            the PI4J pin object.
	 * @return a GPIO Builder instance.
	 */
	public static Out out(Pin pin) {
		return new Out(pin);
	}

	public static class Out extends GPIO<Out> {

		private PinState initialState = null;

		private PinState shutdownState = null;

		private Double initialValue = null;

		private Out(Pin pin) {
			super.pin = pin;
			super.pinMode = PinMode.DIGITAL_OUTPUT;
			pin.getName();
		}

		/**
		 * The constructed GPIO output pin will be analog.
		 * 
		 * @return this GPIO Builder instance (for chaining).
		 */
		@Override
		public Out analog() {
			super.pinMode = PinMode.ANALOG_OUTPUT;
			return this;
		}

		/**
		 * The constructed GPIO output pin will be digital.
		 * 
		 * @return this GPIO Builder instance (for chaining).
		 */
		@Override
		public Out digital() {
			super.pinMode = PinMode.DIGITAL_OUTPUT;
			return this;
		}

		/**
		 * The constructed GPIO pin will be a PWM output pin.
		 * 
		 * @return this GPIO Builder instance (for chaining).
		 */
		public Out pwm() {
			super.pinMode = PinMode.PWM_OUTPUT;
			return this;
		}

		/**
		 * The constructed GPIO pin will be a Tone output pin. (not fully implemented
		 * yet).
		 * 
		 * @return this GPIO Builder instance (for chaining).
		 */
		protected Out tone() {
			super.pinMode = PinMode.PWM_TONE_OUTPUT;
			return this;
		}

		/**
		 * The pin will be high when it starts (no GPIO.State object will be emitted for
		 * that state transition).This has no effect on pins that are not digital.
		 * 
		 * @return this GPIO Builder instance (for chaining).
		 */
		public Out initiallyHigh() {
			initialState = PinState.HIGH;
			return this;
		}

		/**
		 * The pin will be low when it starts (no GPIO.State object will be emitted for
		 * that state transition). This has no effect on pins that are not digital.
		 * 
		 * @return this GPIO Builder instance (for chaining).
		 */
		public Out initiallyLow() {
			initialState = PinState.LOW;
			return this;
		}

		/**
		 * @return this pin's initial state as per the PI4J library's
		 *         <code>PinState</code> enum (for digital pins).
		 */
		public PinState getInitialState() {
			return initialState;
		}

		/**
		 * The pin will be at the specified value when it starts (no GPIO.State object
		 * will be emitted for that state transition). This has no effect on pins that
		 * are not analog or PWM.
		 * 
		 * @param value
		 *            the value to use initially
		 * 
		 * @return this GPIO Builder instance (for chaining).
		 */
		public Out initiallyAt(double value) {
			initialValue = value;
			return this;
		}

		/**
		 * @return this pin's initial value (for analog or PWM pins).
		 */
		public Double getInitialValue() {
			return initialValue;
		}

		/**
		 * The pin will become high when it shuts down (no GPIO.State object will be
		 * emitted for that last state transition). This has no effect on pins that are
		 * not digital.
		 * 
		 * @return this GPIO Builder instance (for chaining).
		 */
		public Out shuttingDownHigh() {
			shutdownState = PinState.HIGH;
			return this;
		}

		/**
		 * The pin will become low when it shuts down (no GPIO.State object will be
		 * emitted for that last state transition). This has no effect on pins that are
		 * not digital.
		 * 
		 * @return this GPIO Builder instance (for chaining).
		 */
		public Out shuttingDownLow() {
			shutdownState = PinState.LOW;
			return this;
		}

		/**
		 * @return this pin's state on shutdown, as per the PI4J library's
		 *         <code>PinState</code> enum (for digital pins).
		 */
		public PinState getShutdownState() {
			return shutdownState;
		}

		/**
		 * This is used to allow the superclass to do chaining properly
		 */
		@Override
		protected Out getThis() {
			return this;
		}

		/**
		 * Creates an Akka Streams sink that sets the pin's state accordingly when it
		 * receives a <code>GPIO.State</code> message.
		 * 
		 * @param system
		 *            the ActorSystem in which to create the underlying Akka actor
		 * @see State
		 * @return a sink object that can be used in Akka Streams
		 */
		public Sink<State, NotUsed> asSink(ActorSystem system) {
			return Flow.of(State.class).ask(system.actorOf(asProps()), State.class, Timeout.apply(1, TimeUnit.SECONDS))
			        .to(Sink.ignore());
		}

		/**
		 * Creates an Akka Streams source that sets the pin's state accordingly when it
		 * receives a <code>GPIO.State</code> message, then emits a
		 * <code>GPIO.State</code> message whith the pin's new state.
		 * 
		 * @param system
		 *            the ActorSystem in which to create the underlying Akka actor
		 * @see State
		 * @return a flow object that can be used in Akka Streams
		 */
		public Flow<State, State, NotUsed> asFlow(ActorSystem system) {
			return Flow.fromGraph(GraphDSL.create(b -> {
				return b.add(Flow.of(State.class).ask(system.actorOf(asProps()), State.class, ASK_TIMEOUT));
			}));
		}

		/**
		 * @return a Props object that can be used to create an Akka Actor
		 */
		@Override
		public Props asProps() {
			return Props.create(GPIOOutActor.class, this);
		}

		/**
		 * Set the GPIO pin to a fixed value.
		 * 
		 * @param system
		 *            the ActorSystem in which to create the underlying Akka actor
		 * @param state
		 *            the state at which this GPIO is to be fixed
		 */
		public void fixedAt(ActorSystem system, GPIO.State state) {
			switch (state) {
			case HIGH:
				system.actorOf(digital().initiallyHigh().asProps());
				break;
			default:
				system.actorOf(digital().initiallyLow().asProps());
				break;
			}
		}

		/**
		 * Set the GPIO pin to a fixed value.
		 * 
		 * @param system
		 *            the ActorSystem in which to create the underlying Akka actor
		 * @param value
		 *            the value at which this GPIO is to be fixed
		 */
		public void fixedAt(ActorSystem system, double value) {
			system.actorOf(analog().initiallyAt(value).asProps());
		}
	}

	/*
	 * Input Pin
	 */
	/**
	 * The constructed GPIO pin will be an input pin.
	 * 
	 * @param pin
	 *            the pin number on the device (this is the device-specific number,
	 *            NOT the Broadcom internal numbering).
	 * @return a GPIO Builder instance.
	 */
	public static In in(int pin) {
		return new In(Utils.asPin(pin));
	}

	/**
	 * The constructed GPIO pin will be an input pin.
	 * 
	 * @param pin
	 *            the PI4J pin object.
	 * @return a GPIO Builder instance.
	 */
	public static In in(Pin pin) {
		return new In(pin);
	}

	public static class In extends GPIO<In> {

		private Set<ActorRef> listeners = new HashSet<ActorRef>();

		private In(Pin pin) {
			super.pin = pin;
			super.pinMode = PinMode.DIGITAL_INPUT;
			pin.getName();
		}

		/**
		 * The constructed GPIO output pin will be analog.
		 * 
		 * @return this GPIO Builder instance (for chaining).
		 */
		@Override
		public In analog() {
			super.pinMode = PinMode.ANALOG_INPUT;
			return this;
		}

		/**
		 * The constructed GPIO output pin will be digital.
		 * 
		 * @return this GPIO Builder instance (for chaining).
		 */
		@Override
		public In digital() {
			super.pinMode = PinMode.DIGITAL_INPUT;
			return this;
		}

		/**
		 * Upon construction, the underlying actor will notify each of the listeners
		 * passed whenever the state of the pin changes. This is done by sending a
		 * GPIO.State message for digital pins, an Integer message for PWM pins, and a
		 * Double message for analog pins.
		 * 
		 * @param listeners
		 *            the ActorRef of Actors to be notified
		 * 
		 * @return this GPIO Builder instance (for chaining).
		 */
		public In notifyActor(ActorRef... listeners) {
			this.listeners.addAll(Arrays.asList(listeners));
			return this;
		}

		public Set<ActorRef> getListeners() {
			return listeners;
		}

		public boolean hasListener() {
			return listeners.size() > 0;
		}

		/**
		 * This is used to allow the superclass to do chaining properly
		 */
		@Override
		protected In getThis() {
			return this;
		}

		/**
		 * Creates an Akka Streams source that emits a <code>GPIO.State</code> (for
		 * digital pins), a Double (for analog pins) or an Integer (for PWM pins)
		 * message every time the state of this pin changes. If this pin changes faster
		 * than data can be processed in the stream, only the last state of the pin is
		 * emitted.
		 * 
		 * @param system
		 *            the ActorSystem in which to create the underlying Akka actor
		 * @param mat
		 *            the Materializer which will be used to materialize the stream
		 * @return a source that can be used in Akka Streams
		 */
		public Source<State, NotUsed> asSource(ActorSystem system, Materializer mat) {
			return asSource(system, mat, 1, OverflowStrategy.dropTail());
		}

		/**
		 * Creates an Akka Streams source that emits a <code>GPIO.State</code> (for
		 * digital pins), a Double (for analog pins) or an Integer (for PWM pins)
		 * message every time every time the state of this pin changes.
		 * 
		 * @param system
		 *            the ActorSystem in which to create the underlying Akka actor
		 * @param mat
		 *            the Materializer which will be used to materialize the stream
		 * @param bufferSize
		 *            the number of items to keep in the buffer
		 * @param overflowStrategy
		 *            how to react if the buffer is full
		 * @see State
		 * @return a source that can be used in Akka Streams
		 */
		public Source<State, NotUsed> asSource(ActorSystem system, Materializer mat, int bufferSize,
		        OverflowStrategy overflowStrategy) {
			final Source<State, ActorRef> source = Source.actorRef(bufferSize, overflowStrategy)
			        .collectType(State.class);
			Pair<ActorRef, Source<State, NotUsed>> preMat = source.preMaterialize(mat);
			system.actorOf(addListeners(preMat.first()).asProps());
			return preMat.second();
		}

		/**
		 * Creates an Akka Streams element that sets the pin's value every time it
		 * receives a <code>GPIO.State</code> (for digital pins), Double (for analog
		 * pins) or Integer (for PWM pins) message. It then sends a message containing
		 * the new value of the pin (<code>GPIO.State</code>, Double or Integer).
		 * 
		 * @param system
		 *            the ActorSystem in which to create the underlying Akka actor
		 * @see State
		 * @see Get
		 * @return a source that can be used in Akka Streams
		 */
		public Flow<State, State, NotUsed> asFlow(ActorSystem system) {
			return Flow.of(State.class).ask(system.actorOf(asProps()), State.class, ASK_TIMEOUT);
		}

		/**
		 * @return a Props object that can be used to create an Akka Actor. The actor is
		 *         able to process <code>GPIO.State</code> (for digital pins), Double
		 *         (for analog pins) or Integer (for PWM pins) messages.
		 */
		@Override
		public Props asProps() {
			return Props.create(GPIOInActor.class, this);
		}

	}

}
