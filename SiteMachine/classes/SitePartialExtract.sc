SitePartialExtract {
	var <inbus, <pollRate, <numBins, <minSlope, <fromFreq, <toFreq, <server, <loadCond;
	var <fftbuf, <magbuf, <freqbuf, <synthdef, <sinedef, <note, <extractRoutine, <testRoutine;
	var <fromBin, <toBin, <freqres, <sr;
	var <freqpeaks,<amppeaks, <peaklib, <peaklibcnt, <>peaklibsize, <>minpeaks;

	*new { |inbus, pollRate = 3, numBins=512, minSlope = 0.1, fromFreq = 130, toFreq = 10000, server, loadCond|
		^super.newCopyArgs(inbus, pollRate, numBins, minSlope, fromFreq, toFreq, server, loadCond).init;
	}

	init {
		fork {
			server = server ?? Server.default;
			sr = server.sampleRate;
			freqres = sr/2/numBins;

			fftbuf = Buffer.alloc(server, numBins*2);
			magbuf = Buffer.alloc(server, numBins);
			freqbuf = Buffer.alloc(server, numBins);

			freqpeaks = [];
			amppeaks = [];
			peaklib = [];
			peaklibcnt = 0;
			peaklibsize = 10;	// store this many peak extraction sets at any given time
			minpeaks = 5;		// must detect this many peaks to keep the set

			// set fromBin/toBin
			this.fromFreq_(fromFreq);
			this.toFreq_(toFreq);

			synthdef = CtkSynthDef(\magbufferana, {arg inbus, fftbuf, sndbuf, magbuf, freqbuf;
				var in, chain;
				in = InFeedback.ar(inbus, 1);
				chain = FFT(fftbuf, in);
				chain = PV_MagBuffer(chain, magbuf);
				chain = PV_FreqBuffer(chain, freqbuf);
				// Out.ar(0, IFFT(chain));
			});

			// for test method
			sinedef = CtkSynthDef(\siner, {arg outbus = 1, freq = 440, amp=0.5, dur=0.2;
				var env;
				env = EnvGen.kr(Env([0, 1, 0], [0.5, 0.5], \sin),timeScale:dur,doneAction: 2);
				Out.ar(outbus,SinOsc.ar(freq,mul:amp)*env);
			});

			this.loadRoutines;

			note = synthdef.note(addAction: \tail)
			.inbus_(inbus).fftbuf_(fftbuf).magbuf_(magbuf).freqbuf_(freqbuf);

			server.sync;
			loadCond !? {loadCond.test_(true).signal}; // for external loading process
		}
	}

	play {
		fork {
			note.play;
			0.2.wait;
			extractRoutine.reset.play;
		}
	}

	pause {
		note.pause;
		extractRoutine.stop;
	}

	stop { this.pause }

	test {
		testRoutine.reset.play;
	}

	stopTest {
		testRoutine.stop;
	}

	inbus_ { |busnum|
		note.inbus_(busnum)
	}

	free {
		[fftbuf, magbuf, freqbuf].do(_.free);
		[extractRoutine, testRoutine].do(_.stop);
		extractRoutine = testRoutine = nil;
		note.free;
	}

	loadRoutines {
		extractRoutine = Routine({
			inf.do{
				magbuf.getn(fromBin, toBin, {|vals|
					var xmax, ymax, fpks, apks;
					var p1, p2;
					// vals.do{|me, i| [i,me, (i+1)* 44100/2/512].postln}; // post the vals
					p1 = 0;
					p2 = 0;

					(vals ++ [0, 0]).do({
						| val, index |
						var slope1, slope2;
						slope1 = p1 - p2;
						slope2 = val - p1;

						if ((	(slope1 >= 0.0)
							&&	(slope1.abs > minSlope)
							&&	(slope2 <= 0.0)
							&&	(slope2.abs > minSlope)
							), { // interpolate peak value
								var fpk, apk;
								// peak bin offset from p1, fractional
								fpk = this.getxpeak(p2, p1, val);
								fpks = fpks.add((((index + fromBin + fpk) * freqres)));
								// peak amplitude
								apk = this.getypeak(p2, p1, val, fpk);
								apks = apks.add(apk);
								/*postf("found peak: %, [%], %, %",
								[p2, p1, val], index - 1, fpk,
								(index + fromBin + fpk) * freqres
								);*/
							}
						);
						p2 = p1;
						p1 = val;
					});

					apks !? {amppeaks = apks.normalizeSum};
					fpks !? {freqpeaks = fpks};

					if( (freqpeaks.size >= minpeaks) and: (freqpeaks.size == amppeaks.size),{
						if(peaklib.size < peaklibsize, {
							peaklib = peaklib.add([freqpeaks, amppeaks]);
							},{
								peaklib[peaklibcnt] = [freqpeaks, amppeaks];
								peaklibcnt = (peaklibcnt+1) % peaklibsize;
							}
						)
						},{
						}
					);

					//debug
					// ("peaks: \n").postln;
					// if( (freqpeaks.size > 0) and: (freqpeaks.size == amppeaks.size),{
					// 	[freqpeaks,amppeaks].lace(amppeaks.size*2).do{ |frqamppair|
					// 		"\t"++frqamppair.round(0.01).postln
					// 	};
					// });
				});

				pollRate.reciprocal.wait;
			};
		});

		// to output sines synthesized of analyzed audio stream
		testRoutine = Routine({
			inf.do{
				freqpeaks.do{|freq, i|
					sinedef.note.outbus_(1).freq_(freq).amp_(amppeaks[i]*0.05).dur_(0.7).play
				};
				(pollRate.reciprocal * 1.3).wait;
			};
		});
	}

	fromFreq_{ |freq|
		fromBin = (freq / (sr/2/numBins)).floor.asInt;
	}

	toFreq_{ |freq|
		toBin = (freq / (sr/2/numBins)).ceil.asInt;
	}

	// find local maxima
	getxpeak {
		|a, b, c|
		^((1/2) * ((a - c) / (a - (2*b) + c)));
	}
	getypeak {
		|a, b, c, p|
		^(b - (0.25 * (a - c) * p));
	}
}

/* test
~rawMotor1_ster = CtkBuffer.playbuf("/Users/admin/Documents/Suyama/studio_visit_motors_contact_plus_air/4CH/FOLDER01/4CH003I.wav").load(sync: true);

~srcBus = CtkAudio.play(2)

~fft_analysis = CtkSynthDef(\magbufferana, {
arg outbus=0, sndbuf, muteout = 0;
	var in, chain;
	in = PlayBuf.ar(2, sndbuf, BufRateScale.kr(sndbuf), loop: 1);
	// in = Mix.ar(5.collect{|i|SinOsc.ar(200 +(200*i))}) * 5.reciprocal;
	Out.ar(outbus, in * 1-muteout);
});


~fft = ~fft_analysis.note().outbus_(~srcBus).sndbuf_(~rawMotor1_ster).play;
s.sendMsg(\n_free, 1001)
s.scope(2, ~fft.outbus.bus)
~fft.free

~spe = SitePartialExtract(~srcBus.bus+1)
~spe = SitePartialExtract(~testbus.bus)
~spe.note.inbus.bus
~spe.note.inbus_(~testbus.bus)
~spe.note.inbus_(~testbus.bus+1)
~spe.note.inbus_(~testbus.bus+2)
~spe.note.inbus_(~testbus.bus+3)
~spe.note.inbus_(~testbus.bus+4)
~spe.note.inbus_(~testbus.bus+5)

~spe.play
~spe.test
~spe.stopTest

~spe.peaklib.size
~spe.peaklib[0].do{|i| i.round(0.01).postln;}; nil

~spe.stop
~spe.free

~spe.magbuf
~spe.freqbuf

~spe.fromBin
~spe.toBin


new { |inbus, pollRate = 5, numBins=512, minSlope = 0.1, fromFreq = 100, toFreq = 10000, server|
*/