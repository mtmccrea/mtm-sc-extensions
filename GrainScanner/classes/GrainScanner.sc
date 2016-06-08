// a master class that holds multiple GrainScan1's,
// stores presets for multiple instances, etc.
GrainScanner1 {
	classvar <verbDef;
	// copyArgs
	var numScanners, initBufOrPath, outbus, initGUI;
	var <scanners, <buffers, <bufferPath, <lastRecalledPreset, loadSuccessful, <server;
	var <presetWin, <auxBus, <group, <verbSynths;

	*new {|numScanners, bufOrPath, outbus = 0, initGUI = true|
		^super.newCopyArgs(numScanners, bufOrPath, outbus, initGUI).init;
	}

	init {

		fork({
			var bufcond = Condition();
			loadSuccessful = true; // init load test
			server = Server.default;

			this.initBuffer( initBufOrPath, bufcond );
			bufcond.wait;

			// catch failed buffer load
			loadSuccessful.if({
				group = CtkGroup.play();
				auxBus = CtkAudio.play(2);

				this.class.verbDef ?? { this.loadSynthLib; 0.2.wait; server.sync; };

				verbSynths = 2.collect{ |i|
					verbDef.note(addAction: \tail, target: group)
					.outbus_(2+i).inbus_(auxBus.bus+i).mix_(1).play
				};

				scanners = numScanners.collect{ GrainScan1(outbus, buffers, this) };

				initGUI.if{ 0.3.wait; scanners.do(_.gui) };

				Archive.global[\grainScanner1] ?? { this.prInitArchive };
			},{error("GrainScanner1 failed to load, check your provided buffer/path")});

		}, AppClock )
	}

	storePreset { |name, overwrite=false|
		block{ |break|
			(this.presets[name.asSymbol].notNil and: overwrite.not).if {
				warn("NOT CREATING PRESET. Preset already exists! Choose another name or explicitly overwrite with flag");
				break.()
			};

			this.presets.put( name.asSymbol,
				IdentityDictionary(know: true).putPairs([
					\bufName, PathName(scanners[0].synths[0].buffer.path).fileName,

					\params, scanners.collect{ |scanner, i|
						IdentityDictionary(know: true).putPairs([
							// NOTE: these keys must match the class setters
							// so they can be recalled with this.perform(key++'_', val)
							\grnDur,		scanner.synths[0].grainDur,
							\grnRand,		scanner.synths[0].grainRand,
							\minDisp,		scanner.synths[0].minDisp,
							\maxDisp,		scanner.synths[0].maxDisp,
							\density,		scanner.synths[0].density,
							\fluxRate,		scanner.synths[0].fluxRate,
							\start,			scanner.synths[0].start,
							\end,			scanner.synths[0].end,
							\amp,			scanner.synths[0].amp,
						]);
					}
				])
			);
			postf("Preset % stored\n", name);
		}
	}

	play { |fadeTime = 0.5|
		scanners.do(_.play(fadeTime));
	}
	resume { |fadeTime = 4|
		scanners.do(_.play(fadeTime));
	}
	release { |fadeTime = 4|
		scanners.do(_.release(fadeTime));
	}

	recallPreset { |name, fadeTime = 0.1|
		var preset = this.presets[name.asSymbol];

		fork{
			block { |break|
				var curFileName;
				preset ??	{ "Preset not found.".warn; break.() };

				// TODO: check the number of scanners used in this
				// preset vs. how many are currently runnin and adjust accordingly

				// check if the preset uses a different buffer...
				curFileName = PathName(buffers[0].path).fileName;
				if(curFileName != preset.bufName, {
					warn("Preset file doens't match the buffer currently loaded");
					// TODO: load the requested preset buffer
					break.()
				});

				// recall the synth settings
				fork({
					var panCen = rrand(-1.0,1.0), ampScale;
					ampScale = [0, -5, -8].dbamp.scramble;

					preset[\params].do{|dict, index|
						scanners[index].synths.do{ |synth, j|
							synth
							.ctllag_(fadeTime)
							// .panCenter_((panCen + (scanners.size.reciprocal * index)).wrap(-1,1))
							// .panOffset_(j) // 180 deg offset from one another (stereo sources)
							// changed for simple l/r hard pans
							.panCenter_(-1 + (2*j))
							.panOffset_(0) // 180 deg offset from one another (stereo sources)
						};
						dict.keysValuesDo({ |k,v|
							if( k.asSymbol == 'amp',
								{	postf("scaling amp: % % > %",k, v, v * ampScale[index] );
									scanners[index].perform((k++'_').asSymbol, v * ampScale[index] )
								},
								{scanners[index].perform((k++'_').asSymbol, v)}
							);
						})
					};
					lastRecalledPreset = name.asSymbol;
					fadeTime.wait;
					// reallign the gran pointer for the new preset
					scanners.do{|scnr| scnr.synths.do(_.t_posReset_(1)) };
				}, AppClock);
			}
		}
	}

	updatePreset {
		lastRecalledPreset !? {
			postf("Updating preset %\n", lastRecalledPreset);
			this.storePreset( lastRecalledPreset, overwrite: true )
		}
	}

	removePreset { |name|
		this.presets[name.asSymbol].notNil.if(
			{ this.presets.removeAt(name.asSymbol); postf("Preset % removed\n", name) },
			{ warn("Preset not found.. didn't remove!") }
		);
	}

	presetGUI { |numCol=1|
		var presetsClumped, ftBox, varBox, msg_Txt, presetLayouts, maxRows;
		maxRows = (this.presets.size / numCol).ceil.asInt;

		presetsClumped = this.presets.keys.asArray.sort.clump(maxRows);

		presetLayouts = presetsClumped.collect({ |presetGroup|
			VLayout(
				*presetGroup.extend(maxRows,nil).collect({ |name, i|
					var lay;
					name.notNil.if({
						lay = HLayout(
							[ Button().states_([[name]])
								.action_({
									this.recallPreset(name.asSymbol, ftBox.value);
									msg_Txt.string_(format(
										"preset % updated.", name.asSymbol)).stringColor_(Color.black);
							}), a: \top]
						)
					},{
						nil
					})
				})
			)
		});

		presetWin = Window("Presets", Rect(0,0,100, 100)).view.layout_(
			VLayout(
				[ Button().states_([
					["Play", Color.black, Color.grey],
					["Release", Color.white, Color.red]

				]).action_({ |but|
					switch( but.value,
						0, {this.release},
						1, {this.play}
					)
				}).maxWidth_(70).fixedHeight_(35), a: \right],
				HLayout(
					nil,
					StaticText().string_("Fade Time").align_(\right).fixedHeight_(25),
					ftBox = NumberBox().value_(1.0).maxWidth_(35).fixedHeight_(25)
				),
				HLayout(
					msg_Txt = StaticText().string_("Select a preset to recall.").fixedHeight_(35),
					Button().states_([["Update Preset"]]).action_({this.updatePreset}).fixedWidth_(95)
				),
				HLayout( *presetLayouts )
			)
		).front;
	}

	free { |freeBufs=false|
		scanners.do(_.free);
		freeBufs.if(buffers.do(_.free));
		group.freeAll;
	}

	prInitArchive {
		^Archive.global.put(\grainScanner1, IdentityDictionary(know: true));
	}

	presets { ^Archive.global[\grainScanner1] }
	listPresets { ^this.presets.keys.asArray.sort.do(_.postln) }

	*archive { ^Archive.global[\grainScanner1] }
	*presets { ^Archive.global[\grainScanner1] }
	*listPresets { ^this.class.presets.keys.asArray.sort.do(_.postln) }

	backupPreset {
		this.class.backupPreset
	}

	*backupPreset {
		Archive.write(format("~/Desktop/archive_GrainScanner1BAK_%.sctxar",Date.getDate.stamp).standardizePath)
	}

	initBuffer { |bufOrPath, finishCond|
		case
		// buffers is a string (path) to soundfile to assign to buffers
		{ bufOrPath.isKindOf(String) }{
			this.prepareBuffers(bufOrPath, finishCond);
		}
		// this assumes mutiple channels of mono buffers from the same file path
		{ bufOrPath.isKindOf(Array) }{
			var failtest;
			failtest = bufOrPath.collect({|elem| elem.isKindOf(Buffer)}).includes(false);
			failtest.if({
				"one or more elements in the array provided is not a buffer".error;
				loadSuccessful = false;
			},{
				buffers = bufOrPath;
				bufferPath = buffers[0].path;
			});
			finishCond.test_(true).signal;
		}
		{ bufOrPath.isKindOf(Buffer) }{
			case
			// use the mono buffer
			{ bufOrPath.numChannels == 1 }{
				"using mono buffer provided".postln;
				bufferPath = bufOrPath.path;
				buffers = [bufOrPath];
				finishCond.test_(true).signal;
			}
			// split into mono buffers
			{ bufOrPath.numChannels > 1 }{
				"loading buffer anew as single channel buffers".postln;
				this.prepareBuffers(bufOrPath.path, finishCond);
			}
		};
	}

	prepareBuffers { |path, finishCond|

		block { |break|
			var sf;
			// check the soundfile is valid and get it's metadata
			sf = SoundFile.new;
			{sf.openRead(path)}.try({
				"Soundfile could not be opened".warn;
				loadSuccessful = false;
				finishCond.test_(true).signal;
				break.()
			});
			sf.close;

			// load the buffers
			fork {
				// one condition for each channel loaded into a Buffer
				var bufLoadConds = [];
				buffers = sf.numChannels.collect{|i|
					var myCond = Condition();
					bufLoadConds = bufLoadConds.add(myCond);
					Buffer.readChannel(
						server, path,
						channels: i, action: {myCond.test_(true).signal} );
				};
				bufLoadConds.do(_.wait); // wait for each channel to load

				"grain scanner1 buffer(s) loaded".postln;
				bufferPath = path;
				finishCond.test_(true).signal;
			}
		}
	}

	swapBuffer { |newBufOrPath|
		var curBuffers = buffers;
		fork({
			var bufcond = Condition();
			loadSuccessful = true; // init load test
			server = Server.default;

			this.initBuffer( initBufOrPath, bufcond );
			bufcond.wait;

			// catch failed buffer load
			loadSuccessful.if({
				// send the new buffer to the synths
				scanners.do{ |scnr|
					// TODO: this is a temporary constraint to
					// protect from synth/buffer channel mismatch
					(scnr.synths.size == buffers.size).if({
						scnr.synths.do{|synth, i|
							synth.buf_(buffers[i]).bufnum_(buffers[i].bufnum)
						}
					},{
						error("mismatch between number of scanner synths and buffer channels.");
						buffers.do_(_.free);
					});
				};
				// free the old buffers
				curBuffers.do(_.free);
			},{ error("Failed to swap buffers, check your provided buffer/path")});

		}, AppClock )
	}



	// reverb control
	sendToReverb { |onsetTime = 10|
		 scanners.do{ |scnr|
			scnr.synths.do(_.auxLag_(onsetTime));
			scnr.synths.do(_.t_auxOnset_(1));
		};
		verbSynths.do(_.auxLag_(onsetTime));
		verbSynths.do(_.t_auxOnset_(1));

	}

	verbOnsetCurve_ { |curveNum|
		scanners.do{ |scnr| scnr.synths.do(_.auxOnsetCurve_(curveNum)) };
		verbSynths.do(_.auxOnsetCurve_(curveNum));
	}



	loadSynthLib {

		verbDef = CtkSynthDef(\verb_localin, {
			arg outbus = 0, inbus, amp = 1,
			// unused args with dynamic params
			decayTime = 2, mix = 0.5, apDecay = 0.2, scaleReflections = 1, dampFreq = 1800,
			t_auxOnset = 0, auxLag = 10, auxOnsetCurve = 3,

			// dynamic arg params
			minSclReflect = 1, maxSclReflect = 18,
			minDecayTime = 0.5, maxDecayTime = 3.3,
			minAPDecay = 0.05, maxAPDecay = 1.5,
			minMix = 0.2, maxMix = 0.85,
			minDampFreq = 3500, maxDampFreq = 16500,
			verbCutTail = 10;


			var src, combDels, g, lIn, lOut, delay, combs, ap, out;
			var apDelay = 0.095, apOrder = 6;
			var xFormEnv, longOnsetxFormEnv, sclReflect, decTime, apDec, verbMix, dampFrq;


			src = In.ar(inbus, 1);

			xFormEnv = EnvGen.kr(
				Env([0,0,1],[0,auxLag*0.6], auxOnsetCurve, releaseNode:1, loopNode: 0),
				TDelay.kr(t_auxOnset, 0.05), doneAction: 0
			);
			longOnsetxFormEnv = EnvGen.kr(
				Env([0,0,1],[0,auxLag], auxOnsetCurve, releaseNode:1, loopNode: 0),
				TDelay.kr(t_auxOnset, 0.05), doneAction: 0
			);

			longOnsetxFormEnv = LagUD.kr(longOnsetxFormEnv, 0.1, verbCutTail);
			xFormEnv = LagUD.kr(xFormEnv, 0.1, verbCutTail);

			sclReflect	= LinLin.kr(longOnsetxFormEnv, 0,1,
				minSclReflect, TRand.kr( maxSclReflect*0.7, maxSclReflect, t_auxOnset)); //.poll(label: "sclReflect");
			decTime		= LinLin.kr(xFormEnv, 0,1,
				minDecayTime, TRand.kr( maxDecayTime*0.25, maxDecayTime, t_auxOnset) ); //.poll(label: "decTime");
			apDec		= LinLin.kr(longOnsetxFormEnv, 0,1,
				minAPDecay, TRand.kr( maxAPDecay*0.25, maxAPDecay, t_auxOnset)); //.poll(label: "apDec");
			verbMix		= LinLin.kr(longOnsetxFormEnv, 0,1,
				minMix, maxMix);
			dampFrq		= LinLin.kr(longOnsetxFormEnv, 0,1,
				maxDampFreq, TRand.kr( minDampFreq, minDampFreq*2, t_auxOnset)); //.poll(label: "dampFrq");

			combDels = ([0.0297, 0.0371, 0.0411, 0.0437] + 4.collect({Rand(0.0, 0.004)}));
			// combDels = combDels * scaleReflections;
			combDels = combDels * sclReflect;


			// calculate feedback coefficient
			// g = 10.pow(-3 * combDels / decayTime);
			g = 10.pow(-3 * combDels / decTime);

			lIn = LocalIn.ar(4);

			combs = DelayC.ar(src + (lIn * g),
				// combDels.maxItem - ControlRate.ir.reciprocal,
				2.5 - ControlRate.ir.reciprocal,
				combDels - ControlRate.ir.reciprocal
			);

			// combs = LPF.ar(combs, dampFreq); // damping
			combs = LPF.ar(combs, dampFrq); // damping

			combs = LeakDC.ar(combs);

			lOut = LocalOut.ar(combs);

			ap = combs.sum;
			apOrder.do({|i|
				ap = AllpassC.ar( ap,
					apDelay, // 2.0,
					apDelay.rand * LFTri.kr( rrand(8,17.0).reciprocal ).range(0.9, 1), // mod delays a bit
					// apDecay
					apDec
				);
			});

			delay = DelayN.ar(src, ControlRate.ir.reciprocal, ControlRate.ir.reciprocal); // make up delay

			// out = (mix.sqrt * ap) + ((1 - mix.sqrt) * delay);
			out = (verbMix.sqrt * ap) + ((1 - verbMix.sqrt) * delay);

			// ReplaceOut.ar(outbus, out)
			Out.ar(outbus, out * amp)
		});
	}
}

GrainScan1 {
	classvar <grnSynthDef;

	// copyArgs
	var <outbus, <bufferOrPath, <gsMaster;
	var server, <buffers, <group, <synths, <bufDur, <view, <bufferPath, <auxBus;
	var <grnDurSpec, <grnRateSpec, <grnRandSpec, <pntrRateSpec, <grnDispSpec, <densitySpec, <fluxRateSpec, <ampSpec;
	var <>newMomentFunc;

	*new { |outbus=0, bufferOrPath, gsMaster|
		^super.newCopyArgs( outbus, bufferOrPath, gsMaster ).init;
	}

	init {
		server = Server.default;

		grnDurSpec = ControlSpec(0.01, 8, warp: 3, default: 1.3);
		grnRateSpec = ControlSpec(4.reciprocal, 70, warp: 3, default:10);
		grnRandSpec = ControlSpec(0, 1, warp: 4, default: 0.0);
		pntrRateSpec = ControlSpec(0.05, 3, warp: 0, default: 1);
		grnDispSpec = ControlSpec(0, 5, warp: 3, default: 1.5);
		densitySpec = ControlSpec(4.reciprocal, 25, warp: 3, default:13);
		fluxRateSpec = ControlSpec(12.reciprocal, 1, warp: 3, default:0.2);
		ampSpec = ControlSpec(-inf, 12, warp: 'db', default:0);

		fork{
			var cond = Condition();

			this.class.grnSynthDef ?? { this.loadSynthLib; 0.2.wait; };
			server.sync;

			case
			{ bufferOrPath.isKindOf(String) }{
				bufferPath = bufferOrPath;
				this.prepareBuffers(cond);
				cond.wait;
			}
			// this assumes mutiple channels of mono buffers from the same file path
			{ bufferOrPath.isKindOf(Array) }{
				var test;
				test = bufferOrPath.collect({|elem| elem.isKindOf(Buffer)}).includes(false);
				test.if{ "one or more elements in the array provided is not a buffer".error };
				buffers = bufferOrPath;
				bufferPath = buffers[0].path;
			}
			{ bufferOrPath.isKindOf(Buffer) }{
				case
				// use the mono buffer
				{ bufferOrPath.numChannels == 1 }{
					"using mono buffer provided".postln;
					bufferPath = bufferOrPath.path;
					buffers = [bufferOrPath];
				}
				// split into mono buffers
				{ bufferOrPath.numChannels > 1 }{
					"loading buffer anew as single channel buffers".postln;
					bufferPath = bufferOrPath.path;
					this.prepareBuffers(cond);
					cond.wait;
				}
			};

			bufDur = buffers[0].duration;

			group = CtkGroup.play;
			server.sync;

			this.initSynths;
		}
	}

	prepareBuffers { |finishCond|

		block { |break|
			var sf;
			// check the soundfile is valid and get it's metadata
			sf = SoundFile.new;
			{sf.openRead(bufferPath)}.try({
				"Soundfile could not be opened".warn;
				finishCond.test_(true).signal;
				break.()
			});
			sf.close;

			// load the buffers
			fork {
				// one condition for each channel loaded into a Buffer
				var bufLoadConds = [];
				buffers = sf.numChannels.collect{|i|
					var myCond = Condition();
					bufLoadConds = bufLoadConds.add(myCond);
					Buffer.readChannel(
						server, bufferPath,
						channels: i, action: {myCond.test_(true).signal} );
				};
				bufLoadConds.do(_.wait); // wait for each channel to load
				"grain scanner1 buffer(s) loaded".postln;
				finishCond.test_(true).signal;
			}
		}
	}

	initSynths {
		var pancen = rrand(-1,1.0);

		synths = buffers.collect{|buf, i|
			grnSynthDef.note(addAction: \head, target: gsMaster.group)
			.outbus_(outbus)
			.auxOutbus_(gsMaster.auxBus.bus + i)
			.buffer_(buf).bufnum_(buf.bufnum)
			.grainDur_(2.7)
			//.panCenter_(pancen).panOffset_(i)
			.panCenter_(-1 + (2*i)).panOffset_(0)
		};

	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	/* grain controls */

	play { |fadeInTime|
		fadeInTime !? {synths.do(_.fadein_(fadeInTime))};
		"playing".postln;
		synths.do({|synth| synth.isPlaying.not.if({synth.play},{synth.gate_(1)}) });
	}

	release { |fadeOutTime|
		fadeOutTime !? { synths.do(_.fadeout_(fadeOutTime)) };
		synths.do({|synth|
			synth.isPlaying.not.if({"synth isn't playing".warn},{synth.gate_(0)}) });
	}

	grnDur_ {|dur| synths.do(_.grainDur_(dur)); this.changed(\grnDur, dur); }

	grnRate_ {|rateHz| synths.do(_.grainRate_(rateHz)); this.changed(\grnRate, rateHz); }

	// gaussian trigger: 0 = regular at grainRate, 1 = random around grainRate
	grnRand_ {|distro| synths.do(_.grainRand_(distro)); this.changed(\grnRand, distro); }

	// position dispersion of the pointer, in seconds
	grnDisp_ {|dispSecs| synths.do(_.grnDisp_(dispSecs)); this.changed(\grnDisp, dispSecs); }
	// speed of the grain position pointer in the buffer, 1 is realtime, 0.5 half-speed, etc
	pntrRate_ {|rateScale| synths.do(_.posRate_(rateScale)); this.changed(\pntrRate, rateScale); }

	minDisp_ {|dispSecs| synths.do(_.minDisp_(dispSecs)); this.changed(\minDisp, dispSecs); }
	maxDisp_ {|dispSecs| synths.do(_.maxDisp_(dispSecs)); this.changed(\maxDisp, dispSecs); }
	density_ {|numGrains| synths.do(_.density_(numGrains)); this.changed(\density, numGrains); }
	fluxRate_ {|rate| synths.do(_.fluxRate_(rate)); this.changed(\fluxRate, rate); }
	start_ {|timeNorm| synths.do(_.start_(timeNorm)); this.changed(\start, timeNorm); }
	end_ {|timeNorm| synths.do(_.end_(timeNorm)); this.changed(\end, timeNorm); }

	amp_ {|amp| synths.do(_.amp_(amp)); this.changed(\amp, amp); }


	// sync the synths' pointers by resetting to beginning of loop
	// optionally supply pointer rate so they stay sync'd
	syncLoop { |pntrRate|
		pntrRate !? { synths.do(_.posRate_(pntrRate)) };
		synths.do(_.t_posReset_(1));
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	/* scan controls */


	// move to a new "moment" and loop over spanSec seconds
	// window centered at centerSec
	scanRange{ | centerSec, spanSec, syncBufs=true |
		var span, moment;

		span = spanSec / bufDur; // the span of time of the scanning window
		moment = centerSec / bufDur;

		synths.do({|me|
			me.start_(moment - span.half)
			.end_(moment + span.half)
		});
		// sync up the instances, by resetting to start position
		syncBufs.if{ synths.do(_.t_posReset_(1)) };
	}

	// // direct control over pointer location
	// setPointer { | distFromCentroid = 0, grainSize = 2 |
	// 	cluster
	// }


	gui { view = GrainScan1View(this); }


	free {
		group.freeAll;
		buffers.do(_.free);
		auxBus.free;
	}


	loadSynthLib {

		grnSynthDef = CtkSynthDef(\grainScanner1, {
			arg
			buffer, bufnum,
			outbus, 			// main out
			auxOutbus,			// outbus to reverb
			start=0, end=1,		// bounds of grain position in sound file
			grainRand = 0,		// gaussian trigger: 0 = regular at grainRate, 1 = random around grainRate
			grainRate = 10, grainDur = 0.04,
			grnDisp = 0.01,		// position dispersion of the pointer, as percentage of soundfile duration
			//pitch=1,
			// auxmix=0,
			amp=1,
			fadein = 2, fadeout = 2,
			posRate = 1,		// change the speed of the grain position pointer (can be negative)
			// posInv = 0,		// flag (0/1) to invert the posRate
			monAmp = 1,			// monitor amp, for headphones
			amp_lag = 0.3,		// time lag on amplitude changes (amp, xfade, mon send, aux send)
			balance_amp_lag = 0.3,	// time lag on amplitude xfade changes
			t_posReset = 0,		// reset the phasor position with a trigger
			gate = 1,			// gate to start and release the synth

			fluxRate = 0.2,		// rate at which density and dispersion change
			density = 13,		// desity held while modulating duration
			maxDisp = 2.5,		// dispersion can modulate between max and min dispersion
			minDisp = 0.01,
			panCenter = 0,
			panOffset = 0.5,
			globalAmp = 1,		// for overall control over all instances
			ctllag = 0.1, ctlcurve = 0, // fade some of the controls
			t_auxOnset = 0, auxLag = 10, auxOnsetCurve = 3;

			var
			env, grain_dens, amp_scale, trig, b_frames,
			pos, disp, sig, out, aux, auxmix_lagged, frames_start;
			var
			panPos, flux, g_rate;
			var
			gdur, grand, dens, frate, vol, st, ed;

			// envelope for fading output in and out - re-triggerable
			env = EnvGen.kr(Env([1,1,0],[fadein, fadeout], \sin, 1), gate, doneAction: 0);

			// lag values
			#gdur, grand, dens, frate, vol, st, ed = VarLag.kr(
				[grainDur, grainRand, density, fluxRate, amp, start, end],
				ctllag, ctlcurve );

			flux = LFDNoise3.kr( frate); // -1>1

			g_rate = dens * (gdur + (gdur * 0.15 * flux)).reciprocal;
			amp_scale = dens.reciprocal.sqrt.clip(0, 1);
			trig = GaussTrig.ar(g_rate, grand);

			// // calculate grain density
			// grain_dens = grainRate * gdur;
			// amp_scale = grain_dens.reciprocal.sqrt.clip(0, 1);
			//
			// gaussian trigger
			// grainRand = 0 regular at grainRate
			// grainRand = 1 random around grainRate
			// trig = GaussTrig.ar(grainRate, grainRand);


			b_frames = BufFrames.kr(bufnum);
			frames_start = b_frames * st;
			// use line to go from start to end in buffer
			pos = Phasor.ar( t_posReset,
				BufRateScale.kr(bufnum) * posRate, // * (posRate + (posRate * 0.05 * flux)), //* (1 - (posInv*2)),
				frames_start, b_frames * ed, frames_start
			);
			pos = pos * b_frames.reciprocal;

			// add randomness to position pointer, make sure it remains within limits
			// disp = grnDisp * BufDur.kr(bufnum).reciprocal * 0.5; // grnDisp (secs) normalized 0-1
			disp = LFDNoise3.kr( frate ).range(minDisp, maxDisp) * BufDur.kr(bufnum).reciprocal * 0.5;
			pos = pos + TRand.ar(disp.neg, disp, trig);
			pos = pos.wrap(st , ed);

			/* granulator */
			sig = GrainBufJ.ar(1, trig, gdur, buffer, 1 , pos, 1, interp: 1, grainAmp: amp_scale);
			// sig = GrainBuf.ar(numChannels: 1, trigger: trig, dur: gdur, sndbuf: buffer, rate: 1, pos: pos, interp: 1, mul: amp_scale);

			/* overall amp control */
			sig = Limiter.ar( sig * vol * env, -0.5.dbamp);
			out = sig * globalAmp;

			/* aux send control */
			auxmix_lagged = EnvGen.kr(
				Env([0,0,1],[0,auxLag], auxOnsetCurve, releaseNode:1, loopNode: 0),
				t_auxOnset, doneAction: 0
			).sqrt;

			// balance between dry and wet routing
			// out = sig * (1 - auxmix_lagged).sqrt;
			// aux = sig * auxmix_lagged.sqrt;

			panPos = panCenter + panOffset;
			// out = PanAz.ar(4, out, (panPos + (0.1 * flux)).wrap(-1,1));
			// out = PanAz.ar(2, out, (panPos + (0.1 * flux)).wrap(-1,1)); // keep in the front of the container
			out = PanAz.ar(2, out, panPos ); // keep in the front of the container

			// send signals to outputs
			// Out.ar( outbus,		out);
			Out.ar( outbus,		DelayN.ar(out, ControlRate.ir.reciprocal, ControlRate.ir.reciprocal)); // delayed for reverb
			Out.ar( auxOutbus,	out * LagUD.kr(auxmix_lagged , 0.1, 5) );
		});
	}
}

GrainScan1View {
	// copyArgs
	var scanner;
	var <win, <controls;

	*new {|aGrainScan1|
		^super.newCopyArgs( aGrainScan1 ).init;
	}

	init {
		scanner.addDependant( this );
		controls = IdentityDictionary(know: true);
		this.buildControls;
		this.makeWin;
	}

	buildControls {

		controls.putPairs([
			\grnDur, ()
			.numBox_( NumberBox().maxDecimals_(3)
				.action_({ |bx|scanner.grnDur_(bx.value) })
				.value_(scanner.grnDurSpec.default)
			)
			.knob_(	Knob().step_(0.001)
				.action_({|knb|
					scanner.grnDur_(scanner.grnDurSpec.map(knb.value)) })
				.value_(scanner.grnDurSpec.unmap(scanner.grnDurSpec.default))
			),

			\grnRate, ()
			.numBox_( NumberBox().maxDecimals_(3)
				.action_({ |bx|
					scanner.grnRate_(bx.value) })
				.value_(scanner.grnRateSpec.default)
			)
			.knob_(	Knob().step_(0.001)
				.action_({|knb|
					scanner.grnRate_(scanner.grnRateSpec.map(knb.value)) })
				.value_(scanner.grnRateSpec.unmap(scanner.grnRateSpec.default))
			),

			\grnRand, ()
			.numBox_( NumberBox().maxDecimals_(3)
				.action_({ |bx|
					scanner.grnRand_(bx.value) })
				.value_(scanner.grnRandSpec.default)
			)
			.knob_(	Knob().step_(0.001)
				.action_({|knb|
					scanner.grnRand_(scanner.grnRandSpec.map(knb.value)) })
				.value_(scanner.grnRandSpec.unmap(scanner.grnRandSpec.default))
			),

			\pntrRate, ()
			.numBox_( NumberBox().maxDecimals_(3)
				.action_({ |bx|
					scanner.pntrRate_(bx.value) })
				.value_(scanner.pntrRateSpec.default)
			)
			.knob_(	Knob().step_(0.001)
				.action_({|knb|
					scanner.pntrRate_(scanner.pntrRateSpec.map(knb.value)) })
				.value_(scanner.pntrRateSpec.unmap(scanner.pntrRateSpec.default))
			),

			\grnDisp, ()
			.numBox_( NumberBox().maxDecimals_(3)
				.action_({ |bx|
					scanner.grnDisp_(bx.value) })
				.value_(scanner.grnDispSpec.default)
			)
			.slider_(	Slider()
				.action_({|sldr|
					scanner.grnDisp_(scanner.grnDispSpec.map(sldr.value)) })
				.value_(scanner.grnDispSpec.unmap(scanner.grnDispSpec.default))
			),

			\minDisp, ()
			.numBox_( NumberBox().maxDecimals_(3)
				.action_({ |bx|
					scanner.minDisp_(bx.value) })
				.value_(scanner.synths[0].minDisp)
			),

			\maxDisp, ()
			.numBox_( NumberBox().maxDecimals_(3)
				.action_({ |bx|
					scanner.maxDisp_(bx.value) })
				.value_(scanner.synths[0].maxDisp)
			),

			\density, ()
			.numBox_( NumberBox().maxDecimals_(3)
				.action_({ |bx|
					scanner.density_(bx.value) })
				.value_(scanner.densitySpec.default)
			)
			.knob_(	Knob().step_(0.001)
				.action_({|knb|
					scanner.density_(scanner.densitySpec.map(knb.value)) })
				.value_(scanner.densitySpec.unmap(scanner.densitySpec.default))
			),

			\fluxRate, ()
			.numBox_( NumberBox().maxDecimals_(3)
				.action_({ |bx|
					scanner.fluxRate_(bx.value) })
				.value_(scanner.fluxRateSpec.default)
			)
			.knob_(	Knob().step_(0.001)
				.action_({|knb|
					scanner.fluxRate_(scanner.fluxRateSpec.map(knb.value)) })
				.value_(scanner.fluxRateSpec.unmap(scanner.fluxRateSpec.default))
			),


			\fadeIO, ()
			.button_(
				Button()
				.states_([["*", Color.black, Color.red], ["*", Color.black, Color.green]])
				.action_({
					|but| switch( but.value,
						0, {scanner.release},
						1, {scanner.play}
					);
				}).value_(0)
			)
			.txt_( StaticText().string_("Fade in/out") ),

			\amp, ()
			.numBox_( NumberBox().maxDecimals_(2)
				.action_({ |bx|
					scanner.amp_(bx.value.dbamp) })
				.value_(scanner.ampSpec.default)
			)
			.slider_(	Slider()
				.action_({|sldr|
					scanner.amp_(scanner.ampSpec.map(sldr.value).dbamp) })
				.value_(scanner.ampSpec.unmap(scanner.ampSpec.default))
			),

			// reset the pointer moment loop
			\syncPntr, ()
			.button_( Button().states_([[""]]).action_({ |but|
				scanner.syncLoop }) )
			.txt_( StaticText().string_("Sync pointer") ),

			\newPos, ()
			.button_( Button().states_([[""]]).action_({ |but|
				scanner.scanRange(
					*scanner.newMomentFunc.notNil.if(
						{ scanner.newMomentFunc.value },
						{ [scanner.bufDur.rand, rrand(3.5, 8.0)] }),
				)
			}) )
			.txt_( StaticText().string_("New moment") ),

		]);
	}

	makeWin {
		win = Window("a GrainScanner", Rect(200,200,100,100)).layout_(
			VLayout(
				HLayout(
					*[\grnDur, \grnRate, \grnRand, \pntrRate].collect({ |key, i|
						var col = Color.hsv(0.05, 0.45, 1);
						VLayout( StaticText().string_(key).align_(\center).background_(col),
							HLayout(
								controls[key].numBox.fixedWidth_(35).background_(col.alpha_(0.5)),
								controls[key].knob.mode_(\vert).background_(col.alpha_(0.5))
							)
						)
					})
				),

				HLayout(
					// 	// VLayout(
					// 	// 	[controls[\grnDisp].slider.orientation_(\vertical).minHeight_(150), a: \left],
					// 	// 	[controls[\grnDisp].numBox.fixedWidth_(35), a: \left],
					// 	// ),
					VLayout(
						[controls[\amp].slider.orientation_(\vertical).minHeight_(200), a: \left],
						[controls[\amp].numBox.fixedWidth_(35).align_(\center), a: \left],
					),
					VLayout( *[\fadeIO, \newPos, \syncPntr].collect({ |key|
						HLayout(
							[controls[key].button.fixedWidth_(35), a: \left],
							[controls[key].txt.align_(\left), a: \bottomLeft ]
						)
					})
					// ++ [nil, [StaticText().string_("pntr Dispersion").align_(\left), a: \bottom]]
					++ [nil, [StaticText().string_("dB").align_(\left), a: \bottomLeft]]
					),
					nil,
					VLayout( *[\minDisp, \maxDisp].collect({ |key|
						var col = Color.hsv(0.1, 0.45, 1);
						VLayout(
							StaticText().string_(key.asString).align_(\center).background_(col),
							[controls[key].numBox.fixedWidth_(35).align_(\center).background_(col.alpha_(0.5)), a: \center],
						)
					}) ++ [nil]
					),
					10,
					VLayout( *[\density, \fluxRate].collect({ |key|
						var col = Color.hsv(0.15, 0.45, 1);
						VLayout(
							StaticText().string_(key.asString).align_(\center).background_(col),
							HLayout(
								controls[key].numBox.fixedWidth_(35).align_(\center).background_(col.alpha_(0.5)),
								controls[key].knob.mode_(\vert).fixedWidth_(35).background_(col.alpha_(0.5)),
							)
						)
					}) ++ [nil]
					),
				),
			)
		)
		.onClose_({scanner.addDependant( this );})
		.front;
	}

	update {
		| who, what ... args |

		if( who == scanner, {
			switch ( what,

				\grnDur, {
					controls.grnDur.numBox.value_(args[0]);
					controls.grnDur.knob.value_(scanner.grnDurSpec.unmap(args[0])); },
				\grnRate, {
					controls.grnRate.numBox.value_(args[0]);
					controls.grnRate.knob.value_(scanner.grnRateSpec.unmap(args[0])); },
				\grnRand, {
					controls.grnRand.numBox.value_(args[0]);
					controls.grnRand.knob.value_(scanner.grnRandSpec.unmap(args[0])); },
				\pntrRate, {
					controls.pntrRate.numBox.value_(args[0]);
					controls.pntrRate.knob.value_(scanner.pntrRateSpec.unmap(args[0])); },
				\grnDisp, {
					controls.grnDisp.numBox.value_(args[0]);
					controls.grnDisp.slider.value_(scanner.grnDispSpec.unmap(args[0])); },

				\minDisp, {
					controls.minDisp.numBox.value_(args[0]) },
				\maxDisp, {
					controls.maxDisp.numBox.value_(args[0]) },
				\density, {
					controls.density.numBox.value_(args[0]);
					controls.density.knob.value_(scanner.densitySpec.unmap(args[0])); },
				\fluxRate, {
					controls.fluxRate.numBox.value_(args[0]);
					controls.fluxRate.knob.value_(scanner.fluxRateSpec.unmap(args[0])); },
				\amp, {
					controls.amp.numBox.value_(args[0].ampdb);
					controls.amp.slider.value_(scanner.ampSpec.unmap(args[0].ampdb)); },
			)
		});
	}
}

/* Usage
var p;
p = "/Users/admin/src/rover/data/AUDIO/discovery_cliffside_clip.WAV"
g = GrainScanner(0, p)
g.play
g.gui
g.scanRange(rand(g.bufDur), 1)
g.free

i = GrainScanner(0, g.buffers)
i.play
i.gui
i.scanRange(rand(g.bufDur), 1)

q = 4.collect{GrainScanner(0, g.buffers)}
q.do(_.gui)
q.do(_.free)

Need to incorporate multiple pointer locations based on clustered frames in stochastic granulation, with distance from centroid controlled by GuasTrig or something similar.

*/