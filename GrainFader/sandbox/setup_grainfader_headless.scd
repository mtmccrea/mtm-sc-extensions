// running this will boot the server it it's not already booted

// an array for outbusses will send each synth to individual busses
g = GrainFader(0, 2, bufFolderPath: "/Users/admin/src/LOCAL_UW/ultrasound/Sound_Files/mono_textures/");
// g = GrainFader([0,1], [2,3], bufFolderPath: "/Users/admin/src/LOCAL_UW/ultrasound/Sound_Files/mono_textures/");

// once connected on a local network, create a TouchOSC instance
// even though you may not be using touchOSC...
t = TouchOSC("169.254.3.103", 9000) // listen only to a particular device
t = TouchOSC("localhost", 9000)     // listen only to this local IP (from another local app)
t = TouchOSC("169.254.3.103", 9000, lockOnDeviceIP: false) // accept osc messages from other addresses as well, e.g. an iPad + local app, 2 iPads, etc.

// connect the "TouchOSC" to the GrainFader
g.connectTouchOSC(t)

// note the message format:
// It's "faking" a touchOSC instance, and all of the control values
// are expected to be 0 -> 1, which maps into a ControlSpec
// If you have a look at the source code for how GrainFader is mapped to TouchOSC, you'll
// find many of the defaults:
// connectTouchOSC << highlight this and command+i
// for convenience here's the important part:

/*
aTouchOsc.addCtl( \gate++i,			\toggle,	'/1/gate'++i);
aTouchOsc.addCtl( \amp++i,			\fader,		'/1/amp'++i,		ControlSpec(-90, 8, -4, 0, -90));
aTouchOsc.addCtl( \grainRate++i,	\fader, 	'/1/grainRate'++i,	ControlSpec(0.2, 100.0, 5, 0, 10.0, "grn/sec"));
aTouchOsc.addCtl( \grainDur++i,		\fader, 	'/1/grainDur'++i,	ControlSpec(0.02, 3.0, 4, 0, 0.5, "sec"));
aTouchOsc.addCtl( \grainRand++i,	\fader, 	'/1/grainRand'++i,	ControlSpec(0.0, 1.0, \lin, 0, 0));
aTouchOsc.addCtl( \posDisp++i,		\fader, 	'/1/posDisp'++i,	ControlSpec(0.0, 0.9, \lin, 0, 0));
aTouchOsc.addCtl( \pitch++i,		\fader, 	'/1/pitch'++i,		ControlSpec(0.3, 8.0, 3, 0, 1));
aTouchOsc.addCtl( \posRate++i,		\fader,		'/1/posRate'++i,	ControlSpec(0.0001, 5.0, 3, 0.0001, 1.0));
aTouchOsc.addCtl( \start++i,		\fader,		'/1/start'++i,		ControlSpec(0.0, 1.0, \lin, 0, 0.0));
aTouchOsc.addCtl( \end++i,			\fader, 	'/1/end'++i,		ControlSpec(0.0, 1.0, \lin, 0, 1.0));
aTouchOsc.addCtl( \auxmix++i,		\fader,		'/1/auxmix'++i,		ControlSpec(0.0, 1.0, \lin, 0, 0), "aux");
aTouchOsc.addCtl( \posInv++i,		\toggle,	'/1/posInv'++i,     ControlSpec(0.0, 1.0, \lin, 0, 0) );
aTouchOsc.addCtl( \mon++i,			\toggle,	'/1/mon'++i );
aTouchOsc.addCtl( \recvUpdate++i,	\toggle,	'/1/recvUpdate'++i );
aTouchOsc.addCtl( \posReset++i,		\push,		'/1/posReset'++i );
*/

// to send messages locally, e.g. from another app or controller sending OSC messages from this machine,
// set up the local address to send control values to ("127.0.0.1")
n = NetAddr("localhost", 57120);

// now send an amp update:
// this sets the amp of the first (left) synth to [-90,8, -4].asSpec.map(0.5), which is about -3dB
n.sendMsg('/1/amp1', 0.5) // e.g. amp is mapped from -90 to 8 dB

/*
a couple things to note:
"/1" of '/1/amp1' means the first page of the TouchOSC, so that's just a convention you can include but otherwise ignore
"/amp1" means the amp of the first synth (which was on the left in the interface)
'/1/amp2' would change the amp of the second synth
this convention applies to all parameters.

Recall that there are 2 granulators running at once, 1 & 2, and the "xfade" parameter mixes between them:
an xfade of 0 is synth 1, xfade of 1 is synth 2, a balance of 0.5 is a mix of both.
*/


// you can change these default value ranges
// by setting paramters to use new control specs:
t.remap(\amp1, ControlSpec(-120, 24, -4, 0, -90)); // change range to -120 to +24 dB

n.sendMsg('/1/amp1', 0.5) // now this sets amp to about 7dB

// Here are the other parameters:
n.sendMsg('/1/gate1', 0) // synth gate closed (fades out to no sound)
n.sendMsg('/1/gate1', 1) // synth gate open (fades in)

n.sendMsg('/1/grainRate1', 0.5) // grain rate
t.remap(\grainRate1, ControlSpec(1, 5)) // update the spec to your desired range
n.sendMsg('/1/grainRate1', 0) // grain rate of 1
n.sendMsg('/1/grainRate1', 1) // grain rate of 5

n.sendMsg('/1/grainDur1', 0.5)  // grain duration
n.sendMsg('/1/grainRand1', 0.5) // randomness of grain pointer into soundfile
n.sendMsg('/1/posDisp1', 0.5)   // randomness ("dispersion") of location around the grain pointer as it moves
n.sendMsg('/1/pitch1', 0.5)     // pitch shift of grains
n.sendMsg('/1/posRate1', 0.5)   // rate of pointer
n.sendMsg('/1/start1', 0.1)     // starting point in the file of the grain pointer loop
n.sendMsg('/1/end1', 0.9)       // end point in the file of the grain pointer loop
n.sendMsg('/1/auxmix1', 0.5)    // mix of auxiliary send (this was for reverb or fx at the time)
n.sendMsg('/1/posInv1', 0)      // invert flag for the direction of the grain pointer (scan backwards)
n.sendMsg('/1/mon1', 1)         // monitor flag to send this stream out the monitor output
n.sendMsg('/1/recvUpdate1', 1)  // flag to accept a trigger for a soundfile buffer change
n.sendMsg('/1/posReset1', 0.5)  // reset the grain pointer to its start position


// Global controls (not specific to one synth or another, no appended sytnh number to message)

// balance/fade between the 2 granulators
n.sendMsg('/1/xfade', 0.5)  // equal mix of both
n.sendMsg('/1/xfade', 0.5)  // equal mix of both
// amp of the monitor send
n.sendMsg('/1/monAmp', 0.5)  // (default spec: ControlSpec(-90, 8, -4, 0, -90)))



// To change the buffer of the soundfile being granulated, it may be easier to just set up your own responder:
(
~bufDefs = 2.collect{|i|
    OSCdef(format("buf%",i+1).asSymbol, { |msg|
        var bufdex, buf, bufnum, which;
        bufdex = msg[1];
        bufdex.postln;
        buf = g.buffers[bufdex];
        bufnum = buf.bufnum;
        which = [g.synth1, g.synth2][i];
        which.buffer_(buf).bufnum_(bufnum);
    },
    format("/1/buf%",i+1).asSymbol, n
    )
}
)

// here is your list of buffers:
g.buffers.collect({|buf| PathName(buf.path).fileName.postln;})
// set the buffer being used by the index of the above list
n.sendMsg('/1/buf1', 0)
n.sendMsg('/1/buf1', 1)

n.sendMsg('/1/buf2', 1)


// Finally, preset saving and recalling should work as before...


/* CLEANUP */

// free the GrainFader
g.free;

// Disconnect all controls from your object/model.
// Controls are still stored with their mappings, they just no longer control your object/model.
t.disconnectAll

// or free for good - remove the reponders entirely
t.free

// free the OSC responders that change the buffers of the 2 granulator synths
~bufDefs.do(_.free)