s.boot
// initialize midi
MIDIClient.init;
// MIDIClient.sources; // what's available?
// MIDIIn.connect(0, MIDIClient.sources.at(0)); // connect just a specific controller
MIDIIn.connectAll


// -----------------------------------------------------------------------------
/* SynthDefs */

( // one input per synth
~defs = CtkProtoNotes(
	SynthDef(\inputRouter, { arg outbus=0, inbus, amp=0, thru=0, lag=0.05;
		var in;
		in = In.ar(inbus, 1);
		Out.ar(outbus, in * Lag.kr(amp, lag) * thru)
	}),

	SynthDef(\comp, {
		arg outbus=0, inbus,
		thresh = 0.5, slopeBelow = 1.0, slopeAbove = 1.0,
		clampTime = 0.01, relaxTime = 0.1, amp = 1;

		var in;
		in = In.ar(inbus, 1);
		Out.ar(outbus,
			Compander.ar(in, in, thresh, slopeBelow, slopeAbove, clampTime, relaxTime, amp);
		)
	})
)
)

// DUMMY INPUT FOR TESTTING
d = SynthDef(\burst, { arg outbus=0;
	Out.ar(outbus,
		Decay.ar(Impulse.ar((1..5)), 0.1, PinkNoise.ar)
	)
}).send
e = SynthDef(\inToOut, { arg inbus, outbus=0;
	Out.ar(outbus,	In.ar(inbus, 1) )
}).send


// -----------------------------------------------------------------------------
/* Input Nodes/Busses */

// create a bus for the outputs
~instrumentBus = CtkAudio.play(1);
// a group for your inputs and compression
// NOTE: put your processing instrument \after this group
~inGrp = CtkGroup.play;

// ~inChannels = [12,13,14,15,16];
~inChannels = s.options.numOutputBusChannels + [0,1,2,3,4]; // the first 5 hw inputs

// compressor
~compr = ~defs[\comp].note(addAction: \tail, target: ~inGrp).inbus_(~instrumentBus).outbus_(~instrumentBus).play;
(
// inputs
~inputs = ~inChannels.collect{|inChan|
	~defs[\inputRouter].note(addAction: \head, target: ~inGrp)
	.inbus_(inChan)
	.outbus_(~instrumentBus)
	.amp_(0)
	.thru_(1)
	.play
}
)
// check the correct node order
s.queryAllNodes

// THE DUMMY SIGNAL for testing
x = Synth(\burst, [\outbus, s.options.numOutputBusChannels])
// A dummy synth router to pull from the instrument bus that the compressor writes to
y = Synth(\inToOut, [\outbus, 0, \inbus, ~instrumentBus.bus], 1, 'addToTail')

// x.free; y.free;

// -----------------------------------------------------------------------------
/* MIDI Controls */

// faders for input levels
~faderOffset = 1; // first fader cc number
// this spec is fairly accurate with the fader, between about -15dB and +10dB
~inFaders = 5.collect{|i|  MIDICV(0, [0,10.dbamp,4.7].asSpec).cc(~faderOffset+i) };
// buttons for mute
~muteOffset = 40; // first mute note number
~inMutes = 5.collect{|i|  MIDICV(0, \unipolar.asSpec).button(~muteOffset+i); };
~inMutes.do(_.makeToggle); // turn the mutes into toggles
~inMutes.do(_.mirrorHWToggle(destPort: 0)); // mirror the state to hardware

// connect faders to amplitude
~inFaders.do{|fdr,i| fdr.connectTo(~inputs[i], \amp_)};

// connect mute _toggleCVs_ to mute param
// REMEMBER: on means signal comes through, which is why the parameter is called 'thru'
~inMutes.do{|but,i| but.toggleCV.connectTo(~inputs[i], \thru_) };



// -----------------------------------------------------------------------------
/* Useful Settings */

// input
~inputs[2].inbus_(s.options.numOutputBusChannels+7) // change the third input bus
~inputs.do(_.lag_(0.1)); // change ALL fader amp control lag

// Compressor
~compr.thresh_(-12.dbamp)
~compr.slopeAbove_(1.3)
~compr.clampTime_(0.005)
~compr.relaxTime_(0.1)

// change the spec range for the faders
~inFaders.do(_.spec_([0,10.dbamp,4.7].asSpec)) // try a different curve to your amp mapping
// check the fader range by printing it
f = {|val| val.ampdb.postln; }
~inFaders.do(_.connectTo(f))
// stop printing
~inFaders.do(_.disconnect(f))

// disconnect an individual mute control - this removes toggle functionality as well!
~inMutes[0].disconnectAll
// reconnect it
~inMutes[0].makeToggle.mirrorHWToggle // restore toggle mode
~inMutes[0].toggleCV.connectTo(~inputs[0], \thru_)


// -----------------------------------------------------------------------------
/* CLEANUP */
// free MIDI controls
~inMutes.do(_.free);
~inFaders.do(_.free);
// free just the input routers
~inputs.do(_.free);
// free just the compressor
~compr.free
// or free nodes at once by the group
~inGrp.freeAll;
// free instrument buss
~instrumentBus.free;


// -----------------------------------------------------------------------------
/* Debugging */

// introspect...
~inFaders[2].postConnections // shows connected function or [object, method]

MIDICV.midiCvDict.do(_.postConnections)
// turn it off hardware toggle mirroring
// (toggle state no longer echoed to controller)
~midiBut.stopMirrorHWToggle;


// active MIDIdefs (Not just MIDICVs) can be accessed with:
MIDIdef.allFuncProxies // all midi AND OSC responders
MIDIdef.allFuncProxies['MIDI noteOn'] // just midi note ons (buttons)
MIDIdef.allFuncProxies['control'] // just midi controls (faders)

MIDICV.clearAll; // nuke all the MIDICV objects
MIDIIn.disconnectAll // nuke all MIDI responders

// handy debugging... show all midi activity
MIDIFunc.trace(true)
MIDIFunc.trace(false)