~lincv = NumericControlValue(0.5)
~dbcv = NumericControlValue(0, \db.asSpec)
// initdbcv
~lincv.signal(\value).connectTo(~dbcv.methodSlot("input_(object.value)"))

~lincv.value_(0.6)
~dbcv.value



SynthDef(\cvTest, {|dbval|
	dbval.poll(2)
}).send(s)

x = Synth(\cvTest, [\dbval, 14])

x.dbval_(35)
x.free

x.set(0.0, \dbval, 22)
x.dbval

// ~dbcv.signal(\input).connectTo(x.methodSlot("dbval_(object.value)"))
// ~dbConn = ~dbcv.signal(\input).connectTo(x.argSlot(\dbval))
~dbConn = ~dbcv.signal(\value).connectTo(x.argSlot(\dbval))

~dbcv.input_(0.1)
~dbcv.input_(0.8)
~dbcv.input_(0.7)

~lincv.input_(0.222)
~lincv.input_(0.45)
~lincv.input_(0.95)


~sl = Slider();

~slConn = ~sl.signal(\value).connectTo(~lincv.methodSlot("value_(object.value)"))

~uiConn = ~lincv.signal(\value).connectTo(~sl.methodSlot("value_(object.value)"))
~uiConn = ~lincv.signal(\value).connectTo(~sl.valueSlot)



View().front.layout_(HLayout(~sl))

~dbConn.disconnect
~dbConn.connect

~dbConn.free

~uiConn.free

~s1 = Slider();
~b1 = NumberBox();

~n1.value

~n1.input_(0.3)
~n1.value

~n1.value_(0.9)
~n1.value

~n1.input_(0.9)
~n1.value

~n1.spec



d = CtkSynthDef(\cvTest, {|dbval|
	dbval.poll(2)
})
x = d.note.play
x.set(0.0, \dbval, 3)

~cv = NumericControlValue(0, \db.asSpec)
~cv = NumericControlValue(0, \unipolar.asSpec)

// ~conn = ~cv.signal(\value).connectTo(x.methodSlot(\set, 0.0, "'dbval'", \value))
~conn = ~cv.signal(\value).connectTo(x.methodArgSlot(\set, [0.0, 'dbval', "value"]))
~conn = ~cv.signal(\value).connectTo(x.methodArgSlot(\set, [0.0, 'dbval', \value]))
~conn.free


~conn2 = ~cv.signal(\value).connectTo({|...args| args.postln;})

~cv.input_(0.5)
~cv.value_(-33)

~conn.disconnect
~conn2.disconnect

~conn2.free

// initialize midi
MIDIClient.init;
// MIDIClient.sources; // what's available?
// MIDIIn.connect(0, MIDIClient.sources.at(0)); // connect just a specific controller
MIDIIn.connectAll


~mcv.value
~mcv.input
~mcv.free

~mcv.enableOutput
~mcv.ccOut_(0.25)
~mcv.ccOut_(0.75)

~conns = ConnectionList.makeWith({
	~mcv.connectTo(~cv.methodSlot(\input_, 0));
	~cv.connectTo(~mdv.methodSlot(\ccOut_, 0));
})


~conn1 = ~cv.connectTo(~mcv.methodSlot(\ccOut_, 0));
~conn1.collapse(1)

~conn1.disconnect
~conn1.connect
~conn1.free

~conn1.trace(true);

~cv.value
~cv.input
~cv.input_(0.5)
~cv.value_(0.5)

// the "hub", the 0>1 cv through which all signals are routed
~cv = NumericControlValue(0, \unipolar.asSpec)

// make the slider
~sl = Slider();
View().front.layout_(HLayout(~sl))
// make the MIDI fader
MIDIClient.init;
MIDIIn.connectAll;
~mcv = MIDIControlValue()
~mcv.cc_(1)



~dbcv = NumericControlValue(0, [0,10.dbamp,4.7].asSpec)
~postdB = ~dbcv.signal(\value).connectTo({|...args| args[2].ampdb.postln})
~postdB.free

~cvToDbcv = ~cv.signal(\value).connectTo(~dbcv.methodSlot(\input_, 0))
~cvToDbcv.free
// hub to post function
~postConn = ~cv.signal(\value).connectTo({|...args| args[2].postln})
~cv.value_(0.5) // see post

// slider to hub
~slIn = ~sl.signal(\value).connectTo(~cv.valueSlot);
// (drag slider, see post)

// hub to slider
~slOut = ~cv.signal(\value).connectTo(~sl.valueSlot);
~cv.value_(0.5) // test that slider moves

// midi fader to hub (input)
~midiIn = ~mcv.connectTo(~cv.methodSlot(\input_, 0));
// move fader, see post and fader move

// hub to midi fader
~mcv.enableOutput(destPort:0); // enable sending MIDI
~midiOut = ~cv.connectTo(~mcv.methodSlot(\ccOut_, 0));
~midiOut.collapse(2); // limit update rate of MIDI fader
~cv.value_(0.5) // see midi fader move, sw slider move, post
// drag sw slider, see midi fader move in 0.2sec steps


~midiOut.disconnect
~midiOut.connect
~midiOut.free
~postConn.disconnect

~mOut = MIDIOut(0)
~mOut.latency = 0.05
(49..54).do{|i| ~mOut.noteOn(0, i, 1)}
(49..54).do{|i| ~mOut.noteOn(0, i, 0)}
// lower buttons
(40..47).do{|i| ~mOut.noteOn(0, i, 1)}
(40..47).do{|i| ~mOut.noteOn(0, i, 0)}
// top buttons
(16..23).do{|i| ~mOut.noteOn(0, i, 1)}
(16..23).do{|i| ~mOut.noteOn(0, i, 0)}



// Fader Banks
~cvs = 8.collect{NumericControlValue(0, [-inf.dbamp,10.dbamp,4.7].asSpec)} // amp output, roughly aligns with faders

~sliders = 8.collect{Slider()};
~numBoxes = 8.collect{NumberBox()};
View(bounds:Size(300,400).asRect).front.layout_(GridLayout.rows(~sliders, ~numBoxes));

~midis = 8.collect{MIDIControlValue(0)};
(1..8).do{|ccNum,i| ~midis[i].cc_(ccNum).enableOutput};

// Connections

(
(
~uiSends = ConnectionList.makeWith({
	~sliders.connectEach(~cvs, \value, "input_(value)");
	~numBoxes.connectEach(~cvs, \value, "value_(value.dbamp)");
});
)
(
~uiRcvs = ConnectionList.makeWith({
	~cvs.connectEach(~sliders, \value, "value_(object.input)");
	~cvs.connectEach(~numBoxes, \value, "value_(value.ampdb)");
});
)
(
~midiSends = ConnectionList.makeWith({
	~midis.connectEach(~cvs, \value, "input_(value)");
});
)
(
~midiRcvs = ConnectionList.makeWith({
	~cvs.connectEach(~midis, \value, "ccOut_(object.input)");
});
~midiRcvs.do(_.collapse(0.2))
)
(
~cvPosts = ConnectionList.makeWith({
	~cvs.connectEach(
		8.collect{
			{|in, val| postf("in: %, out: %, outdB: %\n", in.round(0.01), val.round(0.01), val.ampdb.round(0.01))}
		}, \value, "value(object.input, value)");
});
)

// change cv mapping
~cvs.do(_.spec_([-inf.dbamp,10.dbamp,4.7].asSpec)) // orig
// need to also set it's value to it's previous value so dependents update
~cvs.do({|cv| cv.spec_([-60.dbamp,10.dbamp,4.7].asSpec); cv.value_(cv.value) })

~uiSends.disconnect
~uiRcvs.disconnect
~midiSends.disconnect
~midiRcvs.disconnect
~cvPosts.disconnect

~uiSends.connect
~uiRcvs.connect
~midiSends.connect
~midiRcvs.connect
~cvPosts.connect

~uiSends.free
~uiRcvs.free
~midiSends.free
~midiRcvs.free
~cvPosts.free

[~uiSends, ~uiRcvs, ~midiSends, ~midiRcvs, ~cvPosts].do(_.free)


~cvPosts.collect(_.connected)

// Feature requests:
MIDIOut capabilities, tied to MIDIControlValue (?)
ability to update the collapse/defer deltas (seems they can get larger, but not shrink)(how are multiple collapses not create?)
when spec of a cv is changed (.spec_()), set the value to it's current state (thus updating the input and signaling a change)