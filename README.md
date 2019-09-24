# riot-core
The core RIoT framework provides Akka classes for the Raspberry Pi and similar small single-board computers. 

## GPIO

RIoT provides wrappers around the [PI4J] library's objects to access input and output GPIO pins. To create one of these wrappers, use the `GPIO` class.

### Creating Akka Streams components

Simply use the `in` method to access a pin as an input, `out` to access it as an output, passing the Wiring / Pi4J [pin number] as a parameter (these differ from the numberring scheme used by Broadcom for the CPU). Then call `asSource`, `asSink` or `asFlow` to create an Akka Streams source, sink, or flow object:

```java
gpio3InputSource = GPIO.in(3).asSource(system);
gpio7OutputSink = GPIO.out(7).asSink(system);
``` 
You can further configure the GPIO pin by calling methods before the final `asSource`, `asSink` or `asFlow` call: 

- Pins default to Digital mode (they have a state that is either High or Low, On or Off). call `analog()` for an Analog pin, that can have a range of values between 0 and 1. Call `pwm()` on an output pin to get a PWM output, as would be used for servos.
- The Broadcom CPU is able to switch resistors between a pin and the ground or positive. Use `withPullupResistor()` to have a resistor between the pin and positive, and `withPullDownResistor()` to have one between the pin and ground. For example, if you have wired a switch between an input pin and the ground, you'll want a resistor between that pin and positive, so that this pin's state is 'pulled' high when the switch is not pressed.
- Output pins can have a value set before the constructred RIoT object has received any message: Use `initiallyHigh()` and `initiallyLow()` to set this initial value with digital pins, and use `initiallyAt(...)` to set the initial value of an analog or PWM pin.
- Similarly, the value they will be reset to when the program terminates can be set using `shuttingDownHigh()` and `ishuttingDownLow()` for digital pins, and `shuttindDownAt(...)` for analog or PWM pins.

### Behaviour of the Akka Streams components

GPIO defines an enum called `State`, which models the states that a digital GPIO port can have.

- A digital GPIO `Source` will emit either `State.HIGH` or `State.LOW` whenever a Pin's state changes. 
- A digital GPIO `Sink`will accept `State.HIGH`, `State.LOW`, or `State.TOGGLE`, setting the state high, low, or the opposite of its current state, respectively.
- A digital GPIO `Flow` will accept the same messages, and emit the current state of the Pin: If `State.TOGGLE` is repeatedly sent to it, it will altenatingly emit `State.HIGH`, then `State.LOW`.

Analog pins can have any value between 0 and 1. The streams components behave similarly as with digital pins, but using `Float` objects:

- An analog GPIO `Source` will emit a `Float` whenever its measured value changes. 
- An analog GPIO `Sink` will accept `Float` messages, and set the value accordingly.
- A digital GPIO `Flow` will accept `Float` messages,  and emit the current value of the Pin.

PWM pins behave similarly, but accept, in addition to the `Float`, also `Integer` messages with a value expressed in number of PWM steps (bewteen 0 and 1024).

### Constructing Akka actors

Regular Akka actors can also be created. Using the GPIO class' `toProps()` methods to create an Akka Props object, then Akka's actorOf method  to get an `ActorRef`. `GPIO.State`, `Float` or `Integer` messages, depending on the GPIO type, can then be sent to it:

```java
Props gpio7Props = GPIO.out(7).asProps();
ActorRef gpio7 = system.actorOf(gpio7Props);
gpio7.tell(GPIO.State.HIGH, self());

```

For the actor to send updates about the pin's state, it will need to know the recipient's `ActorRef`. This is done at construction time using the `notifyActor` method:

```java
Props gpio7Props = GPIO.in(7). notifyActor(myOtherActor).asProps();
system.actorOf(gpio7Props);
```

## I2C

RIoT provides actors built on top of [Pi4J]'s capabilities which allow access to I2C devices. 

To communicate with an I2C device, RIoT will need you to specify which **I2C bus** to use, which **address** the device uses on that bus, and what the **protocol** between the Raspberry Pi and the I2C device is:

- The **bus number** used by the Paspberry Pi is typically **1**. Other devices may have more busses, typically numbered starting with 0.
- I2C devices normally have one **preset address**, or will allow you to switch between a few preset addresses by setting some of its pins high or low (to do this, connect them to GPIO ports, and set these high or low).
- The protocol is either **raw** if the RIoT application wishes to directly read and write bytes to rthe device, or special class than encapsulates a particular protocol (more on these later).

### 'Raw' access to the I2C bus

To access an I2C bus directly, use I2C's `rawDevice()` method, specify the bus and address, and finish with `asFlow` (for an Akka Streaming component) or `asProps` (to use a regular Akka Actor):

```java
Props props = I2C.rawDevice().onBus(1).at(0x23).asProps();
ActorRef rawDevice = system.actorOf(props);
rawDevice.tell(RawI2CProtocol.Command.write(0x14, (byte) 0x86), self());
```

The underlying actor will accept 2 commands: `RawI2CProtocol.Command.write(...)` and `RawI2CProtocol.Command.read(...)`. It will reply with a `RawI2CProtocol.Result` message, which will be empty for a write operation, or will contain the result of the Read operation.

The actor will reply to the sender of a `RawI2CProtocol.Command` with the `RawI2CProtocol.Result`. Similarly, a `Flow` component will recieve `RawI2CProtocol.Command` messages, and will emit `RawI2CProtocol.Result` messages in return.

### Accessing an I2C device

In RIoT, a 'protocol class' encapsulates the specific protocol for a device, defining the commands that can be issued to it, and describing how these commands are implemented (by reading and writing through the bus to the device). 

On the caller side, this class need only be instantiated (possibly passing some additional settings specific to the device) and passed to the I2C object through the `device(...)` method. Typically, this class will also define constants containing the **default addresses** the device uses, and the **commands it will accept** from the caller:

```java
Props props = I2C.device(BMA280.class)
                 .onBus(1)
                 .at(BMA280Constants.DEFAULT_ADDRESS)
                 .asProps();
           

ActorRef bma280 = system.actorOf(props);
bma280.tell(BMA280.Command.SELFTEST, self());

```
The Actor will respond to a Command object sent by the caller with a Response. The format of both Command and Response will typically be defined within the Protocol class.

Similarly, Akka Streams components can be built using the `asFlow(...)` method. The `Flow` component will accept the Commands messages defined in the protocol class, and emit a Response message in return:

```java
Flow<BMA280.Command, BMA280.Results, NotUsed> bma280 = 
     I2C.device(BMA280.class)
        .onBus(1)
		.at(BMA280Constants.DEFAULT_ADDRESS)
		.asFlow(system);

// Send a READ command every 500 millis...
Source<BMA280.Command, ?> timerSource = Source
        .tick(Duration.ZERO, Duration.ofSeconds(1), BMA280.Command.READ);

// ...then print out the measurement to the console
timerSource.via(bma280).to(logSink).run(mat);
```

### Implementing an I2C protocol

Interacting with an I2C device is done through a series of read and write operations. In RIoT, this is encapsulated in a Protocol class, which describes how this interaction happens at startup, shutdown, or in response to messages:

```java
public interface I2CProtocol<I, O> extends Protocol<I, O> {

	void init(I2CDevice dev) throws IOException;

	O exec(I2CDevice dev, I message) throws IOException;

	void shutdown(I2CDevice dev) throws IOException;

}
```

The Protocol class should specify the type of message it will accept (the generic type `I` above), and the type it will send as a response (`O`). 

Often, protocol classes will be able to execute more than just one operation. In this case, possible strategies are specifying a superclass as the type, or an enum:

```java
public class BMA280 implements I2CProtocol<BMA280.Command, BMA280.Results> {
   ...
   
   // Use an enum for the commands
	public static enum Command {
		READ, CALIBRATE, SELFTEST
	}

   // Use a superclass for the results
	public static class Results {
	}
	
	// Some commands will return this subclass
	public static class Measurement extends Results {
		//...
	}
	
	public void init(I2CDevice dev) throws IOException {
		//...
	}

	public Results exec(I2CDevice dev, Command command) throws IOException {
		switch (command) {
		case SELFTEST:
         //...
         return new Results();
		case CALIBRATE:
         //... 
         return new Results();
       case READ:
		default:
		  /...
		  return new Measurements(...);
	}

	@Override
	public void shutdown(I2CDevice dev) throws IOException {
		//...
	}
```

Parameters that are used in configuring the I2C device can be passed to the constructor of the Protocol class, and kept in member variables, so that they are available when the `init()` method is called. Instead of constructing Streams components and Actors usiong a class name, they are then constructed using an instance of the protocol class: 

```java
// Configure a BMA280 device 
BMA280 bma280config = new BMA280( 
		BMA280Constants.AccelerometerScale.AFS_2G, 
		BMA280Constants.Bandwidth.BW_500Hz, 
		BMA280Constants.PowerMode.normal_Mode, 
		BMA280Constants.SleepDuration.sleep100ms);

Flow<BMA280.Command, BMA280.Results, NotUsed> bma280 = 
     I2C.device(bma280config) //instead of 'BMA280.class'
        .onBus(1)
		.at(BMA280Constants.DEFAULT_ADDRESS)
		.asFlow(system);
```

### The Protocol Descriptor

In addition, each Protocol requires a ProtocolDescriptor object, returned by the `getDescriptor` method:

```java
public interface Protocol<I, O> {

	ProtocolDescriptor<I, O> getDescriptor();

}
```

This contains the class name of the input and output message, and the maximal time that can elapse between a command message is received, and a response is sent:

```java

@Override
public ProtocolDescriptor<Command, Results> getDescriptor() {

	return new ProtocolDescriptor<Command, Results>(
	        Command.class, 
	        Results.class, 
	        Timeout.apply(1, TimeUnit.SECONDS));
	
}
```

This class can be expanded in future relase to contain more metadata about the protocol.


[sbt]: https://www.scala-sbt.org/1.x/docs/Setup.html
[streams.g8]: https://github.com/riot-framework/streams.g8
[riot-core]: https://github.com/riot-framework/riot-core
[sbt-riot]: https://github.com/riot-framework/sbt-riotctl
[riot]: https://riot.community
[akka streams]: https://doc.akka.io/docs/akka/current/stream/stream-quickstart.html
[giter8]: http://www.foundweekends.org/giter8/
[systemctl]: https://www.digitalocean.com/community/tutorials/how-to-use-systemctl-to-manage-systemd-services-and-units
[pi4j]: https://pi4j.com
[pin numbers]: https://pi4j.com/1.2/pins

[led]: https://www.aliexpress.com/item/32700885768.html
