s.boot
// initialize midi
MIDIClient.init;
// MIDIClient.sources; // what's available?
// MIDIIn.connect(0, MIDIClient.sources.at(0)); // connect just a specific controller
MIDIIn.connectAll

( // a synth to test with
d = CtkSynthDef(\noiTest, {|outbus=0, amp=0, pan=0, scaleAmp=0.5, midiLag=0.1|
	Out.ar(outbus,
		Pan2.ar(
			PinkNoise.ar(Lag.kr(amp*scaleAmp, midiLag)), // NOTE you'll want to lag most controls driven with MIDI
			pan )
	)
})
)

// create 2 notes
~noi1 = d.note.outbus_(2).pan_(-1).play;
~noi2 = d.note.outbus_(2).pan_(1).play;

// create a MIDI Control Value for each note's amp
~midiVol1 = MIDICV(0, \unipolar.asSpec).cc(1);
~midiVol2 = MIDICV().cc(2);		// defaults to initVal 0, spec: unipolar

// now connect the control to the note's amp method
~midiVol1.connectTo(~noi1, \amp_);
~midiVol2.connectTo(~noi2, \amp_);

// multiple functions/objects can listen to a MIDICV
f = {|val| val.ampdb.postln};	// post the amp values in dB
~midiVol1.connectTo(f);			// remember to connect it!
~midiVol2.connectTo(f);			// remember to connect it!

// this fader range doesn't match well, so try updating the spec:
// update the spec as needed
// updateVal: true (default) – value will update automatically with the new spec
~midiVol1.spec_(\amp.asSpec.maxval_(10.dbamp), updateVal: true);
~midiVol1.spec_([0,10.dbamp,4.7].asSpec);	 // an ok mapping on the x-touch from ~ -15>10dB
// set both volume controls to this spec
~midiVol2.spec_([0,10.dbamp,4.7].asSpec);	 // an ok mapping on the x-touch from ~ -15>10dB


// individual listeners can be disconnected and reconnected
~midiVol1.disconnect(f)
// specifying only the controlled object removes all controls assigned between that fader and the note
~midiVol1.disconnect(~noi1)
// if for some reason you have more connections between a control and an object, you can just disconnect the control from a single object's method
~midiVol2.disconnect(~noi2, \amp_)

// reconnect
~midiVol1.connectTo(~noi1, \amp_)
~midiVol1.connectTo(f)
~midiVol2.connectTo(~noi2, \amp_)

// you can also remap MIDICVs to a different fader than originally assigned
// e.g. move the second note's volume control to the master fader (ccNum 9)
~midiVol2.newCC_(9) // move the master fader...and fader 2

// enable/disable, but retain connections
~midiVol1.disable
~midiVol1.enable

// introspect...
~midiVol1.postConnections // shows connected function or [object, method]
~midiVol1.connectedFunctions
~midiVol1.connectedMethods


/* TOGGLE BUTTON FUNCTIONALITY */

// Create a "button" - this uses noteOn/Off under the hood,
// which is different from midi \control messages (like faders, knobs, etc)
// First, make the MIDICV that you want to use
// a \unipolar spec will give you an output of 0 and 1 for button values
~midiBut = MIDICV(0, \unipolar.asSpec).button(16);

// it's going to broadcast both the down (1) and up (0) signals, it's not yet a "toggle"
g = {|val| postf("My control value: %\n", val)};
~midiBut.connectTo(g);			// remember to connect it!
// ...this is still useful for other types of instrument controls and triggers

// But for toggling...
// need to explicitly make a toggle signal
~midiBut.makeToggle;
// this creates a NumericCV internally to keep track of it's "toggle" state:
~midiBut.toggleCV
// so that CV can be connected to functions or object's methods just like
// a MIDICV (which is actually a subclass of NumericCV)
e = {|val| postf("Toggled! %\n", val)};
~midiBut.toggleCV.connectTo(e) // connect the _toggleCV_ not the ~midiBut!

// This toggle state can be mirrored out to a MIDI hardware port.
// It sends to the same channel as the MIDICV that this toggle corresponds to.
// Turn on the hardware mirroring
//   destPort: port index of controller in MIDIClient.destinations
~midiBut.mirrorHWToggle(destPort: 0)

// NOTE: Right now, toggleState update on button push,
//    and "val" (the MIDICV control value) is still the
//    spec-mapped value of the button (not the toggle state).
//    So if you need the toggle state, ask the MIDICV directly:  ~midiTog.toggleState
// TODO: create a toggle mode that notifies only of the toggle state
//    and fires "on" on push only, and "off" on release only

// turn it off later if you want
~midiBut.stopMirrorHWToggle; // toggle state no longer echoed to controller

~midiBut.free // this will clean up the internal toggle CV as well



// more introspection, global MIDICVs
MIDICV.postConnections
MIDICV.midiCvDict

/* CLEAN UP */
// free the notes
[~noi1, ~noi2].do(_.free)
// free the midi controls
[~midiVol1, ~midiVol2].do(_.free)


// Now... for using mutiple MIDI assignments: 8 notes, 8 faders (amp), 8 encoders (pan)

// make 8 noise generators
~noi = 8.collect{|i| d.note.outbus_(0).pan_(i*2/7+ -1).scaleAmp_((-1 * 8).dbamp).play};
// volume controls: ccNum 1-8
~vCtl = 8.collect{|i| MIDICV(0, [0,10.dbamp,4.7].asSpec).cc(i+1)}
// connect midi control values to amp param
~vCtl.do{|midi, i| midi.connectTo(~noi[i], \amp_)}
// pan controls
~pCtl = 8.collect{|i| MIDICV(0, \bipolar.asSpec).cc(i+10)}
// connect midi control values to amp param
~pCtl.do{|midi, i| midi.connectTo(~noi[i], \pan_)}


[~noi, ~pCtl, ~vCtl].flat.do(_.free)

// Clean up all MIDICVs globally
MIDICV.clearAll;




// active MIDIdefs (Not just MIDICVs) can be accessed with:
MIDIdef.allFuncProxies
MIDIdef.allFuncProxies['MIDI noteOn']
// Clean up all MIDI in SC
MIDIIn.disconnectAll
// handy debugging... show all midi activity
MIDIFunc.trace(true)
MIDIFunc.trace(false)