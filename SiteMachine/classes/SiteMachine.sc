/* todo
	-- create collection scheme for partials for access by other objects
	-- update fb delay to Resonz
	-- create panning control for fbdelay's "pingponging"

	-- figure out what happens to history read pointer when write pointer is paused
*/

SiteMachine {
	var <playbackGroup, <mstreamGroup, <convolveGroup, <fbdelayGroup, <historyGroup;
	var <playback, <mstream, <convolve, <fbdelay, <pextracts;
	var <loadCond, <server, <synthLib, <listeners, <lrSwitchBus, <historyGrainBus, <lrModBus, <toHistoryBus;
	var <lrGateSynth, <materialGateSynth, <showFXsynths, <lrMixerSynth, <lrModSynth, <showAllSynth;
	var <historyWriterSynths, <historyGrainSynths, <historyPointerSynth, <rescanMaterialSynths;
	var <historyDur, <historyBufL, <historyBufR, <history_rd_pntr, <history_wr_pntr;
	var <isLoaded=false, <grn_env1, <grn_env2, <historyDelBus1, <historyDelBus2;
	var <departRoutine, <fakeLRgateRoutine, <>historyStarted;
	// initialized when startHistory is called
	var departStart, timebreaks, gdur_env, grate_env, roffset_env, del_env, transition_env, pulsetail_env, <rescanMaterialsRoutine, <>rescanMaterialsSwapWait=5;

	*new {
		^super.new.init;
	}

	init {
		loadCond = Condition(false);
		fork {
			server = Server.local;

			// digital video switches
			thisProcess.openUDPPort(5002); // left video
			thisProcess.openUDPPort(5001); // right video

			// analog video switches
			thisProcess.openUDPPort(6662); // left video
			thisProcess.openUDPPort(6661); // right video

			playbackGroup =	CtkGroup.play(
				addAction: \head, server:server);
			mstreamGroup =	CtkGroup.play(
				addAction: \head, server:server);
			server.sync;

			convolveGroup =	CtkGroup.play(
				addAction: \tail, server:server);
			server.sync;

			historyGroup = CtkGroup.play(
				addAction: \after, target: convolveGroup, server:server);
			server.sync;

			fbdelayGroup =	CtkGroup.play(
				addAction: \after, target: historyGroup, server:server);
			server.sync;

			// history instrument stuff
			historyStarted = false;
			historyDur = (60*3.1).asInt;
			historyBufL = CtkBuffer.buffer(server.sampleRate * historyDur, numChannels:1 ).load(sync: true);
			historyBufR = CtkBuffer.buffer(server.sampleRate * historyDur, numChannels:1 ).load(sync: true);
			grn_env2 = CtkBuffer.env(512, Env([0, 1, 0], [0.05, 0.95], [3, 3])).load(sync: true);
			grn_env1 = CtkBuffer.env(512, Env([0, 1, 0], [0.5, 0.5], [-3,3])).load(sync: true);
			// Audio bus for the write and read pointers
			history_rd_pntr = CtkAudio.play(1);
			history_wr_pntr = CtkAudio.play(1);
			historyGrainBus = CtkAudio.play(2);
			// delay amount busses;
			historyDelBus1 = CtkControl.lfo(LFNoise2, 14.reciprocal, 0.0, historyDur).play;
			historyDelBus2 = CtkControl.lfo(LFNoise2, 14.reciprocal, 0.0, historyDur).play;

			// bus to write an amplitude level for each source during
			// "digital switching" mode of playback
			lrModBus = CtkControl.play(3);

			lrSwitchBus = CtkAudio.play(2);
			toHistoryBus = CtkAudio.play(2);

			server.sync;

			showFXsynths = Dictionary().putPairs([
				\conv, [], \fbdelay, []
			]);

			this.loadListeners;
			this.loadSynthDefs;

			// load playback/processing modules here
			playback = SitePlayback( inbusnums:(2..7),
				group:playbackGroup, server:server,loadCond:loadCond);
			loadCond.wait; "1 - playback loaded".postln;
			loadCond.test = false;

			mstream = SiteMaterialStream(group: mstreamGroup,
				server: server, loadCond:loadCond);
			loadCond.wait; "2 - material stream loaded".postln;
			loadCond.test = false;

			convolve = SiteConvolve(
				inbusnums:[
					playback.outbus.bus,	// input - all 6 cams read in a 6ch bus
					mstream.outbusses[0]],	// kernel - first material stream
				initAmp:1, group: convolveGroup, server: server, loadCond:loadCond
			);
			loadCond.wait; "3 - convolve loaded".postln;
			loadCond.test = false;

			fbdelay = SiteFBDelay(
				inbusnums: [historyGrainBus.bus,historyGrainBus.bus+1],
				initAmp:1, group: fbdelayGroup, server: server, loadCond:loadCond);
			loadCond.wait; "4 - fbdelay loaded".postln;
			loadCond.test = false;

			pextracts = 6.collect{|i|
				SitePartialExtract(
					playback.outbus.bus+i, // inbus
					pollRate:3, numBins:512, minSlope:0.1,
					fromFreq:100, toFreq:10000, server: server, loadCond:loadCond
				);	// this add to TAIL of Group 1
			};
			// not this signals true after just the first of the PartialExtracts are
			// loaded
			loadCond.wait; "5 - partial extract loaded".postln;
			loadCond.test = false;

			server.sync;

			// prepare synths
			lrGateSynth = synthLib[\lrSrcSwitch].note(
				addAction: \after, target: playbackGroup, server: server)
			.fadein_(6)
			.inbus_(playback.outbus)
			.ampbus_(lrModBus.bus)
			.outbus_(lrSwitchBus)
			.historybus_(toHistoryBus);

			// synth keeping track of where the writer writes in the buffer, cyclical
			historyPointerSynth = synthLib[\history_pointer].note(target: historyGroup).bufnum_(historyBufL.bufnum).wrphasor_bus_(history_wr_pntr.bus).rphasor_bus_(history_rd_pntr.bus);

			// synths writing audio to L and R buffers
			historyWriterSynths = [ historyBufL, historyBufR ].collect{ |buf, i|
				synthLib[\history_writer_1ch].note(addAction: \after, target: historyPointerSynth)
				.inbus_(toHistoryBus.bus+i) // inbus are outs 0,1
				.wrphasor_bus_(history_wr_pntr.bus)
				.bufnum_(buf.bufnum)
			};

			historyGrainSynths =[ historyBufL, historyBufR ].collect{ |buf, i|
				synthLib[\history_bufgrn].note(
					addAction: \tail, target: historyGroup)
				.out_(historyGrainBus.bus+i)
				.phasor_bus_(history_rd_pntr.bus)
				.sndbuf_(buf).bufnum_(buf.bufnum)
				//fairly seemless real-time
				.dust_(0).freezePntr_(0).amp_(0.dbamp)
				.delay_([historyDelBus1, historyDelBus2].at(i))
				.grn_rate_(2.134127.reciprocal*3.2)
				.grn_dur_(2.0)
				.envbuf1_(grn_env1).envbuf2_(grn_env2).ifac_(0)
			};
			// material grains for during rescan
			rescanMaterialSynths = 2.collect{ |i|
				var initbuf;
				initbuf = mstream.buffers_no_conv.choose;
				mstream.synthdef.note(addAction: \before, target: historyGroup)
				.outbus_(i) //straight out
				.sndbuf_(initbuf).bufnum_(initbuf.bufnum)
				.grn_rate_(14)
				.grn_dur_(6)
				.roffset_(2.0)
				.dust_trig_(1)
				.randPtr_(1)
				.amp_(-8.dbamp)
			};
			// add 2 more for fbdelay to read in
			2.do{ |i|
				var initbuf;
				initbuf = mstream.buffers_for_conv.choose;
				rescanMaterialSynths.add(
					mstream.synthdef.note(addAction: \before, target: historyGroup)
					// same as grain output so fbdelay can access
					.outbus_(historyGrainBus.bus+i)
					.sndbuf_(initbuf).bufnum_(initbuf.bufnum)
					.grn_rate_(14)
					.grn_dur_(6)
					.roffset_(2.0)
					.dust_trig_(1)
					.randPtr_(1)
					.amp_(-8.dbamp)
				)
			};

			lrMixerSynth = synthLib[\lrMixer].note(
				addAction:\after, target: historyGroup)
			.sterinbus1_(lrSwitchBus).sterinbus2_(historyGrainBus).whichsrc_(0)
			.outbus_(0);

			lrModSynth = synthLib[\lrModControl].note.outbus_(lrModBus).modfreq_(15.reciprocal);

			showAllSynth = synthLib[\showAll].note(addAction:\after, target: playbackGroup).amps_([1,1,1,1,1,1]).inbus_(playback.outbus).outbus_(0);

			isLoaded = true;

			this.playModules;
		}
	}

	loadListeners {
		listeners = [

			OSCdef(\digitalL, { |msg, time, addr, recvPort|
				"left digital switched: ".post;
				lrGateSynth.lsrc_(msg[1].postln);
				}, '/video2', nil, 5002
			),
			OSCdef(\digitalR, { |msg, time, addr, recvPort|
				"right digital switched: ".post;
				lrGateSynth.rsrc_(msg[1].postln);
				}, '/video1', nil, 5001
			),
			OSCdef(\analogL, { |msg, time, addr, recvPort|
				"left analog switched: ".post; msg[1].postln;
				this.pulseResonance(0)
				}, '/analog2', nil, 6662
			),
			OSCdef(\analogR, { |msg, time, addr, recvPort|
				"right analog switched".post; msg[1].postln;
				this.pulseResonance(1)
				}, '/analog1', nil, 6662
			),
			OSCdef(\rescan, { |msg, time, addr, recvPort|
				"RESCAN: ".post; msg[1].postln;
				switch( msg[1].asInt,
					1, { "starting rescan".postln;
						// showing
						this.showAll(ampDB:7, amps: [1,1,1,1,1,1], fadein: 0.001);
						this.showFBDelay(fadein:0.005);

						// FBDELAY SETTINGS
						fbdelay.notes.do{ |note| note
							.attack_(0.01)
							.delay_(rrand(0.36, 0.43))	//narrower range initial value
						};
						fbdelay
						.fbamt_(amount:0.71)
						.bw_(bw:430)
						.fbdecaytime_(fbdecaytime:4.5)
						.amp_(0)
						.sendPulse(fadeout:5);

						// GRAIN SETTINGS
						historyGrainSynths.do{ |grnsynth|
							grnsynth
							.grn_rate_(22.5).grn_dur_(0.261)
							.roffset_(10.1).ifac_(0.68)
							.dust_(0).freezePntr_(0).amp_(0.dbamp)
						};
						//synth.grn_rate_(14.5).grn_dur_(0.62)
						// .roffset_(1.1).ifac_(0.4).dust_(1)
						// .freezePntr_(0).amp_(0.dbamp);

						// hiding
						this.hideLRgate(fadeout: 0.05);
						this.hideConv(fadeout:0.05);
						this.hideMaterial(fadeout:0.05);

						rescanMaterialSynths.do{|synth|
							synth.fadein_(2)
							.grn_rate_(14)
							.grn_dur_(1.6)
							.roffset_(2.0)
							.dust_trig_(1)
							.randPtr_(1)
							.amp_(-12.dbamp);

							if(synth.isPlaying,
								{ synth.gate_(1) },
								{ synth.play }
							);
						};

						this.startHistory( duration:90 );
					},
					0, { // return to regular mode
						var xfade;
						xfade = 5;
						this.hideAll(fadeout: xfade); // note fadeOUT param set to xfade
						this.showLRgate(fadein: xfade);
						this.showMaterial(fadein: xfade);
						this.showConv(fadein: xfade);

						this.hideFBDelay(fadeout:20); // give fbDelay it's own tail
						rescanMaterialSynths.do{|synth|
							synth.fadeout_(xfade).release;
						};
						// transition to 0 over xfade in 'steps' iterations
						fork {
							var steps;
							steps = 20;
							steps.do{|i|
								lrMixerSynth.whichsrc_(1-(i+1/steps));
								(xfade / steps).wait;
							};
						};

						this.stopHistory;
					}
				)
				}, '/rescan', nil, 6014
			),
		]
	}

	playModules {
		fork {
			[playback, mstream, convolve, fbdelay].do(_.play);
			0.5.wait;
			server.sync;

			// stop the routine that randomly switches material buffers

			// add a material stream for 2 initial streams
			mstream.addMaterialStream(willconv:false); // add a stream
			server.sync;

			historyPointerSynth.play;
			server.sync;

			// synths writing audio to L and R buffers
			historyWriterSynths.do(_.play);
			lrModSynth.play;
			server.sync;
			historyGrainSynths.do(_.play);
			lrMixerSynth.play;

			// start partial extractors
			pextracts.do(_.play);

			rescanMaterialsRoutine = Routine.run({
				inf.do{
					rescanMaterialSynths.do{|synth, i|
						var newbuf;
						newbuf = if(i<2,
							{mstream.buffers_no_conv.choose},	// straight out
							{mstream.buffers_for_conv.choose}	// for fbdelay
						);
						synth.sndbuf_(newbuf).bufnum_(newbuf.bufnum)
					};
					rrand(rescanMaterialsSwapWait*0.8, rescanMaterialsSwapWait*1.2).wait;
				}
			});
		}
	}

	mute_{ |bool=true|
		if(bool,
			{},
			{}
		);
	}

	showLRgate { |ampDB, ampminDB, ampmaxDB, fadein=5|
		ampDB !? { lrGateSynth.amp_(ampDB.dbamp) };
		ampminDB !? { lrGateSynth.ampmin_(ampminDB.dbamp) };
		ampmaxDB !? { lrGateSynth.ampmax_(ampmaxDB.dbamp) };
		if(isLoaded && lrGateSynth.isPlaying.not, {
			lrGateSynth.fadein_(fadein);
			lrGateSynth.play;
		},{"lrGateSynth already running or SiteMachine not yet loaded".warn})
	}

	hideLRgate { |fadeout=0.5|
		fadeout !? {lrGateSynth.fadeout_(fadeout)};
		lrGateSynth.release;
	}

	showMaterial { |ampDB, ampminDB, ampmaxDB, fadein=5|
		fork {
			materialGateSynth.isNil.if( {
				materialGateSynth = synthLib[\showMaterial].note(
					addAction: \tail, target: mstreamGroup)
				.outbus_(0)
				.inbus1_(mstream.outbusses[0])
				.inbus2_(mstream.outbusses[1])
				.rotFreq_(6.reciprocal)
				.ampbus_(lrModBus.bus+1)
				.fadein_(fadein)
				.play
				},{
					materialGateSynth.fadein_(fadein).gate_(1);
			});
			server.sync;
			ampDB !? {materialGateSynth.amp_(ampDB.dbamp)};
			ampminDB !? { materialGateSynth.ampmin_(ampminDB.dbamp) };
			ampmaxDB !? { materialGateSynth.ampmax_(ampmaxDB.dbamp) };
		}
	}

	hideMaterial { |fadeout=2|
		materialGateSynth.fadeout_(fadeout).release; // note: releases gate, doesn't free
	}

	showConv { |which, ampDB, ampminDB, ampmaxDB, fadein = 5|
		fork {
			var alreadyPlayingBusNums;
			alreadyPlayingBusNums = showFXsynths[\conv].collect{|showsynth|
				showsynth.inbus.bus;
			};
			// show all
			which.isNil.if({
				convolve.outbusses.do{|outbus|
					if( alreadyPlayingBusNums.includes(outbus.bus).not, {
						showFXsynths[\conv] = showFXsynths[\conv].add(
							synthLib[\showConv].note(
								addAction: \tail, target: convolveGroup)
							.outbus_(0).inbus_(outbus)
							.fadein_(fadein)
							.ampbus_(lrModBus.bus+2)
							.play;
						)
						}
					);
				};
				server.sync;
				showFXsynths[\conv].do{ |synth|
					ampDB !? {synth.amp_(ampDB.dbamp)};
					ampminDB !?	{ synth.ampmin_(ampminDB.dbamp) };
					ampmaxDB !?	{ synth.ampmax_(ampmaxDB.dbamp) };
				};
				// show individual
				},{
					var synth;
					if( ( which <= (convolve.outbusses.size-1) ) &&
						( alreadyPlayingBusNums.includes(convolve.outbusses[which].bus).not ),
						{
							synth = synthLib[\showConv].note(
								addAction: \tail, target: convolveGroup)
							.outbus_(0).inbus_(convolve.outbusses[which])
							.fadein_(fadein)
							.ampbus_(lrModBus.bus+2)
							.play;
							showFXsynths[\conv] = showFXsynths[\conv].add( synth );
							server.sync;
							ampDB !? {synth.amp_(ampDB.dbamp)};
							ampminDB !?	{ synth.ampmin_(ampminDB.dbamp) };
							ampmaxDB !?	{ synth.ampmax_(ampmaxDB.dbamp) };
						},{
							"not a valid 'which' conv index".postln;
						}
					);
				}
			)
		}
	}

	hideConv { |which, fadeout=2|
		var rmdex;
		// TODO inplement which
		rmdex = showFXsynths[\conv].size.rand;
		showFXsynths[\conv][rmdex] !? {
			showFXsynths[\conv][rmdex].fadeout_(fadeout).release;
			showFXsynths[\conv].removeAt(rmdex);
		}
	}

	showFBDelay { |which, fadein = 0.5, ampDB = 0|
		var alreadyPlayingBusNums;
		alreadyPlayingBusNums = showFXsynths[\fbdelay].collect{|showsynth|
			showsynth.inbus.bus;
		};
		which.isNil.if({
			// show all
			fbdelay.outbusses.do{|outbus|
				if( alreadyPlayingBusNums.includes(outbus.bus).not, {
					showFXsynths[\fbdelay] = showFXsynths[\fbdelay].add(
						synthLib[\showFBDelay].note(
							addAction: \tail, target: fbdelayGroup)
						.outbus_(0).inbus_(outbus)
						.fadein_(fadein).amp_(ampDB.dbamp)
						.play;
					)
				})
			};
			// show individual
			},{
				if( ( which <= (fbdelay.outbusses.size-1) ) &&
					( alreadyPlayingBusNums.includes(fbdelay.outbusses[which].bus).not ),{
						var inbus;
						inbus = fbdelay.outbusses[which].bus;

						showFXsynths[\fbdelay] = showFXsynths[\fbdelay].add(
							synthLib[\showFBDelay].note(
								addAction: \tail, target: fbdelayGroup)
							.outbus_(0).inbus_(inbus)
							.fadein_(fadein).amp_(ampDB)
							.play;
						)
					},{
						"not a valid 'which' fbdelay index".postln;
					}
				)
			}
		)
	}

	hideFBDelay { |which, fadeout = 12|
		var rmdex;
		// TODO inplement which
		which.notNil.if({
			rmdex = showFXsynths[\fbdelay].size.rand;
			showFXsynths[\fbdelay][rmdex].fadeout_(fadeout).release;
			showFXsynths[\fbdelay].removeAt(rmdex);
			},{
				showFXsynths[\fbdelay].do{|note|
					note.fadeout_(fadeout).release;
				};
				showFXsynths[\fbdelay] = [];
			}
		)
	}

	// NOTE: amps are 0>1
	showAll { |ampDB, amps, fadein = 0.001 |
		ampDB !? {showAllSynth.amp_(ampDB.dbamp)};
		amps !? {showAllSynth.amps_(amps)};
		showAllSynth.fadein_(fadein).play;
	}

	hideAll{ |fadeout|
		fadeout !? {showAllSynth.fadeout_(fadeout)};
		showAllSynth.release;
	}

	// History buffer methods
	pauseHistoryWriter {
		fork {
			// pause the writer
			historyWriterSynths.do{|synth|
				synth.release; // close the amp gate
				0.1.wait;
				synth.pause;
			};
		}
	}
	resumeHistoryWriter {
		historyWriterSynths.do{|synth|
			synth.run;
			synth.gate_(1);
		};
		// wait before doing this?
		historyPointerSynth.t_reset_rd_(1); // bring rd pos back to wr pos
	}

	startHistory { |duration = 120|

		departStart = Main.elapsedTime;
		historyStarted = true;

		// GRAIN envelopes
		timebreaks = [0.2,0.35,0.25,0.15, 0.05].normalizeSum * duration;
		transition_env = Env([0,1.0], [2], 2); // transition to grain instrument
		gdur_env =  Env([0.8,0.5,0.342,0.242,0.12382,0.06382], timebreaks, 3);
		grate_env = Env([2.4,6,12,15,21,75], timebreaks, 'lin');
		roffset_env = Env([1,3,4.5,9,12,16], timebreaks, 'lin');
		del_env = Env([historyDur,0], duration, -2);
		pulsetail_env = Env([2,5], duration, 2);

		// stop the writing pointer
		this.pauseHistoryWriter;

		departRoutine !? { departRoutine.stop };
		// for use with analog source switch OSC trigger method
		departRoutine = Routine.run({
			inf.do{
				var now;
				now = Main.elapsedTime - departStart;
				lrMixerSynth.whichsrc_(transition_env[now]);
				0.5.wait;
			}
		});

	}

	pulseResonance { |which=0|
		var synth;
		// for REPLACING resonance
		which.asArray.do{ |i|
			var pklib, freqs, amps;
			pklib = pextracts.choose.peaklib;
			if(pklib.size > 0, {
				var pk;
				pk = pklib.choose;
				fbdelay.newResonance(which: i,
					freqs: (pk[0]*rrand(1.0, 4.7)).clip(20, 20000),
					amps: pk[1],
					pulsetail: 7,
					echodist: rrand(0.16, 0.7) // time between echoes
				);
				//debug
				"new resonance:  ".post; i.postln;
				},{
					fbdelay.sendPulse(fadeout:5); // pulse the running synth
					"Defaulting to pulsing the init freqs in fbdelay".warn;
					"NO PEAKS in peaklib: ".post; pklib.postln;
			})
		};
		//debug
		"replaced resonance".postln;
	}

	stopHistory {
		historyStarted = false;
		this.resumeHistoryWriter;
		departRoutine.stop;
	}

	free {
		fork {
			[ 	playback, mstream, convolve, fbdelay,
				lrGateSynth, lrMixerSynth, lrModSynth, showAllSynth
			].do(_.free);

			pextracts.do(_.free);
			0.1.wait;

			[ 	playbackGroup, mstreamGroup, convolveGroup,
				fbdelayGroup, historyGroup
			].do(_.free);

			[	historyBufL, historyBufR, history_rd_pntr, history_wr_pntr,
				lrSwitchBus, historyGrainBus, grn_env1, grn_env2,
				historyDelBus1, historyDelBus2, lrModBus, toHistoryBus
			].do(_.free);
			rescanMaterialSynths.do(_.free);
			listeners.do(_.free);

			rescanMaterialsRoutine.stop;
			departRoutine !? {departRoutine.stop};
		};
	}

	// for testing with pre-recorded files as inputs and no OSC messages
	fakeLRgateSwitch { |bool=true|
		if(bool,
			{
				fakeLRgateRoutine = Routine.run({
					var action;
					inf.do{
						action = [0,1,2].wchoose([0.25,0.25,0.5]);
						switch(action,
							0,{	this.lrGateSynth.lsrc_([0,1,2].choose) }, // switch left
							1,{ this.lrGateSynth.rsrc_([0,1,2].choose) }, // switch right
							2,{ // switch both
								this.lrGateSynth.lsrc_([0,1,2].choose);
								this.lrGateSynth.rsrc_([0,1,2].choose);
							}
						);
						rrand(5, 10.0).wait;
					}
				})
			},{
				fakeLRgateRoutine !? {fakeLRgateRoutine.stop}
		})
	}
}


/* original
fbdelay.fbamt_(amount:0.55);
fbdelay.bw_(bw:330);
fbdelay.amp_(6);
fbdelay.fbdecaytime_(fbdecaytime:7);*/

/* Removed from stopHistory
// return to fairly seemles playback (his is "hidden" at this point)
historyGrainSynths.do{ |synth| synth
.dust_(0).freezePntr_(0).amp_(-3.dbamp) //.delay_(0.0)
.grn_rate_(2.134127.reciprocal*3.2).grn_dur_(2.0).roffset_(0)
};*/

/*  From startHistory
// for use as dummy analog switch source (non-OSC triggered)
departRoutine = Routine.run({
inf.do{
var now, del, rate, dur, roff;
now = Main.elapsedTime - departStart;

lrMixerSynth.whichsrc_(transition_env[now]);

// fixed grain behavior for now
historyGrainSynths.do{ |synth, i|
synth.grn_rate_(14.5).grn_dur_(0.62).roffset_(1.1)
.ifac_(0.7).dust_(1).freezePntr_(0).amp_(-6.dbamp);
};

// REPLACE resonant frequency
2.do{ |i|
var pklib, freqs, amps;
pklib = pextracts.choose.peaklib;
if(pklib.size > 0, {
var pk;
pk = pklib.choose;
//debug
postf("~~~~~~\nchosen peaks: %\n",pk);

fbdelay.newResonance (i,
pk[0], // freqs
pk[1],  // amps
pulsetail_env[now] // pulsetail
)
},{ // no peaks found
"no peaks found	- defaulting".warn;
fbdelay.sendPulse(fadeout: pulsetail_env[now]);
})
};


// // use below loop for transitioning parameters for
// // grain behavior over 'duration'
// historyGrainSynths.do{ |synth, i|
// 	fbdelay.delay_(i, rrand(0.08, 0.3102).postln);
//
// 	del = rrand(0,del_env[now]);
// 	rate = grate_env[now];
// 	dur = gdur_env[now];
// 	roff = roffset_env[now];
// 	// sswpfrq = sweepfrq_env[now];
//
// 	synth
// 	// .delay_( del ) // using historyDelBus instead
// 	.grn_rate_( rate )
// 	.grn_dur_( dur )
// 	.roffset_( roff )
// 	.freezePntr_( 0 ) // [0,1].choose
// 	.dust_(0) // [0,1].choose
// };
//
// // update resonant frequency of already-running echo
// 2.do{ |i|
// 	var pklib, freqs, amps;
// 	pklib = pextracts.choose.peaklib;
// 	if(pklib.size > 0, {
// 		var pk;
// 		pk = pklib.choose;
// 		fbdelay.resonances_(i,
// 			pk[0], // freqs
// 			pk[1]  // amps
// 		)
// 	})
// };
//
// fbdelay.sendPulse(fadeout: pulsetail_env[now]);
// // debug
// postf("now: % (%)\n delay: %\n g_rate: %\n g_dur %\n roffset: %\n\n",
// now, now/duration, del, rate, dur, roff);

rrand(4.0, 7.0).wait;
}
});
*/

/* From pulseResonance
// for updating params in existing resonance
// update the echo distance in resonance
fbdelay.delay_(which, rrand(0.1, 0.3102).postln);
pklib = pextracts.choose.peaklib;
if(pklib.size > 0, {
var pk;
pk = pklib.choose;
fbdelay.resonances_(which, pk[0], pk[1])
});
*/

// Deprecated: no longer updating the grains with analog switch
/*updateGrain { |which=0|
var now, del, rate, dur, roff, synth;
synth = historyGrainSynths[which];

now = Main.elapsedTime - departStart;
del = rrand(0,del_env[now]);
rate = grate_env[now];
dur = gdur_env[now];
roff = roffset_env[now];

synth.delay_( del ).grn_rate_( rate ).grn_dur_( dur ).roffset_( roff )
.freezePntr_( 0 ) // [0,1].choose
.dust_(0); // [0,1].choose
fbdelay.sendPulse(fadeout: pulsetail_env[now]);
// debug
postf("now: %\n delay: %\n g_rate: %\n g_dur %\n roffset: %\n\n",
now, del, rate, dur, roff
);
}*/

/*  test
s.boot;
s.scope;
~sm = SiteMachine()
~sm.isLoaded

~sm.startLRgate
~sm.free

[~sm.playback, ~sm.mstream, ~sm.convolve, ~sm.fbdelay].do(_.play)

s.scope(2, ~sm.mstream.outbusses[0].bus)

~sm.playbackGroup
~sm.playback
~sm.mstream
var <playbackGroup, <mstreamGroup, <convolveGroup, <fbdelayGroup;
var <playback, <~sm.mstream, <convolve, <fbdelay, <pextract;

~sp = SitePlayback(inbusnums:(2..7));
~sp.inbusnums
*/