RecordControl {
	// copyArgs
	var <numChannels, <>headerFormat, <>sampleFormat, <>overwrite, <>appendKr, server;

	var <busnum, <fileName, <directory, <>verbose=true;
	var <buffer, <recPath, <recording=false;
	var bufPrepared=false, dataRecorded=false, recCnt=0;
	var wrSynth, baseFileName;

	*new { |busOrIndex, numChannels=1, fileName, directory, headerFormat = "WAV", sampleFormat = "int32", overwrite=false, appendKr=true, server|
		^super.newCopyArgs(
			numChannels, headerFormat, sampleFormat, overwrite, appendKr, server
		).init(busOrIndex, fileName, directory);
	}

	init { |argBus, argFileName, argDirectory|
		// handle unspecified vars
		if (argBus.isNil) { Error("Unspecified bus or bus index.").throw };

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
	}

	// incrementFileName: only relevant when hitting record multiple times
	//   -  true:  create another buffer, increment filename
	//   -  false: defer to overwrite setting: either overwrites file or errors out because file exists
	record { |incrementFileName=true|
		// already recording?
		recording.if{Error("Already recording! this.stop first.").throw};

		// prepare another buffer, increment fileName
		Routine.run({
			var cond = Condition();

			incrementFileName.if{
				if (recCnt > 0) {
					fileName = format("%_%", baseFileName, recCnt);
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
				this.changed(\recording, recording);
			};
			verbose.if {wasRecording.if {"Recording finished".postln} };
		}
	}

	numChannels_ { |n|
		numChannels = n;
		this.prBuildSynthDef;
		this.prCheckRecState;
		this.changed(\numChannels, numChannels);
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
		{ Error("Unrecognized bus type").throw };
		this.prCheckRecState;
		this.changed(\busnum, busnum);
	}

	fileName_ { |string|
		fileName = string.split($.)[0]; // strip extension if provided
		baseFileName = fileName;
		recCnt = 0;  // reset file name increment counter
		this.prCheckRecState;
	}

	directory_{ |path|
		directory = this.prCheckValidDir(path);
		this.prCheckRecState;
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

	openDirectory {
		directory.openOS
	}

	free {
		this.stop;
	}


	/* PRIVATE */

	prBeginRecording {
		bufPrepared.not.if {Error("Recording buffer hasn't been prepared. Cannot record!").throw};

		// record
		wrSynth = Synth(\controlWr_++numChannels++\ch,
			[\bufnum, buffer.bufnum, \inbus, busnum],
			server.defaultGroup, 'addToTail'
		);

		dataRecorded = true;
		recording = true;
		this.changed(\recording, recording);
		verbose.if {postf("Recording % channels to %\n", numChannels, recPath)};
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

	prCheckValidDir { |path|
		if (PathName( path ).isFolder.not) {
			Error(format("Invalid directory: %\n", path)).throw
		} { ^path };
	}

	prBuildSynthDef {
		SynthDef(\controlWr_++numChannels++\ch, {arg bufnum, inbus;
			DiskOut.kr(bufnum, In.kr(inbus, numChannels));
		}).send(server);
	}

	prPrepareBuffer { |cond|
		recPath = directory +/+ fileName ++ appendKr.if({"_kr."},{"."}) ++ headerFormat;

		if (File.exists(recPath) and: overwrite.not) {
			Error("File already exists. Choose another fileName or set overwrite=true").throw
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
a = CtkControl.lfo(SinOsc).play
b = CtkControl.lfo(LFTri).play

r = RecordControl( a.bus, 2, "ctlTestTwo", "~/Desktop/test".standardizePath, appendKr:false)
r = RecordControl( a.bus, 2, "ctlTestSix", "~/Desktop/test".standardizePath, overwrite:true)
r = RecordControl( a.bus, 2, "ctlTestSix", "~/Desktop/test".standardizePath, overwrite:false)
r = RecordControl( a.bus, 2, directory: "~/Desktop/test".standardizePath, overwrite:false)
r.dump

r.record
r.record(incrementFileName:false)
r.stop

r.openDirectory // find the files

r.numChannels_(2)
r.busnum_(a.bus)
r.sampleFormat = "int24"
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