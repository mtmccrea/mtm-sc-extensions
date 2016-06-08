SiteMaterialStream {
	var <foleyDir, <initGroup, <server, <loadCond;
	var <group, <outbusses, <synthdef, <notes, <isPlaying = false, <whichbuffersArr, <>autoBufSwitch;
	var <buffers_for_conv, <buffers, <buffers_no_conv, <sf_names_for_conv, <sf_names_no_conv, <streamRoutines, <>switchRate = 0.1, <amp=0.4;

	// busnums an array of hardware input busses to create a playback stream from
	*new { |argFoleyDir, group, server, loadCond|
		^super.newCopyArgs(argFoleyDir, group, server, loadCond).init;
	}

	init {
		var cond;
		fork {
			cond = Condition(false);
			server = server ?? Server.default;
			foleyDir = foleyDir ?? {Platform.resourceDir ++ "/sounds/suyama_foley_mono/"};
			outbusses = [];
			notes = [];
			whichbuffersArr = [];
			autoBufSwitch = true;

			group = initGroup.notNil.if(
				{CtkGroup.play(target: initGroup)},
				{CtkGroup.play(addAction: \head, server: server)}
			);
			server.sync;

			synthdef = CtkSynthDef( \material_grn, {
				arg outbus=0, sndbuf, bufnum, grn_rate = 3, grn_dur=0.3, amp = 1,
				randPtr=0, randPtr_rate = 1.273, dust_trig=0, playback_rate=1, fadein = 2, fadeout = 2,
				freezePntr=0, roffset=0.0, nudge=0, add_rand_dur=0.25, gate=1;
				var env, bufg, pos, trig, sndout;
				env = EnvGen.kr( Env( [0,1,0],[fadein, fadeout], \sin, 1 ), gate, doneAction: 0 );
				pos = Select.kr( randPtr, [
					// linearly advancing pointer
					LFSaw.kr(BufDur.kr(bufnum).reciprocal, 1).range(0,1), // linear 0>1
					LFDNoise1.kr(randPtr_rate).range(0,1)
				]);
				// support for freezing the pointer if desired
				pos = Select.kr(freezePntr, [ pos, Latch.kr( pos, freezePntr ) ]);
				pos = ( pos +
					LFNoise1.kr(20).range(roffset.neg / BufDur.kr(bufnum), 0) + nudge
				).wrap(0,1);

				trig = Select.kr(dust_trig, [ Impulse.kr(grn_rate), Dust.kr(grn_rate) ]);
				bufg = BufGrain.ar(
					trig,
					// add_rand_dur is a percent of variance in the grain dur, keep low
					grn_dur * LFNoise1.kr(20).range(1, (1+add_rand_dur)),
					sndbuf, playback_rate, pos,
					interp:2
				);
				sndout = bufg * env;
				Out.ar( outbus, sndout * amp);
			});

			buffers_no_conv = IdentityDictionary(know: true);
			buffers_for_conv = IdentityDictionary(know: true);

			// note: no file extension
			sf_names_for_conv = [
				// "stone_light_intermittant",
				"stone_light_res",
				// "stone_light",
				"stone_rub_airy",
				// "stone_rub_light_unsteady",
				"stone_rub_res",
				"stone_rub_thin_siren",
				// "stone_soft",
				// "stone_thin",
				"stone_thin2",
				"turf",
				"wood_rub",
				"traffic",
				"ambient_underground_footfalls_ed",
				"ambient_underground_ed"
			];
			sf_names_no_conv = [
				"stone_heavy",
				"rummage_metal",
				"rummage_plastic",
				// "rummage_wood_long",
				"rummage_wood",
				"shuffle_lite_intermittant",
				"wood_plastic_claps_rubs",
				"footsteps_planks"
			];

			sf_names_for_conv.do({ |name|
				cond.test_(false);
				buffers_for_conv.put(name.asSymbol,
					CtkBuffer.playbuf(foleyDir++name++".wav").load(sync: true, onComplete: {cond.test_(true).signal});
				);
				cond.wait;
			});
			sf_names_no_conv.do({ |name|
				cond.test_(false);
				buffers_no_conv.put(name.asSymbol,
					CtkBuffer.playbuf(foleyDir++name++".wav").load(sync: true, onComplete: {cond.test_(true).signal});
				);
				cond.wait;
			});

			"\nDONE loading bufffs".postln;
			this.addMaterialStream(willconv:true); // this first stream will be convolved
		}
	}

	addMaterialStream { |willconv = true|
		fork {
			var outbus, note, initbuf, streamRoutine, notedex, whichbuffers;
			outbus = CtkAudio.play(1);
			server.sync;

			whichbuffers = if(willconv,
				{	buffers_for_conv },
				{	buffers_no_conv }
			);
			initbuf = whichbuffers.choose;

			note = synthdef.note(target: group)
			.outbus_(outbus).sndbuf_(initbuf).bufnum_(initbuf.bufnum)
			.grn_rate_(1).grn_dur_(2)
			.amp_(-8.dbamp)
			.randPtr_(0);

			notes = notes.add(note);
			notedex = notes.size-1;
			whichbuffersArr = whichbuffersArr.add(whichbuffers);

			// for automated buffer swapping
			streamRoutine = Routine({
				inf.do{
					var change, newbuf;
					change = [true, false].wchoose([0.7, 0.3]);
					if(change, {
						newbuf = whichbuffersArr[notedex].choose;
						notes[notedex].sndbuf_(newbuf).bufnum_(newbuf.bufnum);
					});
					rrand(switchRate.reciprocal*0.7,switchRate.reciprocal*1.3).wait;
				}
			});

			if(isPlaying, {
				server.sync;
				0.1.wait;
				note.play;
				streamRoutine.play;
			});

			outbusses = outbusses.add( outbus );
			streamRoutines = streamRoutines.add( streamRoutine );
			loadCond !? {loadCond.test_(true).signal}; // for external loading process
		};
	}

	play {
		fork {
			notes.do(_.play);
			server.sync;
			streamRoutines.do{ |r| r.reset; r.play; };
			isPlaying = true;
		}
	}

	stop {
		notes.do(_.free);
		streamRoutines.do(_.stop);
		isPlaying = false;
	}

	free {
		outbusses.do(_.free);
		group.freeAll;
		isPlaying = false;
		buffers_no_conv.keysValuesDo({|key, val| val.free }).clear;
		buffers_for_conv.keysValuesDo({|key, val| val.free }).clear;
		streamRoutines.do{|r| r.stop; r.clock.clear;};
	}

	amp_ { |ampDB|
		ampDB !? {
			amp = ampDB.dbamp;
			notes.do{|note| note.amp_(amp)};
		}
	}
}

/* scratch */

// streamRoutine = Routine({
// 	inf.do{
// 		var change, wait;
// 		//debug
// 		Main.elapsedTime.round.postln;
//
// 		change = [true, false].wchoose([0.7, 0.3]);
// 		change.if({
// 			//debug
// 			"changing material from stream routine: ".post; notedex.postln;
// 			//this.changeMaterial(notedex);
// 		},{"not changing".postln});
//
// 		wait = rrand(switchRate.reciprocal * 0.85, switchRate.reciprocal * 1.3);
// 		notedex.post; " waiting: ".post; wait.postln; "--".postln;
// 		wait.wait;
// 	}
// });

// changeMaterial { |which|
// 	which.notNil.if(
// 		// change individual
// 		{
// 			//debug
// 			"\tcalling changeBuf with".post; which.postln;
// 			this.prChangeBuf(which);
// 		},
// 		// change both, NOTE: expects just 2 streams in the case of Site Machine
// 		{
// 			//debug
// 			"\tcalling changeBuf twice; on both streams".post;
// 			2.do{|dex| this.prChangeBuf(dex)};
// 		}
// 	);
// }

// prChangeBuf { |which|
// 	var newbuf;
// 	newbuf = whichbuffersArr[which].choose;
// 	//debug
// 	"\t\tchanging to buf: ".post; newbuf.bufnum.postln;
// 	// newbuf = buffers.choose; // for single buffers list
// 	notes[which].sndbuf_(newbuf).bufnum_(newbuf.bufnum);
// }
//
// routineBufSwitch_{ |bool=true|
// 	autoBufSwitch = bool;
// 	if(bool,
// 		{ streamRoutines.do{|r|r.reset.play} },
// 		{ streamRoutines.do{|r|r.stop} }
// 	)
// }

/* test
~sms = SiteMaterialStream.new();
~sms.outbusses[0].bus
~sms.play

~sms.addMaterialStream
~sms.notes

~sms.stop
~sms.free

s.scope(2, 56)
*/