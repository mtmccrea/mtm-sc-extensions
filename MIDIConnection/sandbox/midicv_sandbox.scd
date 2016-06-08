/*  TODO:
/ convert to MIDIdef
/ add enable/disable controls to MIDICV
/ don't create more than one connection if it's the same thing
*/
s.boot
// initialize midi
MIDIClient.init;
// MIDIClient.sources; // what's available?
// MIDIIn.connect(0, MIDIClient.sources.at(0)); // connect just a specific controller
MIDIIn.connectAll

(
d = CtkSynthDef(\noiTest, {|outbus=0, amp=0, pan=0, scaleAmp=0.5, midiLag=0.1|
	Out.ar(outbus,
		Pan2.ar(
			PinkNoise.ar(Lag.kr(amp*scaleAmp, midiLag)), // NOTE you'll want to lag most controls driven with MIDI
			pan )
	)
})
)

// create 2 notes
~noi1 = d.note.outbus_(0).pan_(-1).play;
~noi2 = d.note.outbus_(0).pan_(1).play;

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
MIDIdef.allFuncProxies['MIDI control']
// Clean up all MIDI in SC
MIDIIn.disconnectAll




MIDIFunc.trace(true)
MIDIFunc.trace(false)

\db.asSpec
ControlSpec
m = MIDICV(0, \db.asSpec.maxval_(10)).cc(1)


m.spec_(\db.asSpec.maxval_(10))
m.spec.maxval_(-16)


m.free
MIDIFunc.allFuncProxies['MIDI control']
MIDIFunc.allFuncProxies['MIDI control'][0].free
MIDIFunc.allFuncProxies['MIDI control'].do(_.free)

// connect to these objects
f = {|val| postf("A function getting: %\n", val)}; // a function to fire
o = ().put(\val1, 2); // any object with a method to call by the MIDICV
o

m.connectTo(f)
m.connectTo(o, \val1_)
m.connectTo(o)

m.responders
m.cc(2) // set the ccNum

m.responders[1].method

m.disconnectAll

m.input_(0.3)
o

m.disconnect(f) // disconnect the function responder
m.value_(-30)

m.disconnect(o) // disconnect the object 'o'
m.input_(0.999)
o // still the old value
m.connectTo(o, \test)
m.connectTo(o, \val1_)
m.input_(0.999)
o
m.disconnect(o, \val1_) // disconnect the object 'o'
m.input_(0.1)
o

m.disconnect(o, \test)
m.responders

m.value_(-30)

m.disconnect(f)
m.disconnectAll
m.disable


/* MIDI Setup */
MIDIClient.init;
MIDIClient.sources;
MIDIIn.connect(0, MIDIClient.sources.at(0));
MIDIIn.connectAll

MIDIFunc.trace(true)
MIDIFunc.trace(false)

// inspect the MIDI data, nil: match any incoming channel
a = MIDIFunc.cc({arg ...args; args.postln}, nil);
a.free; // cleanup
// inspect the MIDI data, match channel 81
a = MIDIFunc.cc({arg ...args; args.postln}, 81);
a.free; // cleanup

// see all MIDIFunc responders
MIDIFunc.allFuncProxies['MIDI control']
// clear all MIDIFunc responders
MIDIFunc.allFuncProxies['MIDI control'].do(_.free) // if MIDIFuncs

MIDIdef.allFuncProxies['MIDI control'].collect(_.key)[0].class

a = MIDIdef.cc(\myMIDIdef, {arg ...args; args.postln}, nil, 0);
a.free
// free all MIDIdefs
MIDIdef.freeAll;

// MIDIIn.disconnect
MIDIIn.disconnectAll


// test object
o = ()
o.param1 = 0
o.param2 = -1

~spec = \db.asSpec
c = MIDIdef.cc(\myDef, {|val| MIDIdef(\myDef).changed(\value, ~spec.map(val));}, 81)

~conn1 = c.addConnection( {
	|obj, changed, val|
	// [obj, changed, val].postln;
	"\tt 1".postln;
}
);
~conn2 = c.addConnection( {
	|obj, changed, val|
	// [obj, changed, val].postln;
	2.postln;
}
);
~conn1.disconnect
~conn2.disconnect

~conn1.connect
~conn2.connect



c = MIDICtl.new(81, \db.asSpec, {|val| postf("doing something with: %\n", val) }, \myFirstMIDICtl)
c.ccNum
c.disable
c.enable
c.spec
c.free


c.connectTo({|val| obj.myParam_(val) })
c.connectTo(obj, \myParam_)
~fader.connectTo(obj, \myParam_)

obj.addConnection()

~myObj = ().put(\param, 0)

~setValFund = { |val|
	var mapped, spec;
	spec = \db.asSpec;
	mapped = spec.map(val);
	~myObj.param_(mapped);
	this.changed(\value, val);
	this.changed(\mappedValue, mapped);
}

~slider = Slider(bounds:40@400).front;

~setValFund.(0.2)

~sconn = ~setValFund.connectTo(~slider.methodSlot(\value_,0)).filter(\value)

~slider.value

~conn1 = c.addConnection( {
	|obj, changed, val|
	[obj, changed, val].postln;
	o.param1 = val;
}
);
~conn1.disconnect
o

~colorValue.connectTo(~view.methodSlot(\background_)).filter(\value);
~conn2 = c.addConnection(o.methodSlot(\param2_)).filter(\value);
~conn2.disconnect
o


~slider = Slider(bounds:40@400).front;

~conn = c.addConnection( {
	|obj, changed, val|
	[obj, changed, val].postln;
	{~slider.value_(obj.spec.unmap(val))}.defer
}
);

~conn.disconnect
~conn.connect




// set the MIDICtl to the next control that is touched
c = MIDICtl.new(\touch, [4,18.2,\exp], {|val| postf("doing something with: %\n", val) }, \myFirstMIDICtl)
c.free

c = MIDICtl.new(\touch, [4,18.2,\exp], {|val| postf("doing something with: %\n", val) }, \myFirstMIDICtl)


// MIDICtl2
MIDIClient.init
MIDIIn.connectAll
MIDIIn.disconnectAll

c = MIDICtl2.new(81, \db.asSpec, \myFirstMIDICtl)
c.ccNum
c.disable
c.enable
c.spec
c.free

// test object
o = ()
o.param1 = 0
o.param2 = -1


~slider = Slider(bounds:40@400).front;
~conn1 = c.connectTo(o.methodSlot(\param1_,0)).filter(\value)
~conn2 = c.connectTo(~slider.methodSlot(\value,0)).defer(\unmappedValue)
o.param1

~conn1.disconnect
~conn2.disconnect


{
	|obj, changed, val|
	[obj, changed, val].postln;
	o.param1 = val;
}
);
~conn1.disconnect
o

~colorValue.connectTo(~view.methodSlot(\background_)).filter(\value);
~conn2 = c.addConnection(o.methodSlot(\param2_)).filter(\value);
~conn2.disconnect
o


MIDIClient.init;
MIDIClient.sources;
MIDIIn.connect(0, MIDIClient.sources.at(0));
MIDIIn.connectAll

MIDIFunc.trace(true)
MIDIFunc.trace(false)

\db.asSpec.max
ControlSpec
/*  TODO:
set spec
set ccNum

*/
m = MIDICV(0, \db.asSpec.maxval_(10)).cc(1)


m.spec_(\db.asSpec.maxval_(10))
m.spec.maxval_(-16)


m.free
MIDIFunc.allFuncProxies['MIDI control']
MIDIFunc.allFuncProxies['MIDI control'][0].free

// connect to these objects
f = {|val| postf("A function getting: %\n", val)}; // a function to fire
o = ().put(\val1, 2); // any object with a method to call by the MIDICV
o

m.connectTo(f)
m.connectTo(o, \val1_)
m.connectTo(o)

m.responders
m.cc(2) // set the ccNum

m.responders[1].method

m.disconnectAll

m.input_(0.3)
o

m.disconnect(f) // disconnect the function responder
m.value_(-30)

m.disconnect(o) // disconnect the object 'o'
m.input_(0.999)
o // still the old value
m.connectTo(o, \test)
m.connectTo(o, \val1_)
m.input_(0.999)
o
m.disconnect(o, \val1_) // disconnect the object 'o'
m.input_(0.1)
o

m.disconnect(o, \test)
m.responders

m.value_(-30)

m.disconnect(f)
m.disconnectAll
m.disable


// Trying MIDIControlValue

m = MIDIControlValue(0, \db.asSpec).cc(81)

o = ()
o.param1 = 0
o.param2 = -1
m.connectTo(o.methodSlot(\param1_, 0))

m.input_(0.3)
m.value_(-30)

m.removeDependant( o)
o
m.dependants

f = {|val| postf("got the value: %\n", val)}

~funcConn = m.connectTo(f.methodSlot(\value, 0))

m.removeDependant(f)

Connection(m, f, false)

m.input_(0.3)
m.value_(-30)
o

~funcConn.disconnect