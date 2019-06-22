//---------------------------//---------------------------
/* FIRST PART - Create some "fake" data to play to a Bus */
//---------------------------//---------------------------

(  // run the whole block >>>

s.waitForBoot({

	~numChannels = 4;  // number of channels of data to record

	// this is the multichannel bus you'll be recording your data to
	// (this bus will be replaced by the control signals)
	~dataBus = Bus.control(s, ~numChannels);

	s.sync;
	~cBusses = [SinOsc, LFSaw, LFTri, LFNoise2].collect{ |ugen, i|
		CtkControl.lfo(ugen, rrand(0.1, 4.5).reciprocal, bus: ~dataBus.index+i).play
	};
})

// NOTE: your data message rate is your effective "sample rate"
// so if you need to increase it, look in your python code to change the rate

)

// see the dummy busses we're recording:
s.scope(~dataBus.numChannels, ~dataBus.index, rate: 'control')




//---------------------------//---------------------------
/* RECORDING THE CONTROL SIGNALS */
//---------------------------//---------------------------

// create the Recorder
// WAIT: I prefer the convenience of the next ~recorder after this...
~recorder = RecordControl( ~dataBus, ~numChannels, makeGui: true);
/*
The first things you'll want to do are:
- select a recording directory
- type a filename (remember to push ENTER so the name is submitted)
- click "Plot signal" and observe your input signal
- - > you can have the plotter automatically set the bounds on your signal
- - - or you can enter bounds manually to keep the plots from continually rescaling
- Click Open directory, which will open the directory you're recording to
- - > that way you'll be able to see the files you're recording
*/
/*
Alternatively... you can specify your filename and/or save directory when instantiating.
Useful so you don't need to select the folder to record to with the dialog every time.
*/
~recorder = RecordControl( ~dataBus, ~numChannels, "ctlTest", "~/Desktop".standardizePath,  makeGui: true )


// Once you've recorded the signals, clean up those "fake" data busses - SAD!
~dataBus.free;
~cBusses.do(_.free);


//---------------------------//---------------------------
/* Read the data back in */
//---------------------------//---------------------------

(
// the path to your recorded file:
~path = "/Users/admin/Desktop/ctlTest_1_fs750.0.WAV";

// enter sample rate of the control data: it's appended to the filename!
~dataFs = 750;

File.exists(~path).not.if{ "Couldn't find that file!".throw };

fork ({
	var cond = Condition();

	~buf = Buffer.read(
		s,
		~path,
		action: { |b|
			"Buffer loaded from disk. Copying to a local array...".postln;
			b.loadToFloatArray(
				action: { |farray|

					// this global var will hold your recorded data for
					// resampling and writing to a file
					~data = farray.clump(~numChannels).flop;

					~numDataFrames = b.numFrames;
					"Buffer loaded into an array: ~array".postln;
					"Freeing the buffer.".postln;
					b.free;
					cond.test_(true).signal;
				}
			)
		}
	);
	cond.wait;
	cond.test = false;

	"Tony Dungy".postln;

	// plot the data!
	~data.plot;
}, AppClock);
)


// does that plot look ok?


//---------------------------//---------------------------
/* Resample the data to the video Fs */
//---------------------------//---------------------------

(
var resampleStep, resampIndices;

// Pick your favorite rate:
// ~newFs = 60; // for the heads
~newFs = 30; // 2009
// ~newFs = 24; // for the cinema lovers

// ratio of resampleing
resampleStep = ~dataFs / ~newFs;

// sample the original data at these indices
resampIndices = (0, resampleStep .. ~numDataFrames).round.asInteger;

(resampIndices.last > (~numDataFrames-1)).if{
	resampIndices = resampIndices.drop(-1)
};

~resampled = ~data.collect{ |chan|
	chan.at(resampIndices)
};
)


/* Validation, we all need it */

// do they look the same?
([~data] ++ [~resampled]).lace(~numChannels * 2).plot; // side-by-side

~data.plot;			// original data
~resampled.plot;	// resampled data

// is all the data there?
~data.shape[1]		// size of og data
~resampled.shape[1]	// size of resampled data

// or better, do their durations match?
(~data.shape[1] / ~dataFs).fuzzyEqual(~resampled.shape[1] / ~newFs) > 0
// OK!

//---------------------------//---------------------------
/* Write the resampled data to a TEXT file (csv) */
//---------------------------//---------------------------

// NOTE: format is 1 multichannel frame per line
//       for easy score rendering: one line of parameters per frame
(
var wr, line;

// Where do you want to save the txt file?
~txtPath = "/Users/admin/Desktop/ctlTest.txt"; // or .csv?

wr = CSVFileWriter(~txtPath, "w");
// if you prefer a different delimiter, there's also
// TabFileWriter, SemiColonFileWriter, or FileWriter (choose yr own delimiter)

// write the data to file
~resampled.shape[1].do{ |i|
	line = ~resampled.collect(_.at(i));
	wr.writeLine(line);
};
// close the file
wr.close;
)

// Make sure the txt file was written correctly.
// Read the data back fro the txt file, plot it
// alongside the original and resampled data
(
var rd, res;
rd = CSVFileReader.read(~txtPath);
rd = rd.asFloat;
~dataFromFile = rd.flop;
)

([~data] ++ [~resampled] ++ [~dataFromFile]).lace(~numChannels * 3).plot // side-by-side

// Clean up these huge arrays
~data = ~resampled = ~dataFromFile = nil;
