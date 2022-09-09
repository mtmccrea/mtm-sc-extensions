/* FIRST PART - READING YOUR DATA IN, WRITING IT TO A BUS */

(  // run the whole block >>>

s.waitForBoot({

	~numChannels = 4;  // number of channels of data to record

	~oscTags = [		// put the tags that matches the message
		'/chan1Tag',		// your plant device/python bridge sends
		'/chan2Tag',
		'/chan3Tag',
		'/chan4Tag'
	];

	~port = 57120;				// the port your python bridge sends TO; SC's default port is 57120

	// this is the multichannel bus you'll be recording your data to
	~dataBus = Bus.control(s, ~numChannels);

	(
		// these OSCdefs will receive the data from python
		// then write the values to the multichannel Bus
		~oscDefs = ~oscTags.collect{ |tag, i|
			OSCdef(  tag.asString.drop(1).asSymbol, {
				|msg, time, addr, recvPort|
				// msg.postln;						// uncomment to confirm the index of data in msg array
				~dataBus.setAt(i, msg[3]);
			}, tag, recvPort: ~port
			);
		};
	)
})

// NOTE: your data message rate is your effective "sample rate"
// so if you need to increase it, look in your python code to change the rate

)


/* SECOND PART - RECORDING */

//---------------------------//---------------------------
// create the Recorder
//---------------------------//---------------------------

~recorder = RecordControl( ~dataBus, ~numChannels, makeGui: true);

// The first things you'll want to do are:
// - select a recording directory
// - type a filename (remember to push ENTER so the name is submitted)
// - click "Plot signal" and observe your input signal
// - - > you can have the plotter automatically set the bounds on your signal
// - - - or you can enter bounds manually to keep the plots from continually rescaling
// - Click Open directory, which will open the directory you're recording to
// - - > that way you'll be able to see the files you're recording

// alternatively... you can specify your
// filename and/or directory to save to when instantiating
// useful so you don't need to select the folder to record to
// with the dialog every time
~recorder = RecordControl( ~dataBus, ~numChannels, "ctlTest", "~/Desktop".standardizePath )



//---------------------------//---------------------------
/* Some things to note */
//---------------------------//---------------------------

// you don't need to change the filename every time you push record (although you can)
// if you successively hit record, the filenames will automatically increment

// make note of your sample rate, when doing many recordings over many sessions, it's probably
// a good idea to keep the sample rate consistent

// if you don't enter a filename, it defaults to a Date stamp
// if you don't ender a directory name if defaults to:
thisProcess.platform.recordingsDir;

// if you accidentally close the window, the recorder is still instantiated, just run
~recorder.makeGui



//---------------------------//---------------------------
/* Some optional stuff that could be useful */
//---------------------------//---------------------------

// if you'll be recording in "sessions" you can make subfolders to record to

// make a sub directory in the current record directory
~folderName = "mySession1";
File.mkdir(~recorder.directory +/+ ~folderName)
// set the recorder to record to this new directory
~recorder.directory_(~recorder.directory +/+ ~folderName)
// >> note the GUI updates to reflect the new directory, you can press "Open directory" to view it

// cleanup
~recorder.free;
~oscDefs.do(_.free)



// Test ControlPlotter with a signal
~dataBus = Bus.control(s,numChannels: 4);
c = CtkControl.lfo(SinOsc, 2, bus: ~dataBus.index).play
c.freq = 0.5
c.freq = 50
c.free
d = CtkControl.lfo(LFSaw, 2, bus: ~dataBus.index+1).play
d.freq = 0.5
d.free
p = ControlPlotter(~dataBus.index, 4, plotLength: 75, refresh_rate: 60, overlay: false)
p.start
p.plotMode = \lines
p.bounds = \auto
p.bounds_(-2,2)
p.free
p.mon.plotter.specs
~dataBus.plot(0.2)



/*
---  SCRATCH - ignore this stuff ---

// fake the signal
r = fork{ inf.do{ ~dataBus.setn([rrand(0.1, 0.8), rrand(0.1, 0.8), rrand(0.1, 0.8), rrand(0.1, 0.8)]); rrand(0.1, 0.8).wait; } }
r.stop

~dataBus.setAt(0, 0.7); // test setting the values
~dataBus.setAt(1, 0.7); // test setting the values
~dataBus.setAt(2, 0.2); // test setting the values
~dataBus.setn([0.2, 0.4, 0.6, 0.8]); // test setting the values


r.makeGui
r.plot
r.record
r.record(incrementFileName:false)
r.stop

r.openDirectory // find the files

// setters
r.numChannels_(2)
~recorder.overlayPlot_(true)
~recorder.overlayPlot_(false)
r.busnum_(a.bus)
r.sampleFormat = "int24"
r.headerFormat = "aiff"
r.appendKr = false
r.fileName_("newNameCtlTestSeven")
r.selectDirectory
r.free

c = ControlPlotter(~dataBus.index, ~numChannels, 50, plotMode: \linear, overlay: false).start
c.bounds_(0,1)

*/