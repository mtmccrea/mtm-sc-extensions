RecordControl {
	// copyArgs
	var <numChannels, <>headerFormat, <>sampleFormat, <>overwrite, <>appendKr, <>appendFs, server;

	var <busnum, <fileName, <directory, <>verbose=true;
	var <buffer, <recPath, <recording=false;
	var bufPrepared=false, dataRecorded=false, recCnt=0;
	var wrSynth, baseFileName, <gui;
	var <plotter, <plotting = false, prevPlotWinBounds, prevPlotBounds, overlayPlot=false;

	*new { |busOrIndex, numChannels=1, fileName, directory, headerFormat = "WAV", sampleFormat = "float", overwrite=false, appendKr=false, appendFs=true, server, makeGui=false|
		^super.newCopyArgs(
			numChannels, headerFormat, sampleFormat, overwrite, appendKr, appendFs, server
		).init(busOrIndex, fileName, directory, makeGui);
	}

	init { |argBus, argFileName, argDirectory, makeGui|
		// handle unspecified vars
		if (argBus.isNil) {
			var er;
			er = Error("Unspecified bus or bus index.");
			this.changed(\error, er.errorString);
			er.throw;

		};

		server = server ?? Server.default;

		this.fileName_(
			if (argFileName.notNil) {
				argFileName.split($.)[0]		// strip out extension if given
			} {
				Date.getDate.stamp
			}
		);

		if (argDirectory.isNil) {				// default to recordings directory
			var recdir;
			recdir = thisProcess.platform.recordingsDir;
			if (File.exists(recdir).not) { recdir.mkdir };
			directory = recdir;
		} {
			this.directory_(argDirectory);
		};

		this.busnum_(argBus);

		server.serverRunning.not.if {
			"Server isn't running, scheduling to prepare the buffer when booted.".warn
		};
		server.doWhenBooted({ this.prBuildSynthDef });

		CmdPeriod.doOnce {this.free};

		makeGui.if{ this.makeGui };
	}

	makeGui {
		if (gui.isNil) {
			gui = RecordControlView(this);
		} {
			gui.win.isClosed.if{
				gui = RecordControlView(this);
			};
			gui.win.front
		};
	}

	// incrementFileName: only relevant when hitting record multiple times
	//   -  true:  create another buffer, increment filename
	//   -  false: defer to overwrite setting: either overwrites file or errors out because file exists
	record { |incrementFileName=true|
		// already recording?
		recording.if{
			var er;
			er = Error("Already recording! this.stop first.");
			this.changed(\error, er.errorString);
			er.throw;
		};

		// prepare another buffer, increment fileName
		Routine.run({
			var cond = Condition();

			incrementFileName.if{
				if (recCnt > 0) {
					fileName = format("%_%", baseFileName, recCnt);
					this.changed(\filename, fileName);
				};
				recCnt = recCnt+1;
			};

			this.prPrepareBuffer(cond);
			cond.wait;
			this.prBeginRecording;
		})

	}

	stop {
		fork{
			var wasRecording;
			wasRecording = recording;

			wrSynth !? {wrSynth.free; wrSynth = nil};
			server.sync;

			buffer !? {
				this.prCleanupBuffer;
				recording = false;
			};

			wasRecording.if {
				this.changed(\recording, recording);
				verbose.if {"Recording finished".postln}
			};
		}
	}

	numChannels_ { |n|
		numChannels = n.asInt;
		this.prBuildSynthDef;
		this.prCheckRecState;
		this.changed(\numchannels, numChannels);
		this.prUpdatePlotter;
	}

	busnum_ { |busOrIndex|
		case
		{busOrIndex.isKindOf(Integer)} {
			busnum = busOrIndex
		}
		{busOrIndex.isKindOf(Bus)} {
			busnum = busOrIndex.index
		}
		{busOrIndex.respondsTo('bus')} { //CtkBus, without dependency
			busnum = busOrIndex.bus
		}
		{ var er;
			er = Error("Unrecognized bus type");
			this.changed(\error, er.errorString);
			er.throw;
		};
		this.prCheckRecState;
		this.changed(\busnum, busnum);
		this.prUpdatePlotter;
	}

	fileName_ { |string|
		fileName = string.split($.)[0]; // strip extension if provided
		baseFileName = fileName;
		recCnt = 0;  // reset file name increment counter
		this.prCheckRecState;
		this.changed(\filename, fileName);
	}

	directory_{ |path|
		directory = this.prCheckValidDir(path);
		this.prCheckRecState;
		this.changed(\dirname, directory);
	}

	// open Dialog to select recording directory
	selectDirectory {
		FileDialog(
			{ |path| this.directory_(path) },
			fileMode: 2,			// directory
			acceptMode: 0,		// "open"
			stripResult: true	// result isn't a list
		)
	}

	plot { |overlay|
		overlay !? {overlayPlot = overlay};

		plotter ?? {
			var win;
			plotter = ControlPlotter(busnum, numChannels, 50, 25, \linear, overlayPlot);

			win = plotter.mon.plotter.parent;

			prevPlotWinBounds !? { win.bounds_(prevPlotWinBounds) };
			prevPlotBounds !? {plotter.bounds_(*prevPlotBounds)};
			overlayPlot.if{ plotter.plotColors_(numChannels.collect{Color.rand(0.3, 0.7)}) };
			plotter.start;
			plotting = true;

			win.view.onMove_({|v| prevPlotWinBounds= v.findWindow.bounds });
			win.view.onResize_({|v| prevPlotWinBounds= v.findWindow.bounds });

			plotter.mon.plotter.parent.onClose_({ |me|
				plotter = nil;
				plotting = false;
			})
		}
	}

	overlayPlot_ { |bool|
		if (overlayPlot != bool) {
			overlayPlot = bool;
			this.prUpdatePlotter;
		};
	}

	setPlotterBounds { |...args|
		prevPlotBounds = args;
		plotter !? { plotter.bounds_(*args) }
	}

	openDirectory {
		directory.openOS
	}

	free {
		this.stop;
		gui !? {gui.free; gui=nil};
	}


	/* PRIVATE */

	prBeginRecording {
		bufPrepared.not.if {
			var er;
			er = Error("Recording buffer hasn't been prepared. Cannot record!");
			this.changed(\error, er.errorString);
			er.throw;
		};

		// record
		wrSynth = Synth(\controlWr_++numChannels++\ch,
			[\bufnum, buffer.bufnum, \inbus, busnum],
			server.defaultGroup, 'addToTail'
		);

		dataRecorded = true;
		recording = true;
		verbose.if {postf("Recording % channels to %\n", numChannels, recPath)};
		this.changed(\recording, recording);
	}

	prCleanupBuffer {
		buffer.close;
		buffer.free;
		bufPrepared = false;
		buffer = nil;
		dataRecorded.not.if{ "Deleting unwritten-to file".postln; File.delete(recPath) };
	}

	// check if buffer needs to be reset after variable update
	prCheckRecState {
		if (recording) {
			"Currently recording, update will take effect on next .record".warn;
		}
	}

	// when overlay, numChannels, or busnum changes
	prUpdatePlotter {
		plotter !? {
			fork({
				var cnt=0;
				plotter.free;
				while ( {(plotter != nil) and: (cnt < 20)},
					{0.05.wait; cnt = cnt+1;}
				);
				if (plotter != nil) {
					"Could not clear the old plotter in time".warn;
				} { this.plot };
			}, AppClock);
		}
	}

	prCheckValidDir { |path|
		if (PathName( path ).isFolder.not) {
			var er;
			Error(format("Invalid directory: %\n", path));
			this.changed(\error, er.errorString);
			er.throw;

		} { ^path };
	}

	prBuildSynthDef {
		SynthDef(\controlWr_++numChannels++\ch, {arg bufnum, inbus;
			DiskOut.kr(bufnum, In.kr(inbus, numChannels));
		}).send(server);
	}

	prPrepareBuffer { |cond|
		recPath = directory +/+ fileName
		++ case
		{ appendFs } { "_fs%.".format(server.sampleRate / server.options.blockSize) }
		{ appendKr } { "_kr." }
		{ appendFs } { "_fs." }
		{"."}
		++ headerFormat;

		if (File.exists(recPath) and: overwrite.not) {
			var er;
			er = Error("File already exists. Choose another fileName or set overwrite=true");
			this.changed(\error, er.errorString);
			er.throw;
		};

		buffer = Buffer.alloc(server, 65536*2 / server.options.blockSize, numChannels);

		// create an output file for this buffer, leave it open
		buffer.write(
			recPath, headerFormat, sampleFormat, 0, 0, true,
			{
				bufPrepared = true;
				cond !? {cond.test_(true).signal}
			}
		);
	}

	prResetBuffer {
		this.prCleanupBuffer;
		this.prPrepareBuffer;
	}
}

/* Usage
a = CtkControl.lfo(SinOsc, low: -2, high:3).play
b = CtkControl.lfo(LFTri, low: -22, high:13).play

r = RecordControl( a.bus, 2, "ctlTestTwo", "~/Desktop/test".standardizePath, appendKr:false)
r = RecordControl( a.bus, 2, "ctlTestSix", "~/Desktop/test".standardizePath, overwrite:true)
r = RecordControl( a.bus, 2, "pqqwe", "~/Desktop/test".standardizePath, overwrite:false)
r = RecordControl( a.bus, 2, directory: "~/Desktop/test".standardizePath, overwrite:false)
r.dump

r = RecordControl(3)
r.makeGui
"".resolveRelative

r.makeGui

r.plot

r.record
r.record(incrementFileName:false)
r.stop

r.openDirectory // find the files

r.numChannels_(2)
r.overlayPlot_(true)
r.busnum_(a.bus)
r.sampleFormat = "float"
r.headerFormat = "aiff"
r.appendKr = false
r.fileName_("newNameCtlTestSeven")
r.selectDirectory

// make a new directory and record to it
File.mkdir(r.directory +/+ "subtest")
r.directory_(r.directory +/+ "subtest")

r.free
a.free
b.free
*/