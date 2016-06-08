Connection.traceAll = true;
~object = ();

~func = {
	|...args|
	"Signal fired with: %".format(args).postln;
};

c = ~object.connectTo(~func);

~object.changed(\foo, 1);
c.disconnect();
~object.changed(\foo, 2);
c.connect();
~object.changed(\foo, 3);

~slider = Slider(bounds:40@400).front;
~object.connectTo(~slider.valueSlot); // argOrder argument determines whichargs are passed in update~object
~object.changed(\somethig, 0.5);
~object.changed(\somethig, 1);

~object.signal(\important).connectTo({ "important!".postln; });
~object.signal(\unimportant).connectTo({ "unimportant...".postln; });
~object.changed(\important, "foo");
~object.changed(\unimportant, "bar");

v = NumericControlValue(1);
v.connectTo(~func);
v.value = 0.5;
v.value = 0.0;
v.connectTo(~slider.valueSlot);
v.value = 0.2;
v.value = 0.5;

v.connectTo(UpdateChannel(\foo));
UpdateChannel(\foo).connectTo({ |...args| "channelled:%".format(args).postln; });
v.value = 0.3;

v.connectTo(UpdateBroadcaster(\bar));
UpdateBroadcaster(\bar).connectTo({ |...args| "rebroadcast:%".format(args).postln; });
v.value = 0.5;

// difference? an UpdateChannel is a thing that changes, a broadcaster just rebroadcasts the original.

// What from here? We need something similar to UpdateMethod and UpdateValue, except for synth arguments.
// Then you could do something like:
// 		v.connectTo(~synth.argUpdater(\freq));

e = (
	\a: { "a".postln },
	\b: { "b".postln },
	\c: { "c".postln },
);
~slot = MultiMethodSlot(e, {
	|key|
	key;
});

~object.connectTo(~slot);

~object.changed(\b, 1)

