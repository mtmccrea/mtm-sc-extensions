SiteFBDelay {
	var <initInbusnums, <amp, <initGroup, <server, <loadCond;
	var <isPlaying = false, <numconvs, <group, <synthdef;
	var <inbusnums, <outbusses,  <notes, <numres;

	// busnums an array of input busses
	*new {|inbusnums, initAmp=1, group, server, loadCond|
		^super.newCopyArgs(inbusnums.asArray, initAmp, group, server, loadCond).init;
	}

	init {
		fork {
			server = server ?? Server.default;

			group = initGroup.notNil.if(
				{CtkGroup.play(target: initGroup)},
				{CtkGroup.play(addAction: \tail, server: server)}
			);

			inbusnums = [];
			outbusses = [];
			notes = [];
			numres = 9;

			/*// original
			synthdef = CtkSynthDef(\fbdelay, { arg outbus=0, inbus,
				bw = 1500, spread=2, startpan= -1, t_gate,
				pulsetail = 1.2, // tail of pulse
				reltime=2.8, // fade time when synth released
				fbdecaytime = 5, // decay on the feedback amount to avoid ringing
				amp = 1, limamp = 0.5, attack=0.001,
				hpf_freq=130, fb_amt=0, delay=0.01, gate=1,
				minpan = -0.5, maxpan = 0.5, sprd_env,
				pitchDriftFreq=0.0833, freqshift = 5;
				// wSize=0.2, pRatio=0.1, pDispersion=0.01, tDispersion=0.05;

				var source, env, local, rfreqs, ramps, sndout, pan, fbdecay;
				// env just used for freeing
				env = EnvGen.kr(
					Env([1, 1, 0], [0.1, reltime], \sin,  releaseNode:1),
					gate, doneAction: 2
				);

				source = InFeedback.ar(inbus,1);// source = In.ar(inbus,1);
				// envelope/gate the input with a trigger
				source = source * EnvGen.kr(
					Env([0, 1, 0], [attack, pulsetail], [-3,1.5],  loopNode:1),
					t_gate, doneAction: 0
				);

				fbdecay = EnvGen.kr(
					Env([1,1, 0], [0.01, fbdecaytime], [1, 1.5],  loopNode:1),
					t_gate, doneAction: 0
				);

				/* feedbacknetwork */
				local = LocalIn.ar(2) + [source, 0]; // read feedback, add to source
				local = DelayL.ar(local, 0.9, delay); // delay sound
				rfreqs = Lag.kr( Control.names([\resfreqs]).kr(
					[ 56.23, 221.83, 447.73, 568.91, 678.17, 900.92, 1123.47, 2023.22, 2244.35 ]
				));
				ramps = Lag.kr( Control.names([\resamps]).kr(
					numres.collect{numres.reciprocal}
				));
				local = [
					Mix.ar( RLPF.ar(local[0], rfreqs, bw/rfreqs, ramps) ),
					Mix.ar( RLPF.ar(local[1], rfreqs, bw/rfreqs, ramps) );
				];
				local = FreqShift.ar(local, LFDNoise3.kr(pitchDriftFreq) * freqshift);
				/*local = PitchShift.ar(
				local, windowSize: wSize,
				pitchRatio: LFDNoise3.kr(pitchDriftFreq) * pRatio + 1,
				pitchDispersion: pDispersion, timeDispersion: tDispersion
				);*/
				local = LeakDC.ar(local);
				local = HPF.ar(
					Limiter.ar(local, limamp), hpf_freq
				);
				// reverse channels to give ping pong effect, apply decay factor
				LocalOut.ar( local.reverse *
					Lag.kr(fb_amt.clip(0.0, 0.9)) * fbdecay
				);

				sprd_env = EnvGen.kr(
					Env([0,0, 1], [0.01, fbdecaytime*0.83], \sin,  loopNode:1),
					t_gate, doneAction: 0
				);
				// spreader
				sndout = Mix.ar([
					Pan2.ar(local[0], startpan),
					Pan2.ar(local[1], startpan+((spread*startpan.neg) * sprd_env))
				]);
				// pan = LFDNoise3.kr(0.36).range(minpan, maxpan); // drift in panning
				// sndout = Mix.ar([
				// 	Pan2.ar(local[0], pan+(spread * -0.5)),
				// 	Pan2.ar(local[1], pan+(spread * 0.5))
				// ]);
				Out.ar(outbus, sndout * amp * env);
			});*/



			// hoping to use this one
			synthdef = CtkSynthDef(\fbdelay, { arg outbus=0, inbus,
				bw = 1500, spread=2, t_gate,
				pulsetail = 1.2, // tail of pulse
				reltime=3.8, // fade time when synth released
				fbdecaytime = 5, // decay on the feedback amount to avoid ringing
				amp = 1, limamp = 0.5, attack=0.01, dirsrc_att = 0.01,
				hpf_freq=130, fb_amt=0, delay=0.01, gate=1,
				pitchDriftFreq=0.0833, freqshift = 10,
				startpan = -1, dirsrc_amt = 0.5;

				var source, env, local, localfx, rfreqs, ramps, sndout, pan, fbdecay, source_dirout, sprd_env;
				// env just used for freeing
				env = EnvGen.kr( Env([1, 1, 0], [0.1, reltime], \sin,  releaseNode:1), gate, doneAction: 2 );

				source = In.ar(inbus,1); //InFeedback.ar(inbus,1); // source = In.ar(inbus,1);
				source_dirout = source * EnvGen.kr(
					Env([0, 1, 0], [attack, pulsetail*0.5], [-3,\sin],  loopNode:1),
					t_gate, doneAction: 0
				);
				// envelope/gate the input with a trigger
				source = source * EnvGen.kr(
					Env([0, 1, 0], [attack, pulsetail], [-3,\sin],  loopNode:1),
					t_gate, doneAction: 0
				);

				fbdecay = EnvGen.kr(
					Env([1,1, 0], [0.01, fbdecaytime], \sin,  loopNode:1),
					t_gate, doneAction: 0
				);
				sprd_env = EnvGen.kr(
					Env([0,0, 1], [0.01, fbdecaytime*0.7], \sin,  loopNode:1),
					t_gate, doneAction: 0
				);

				/* feedbacknetwork */
				local = LocalIn.ar(2) + [source, 0]; // read feedback, add to source

				local = DelayL.ar(local, 1.5, [delay, delay * 0.27571]); // delay sound
				rfreqs = Lag.kr( Control.names([\resfreqs]).kr(
					[ 56.23, 221.83, 447.73, 568.91, 678.17, 900.92, 1123.47, 2023.22, 2244.35 ]
				));
				ramps = Lag.kr( Control.names([\resamps]).kr( 9.collect{9.reciprocal} ));

				// // effect both channels
				// localfx = [
				// 	Mix.ar( RLPF.ar(local[0], rfreqs, bw/rfreqs, ramps) ),
				// 	Mix.ar( RLPF.ar(local[1], rfreqs, bw/rfreqs, ramps) )
				// ];
				// local = localfx;

				// effect only 1 channel + some source
				localfx = Mix.ar( RLPF.ar(
					local[1] + (source * -16.dbamp), // filter right side + some source
					rfreqs, bw/rfreqs, ramps)
				);

				// local = [local[0], localfx]; //add filtering back in
				local = local + [0, localfx]; //add filtering back in
				// local = FreqShift.ar(local, LFDNoise3.kr(pitchDriftFreq) * freqshift);
				local = LeakDC.ar(local);
				local = HPF.ar(
					Limiter.ar(local, limamp), hpf_freq
				);
				// reverse channels to give ping pong effect, apply decay factor
				local = local.reverse * Lag.kr(fb_amt.clip(0.0, 0.9)) * fbdecay;
				LocalOut.ar( local );

				// spreader
				sndout = Mix.ar([
					Pan2.ar(local[0], startpan),
					Pan2.ar(local[1], startpan+((spread*startpan.neg) * sprd_env))
				]);
				Out.ar(outbus,
					(sndout + [source_dirout*dirsrc_amt, 0]) * amp
				);
				/*sndout = Mix.ar([
				Pan2.ar(local[0]+(source_dirout*dirsrc_amt), startpan),
				Pan2.ar(local[1], startpan+((spread*startpan.neg) * sprd_env))
				]);

				Out.ar(outbus,
				Limiter.ar(local, sndout, limamp) * amp * env
				);*/
			});

			server.sync;
			this.addFBDelays(initInbusnums);
		}
	}

	addFBDelays { |inbusses|
		fork {
			var newins, newoutbusses, newnotes;
			newins = inbusses;
			newoutbusses = newins.size.collect{ CtkAudio.play(2) };

			// hoping to use this one...
			newnotes = newins.collect{ |inbus, i|
				synthdef.note(target: group)
				.inbus_(inbus).outbus_(newoutbusses[i].bus)
				.fb_amt_(0.7)
				.delay_(0.2)
				.fbdecaytime_(7)
				.attack_(0.001)
				.bw_(320)
				.spread_(2)
				.pulsetail_(4.5)
				.amp_(amp)
				.limamp_(0.4) // -8.dbamp
				.hpf_freq_(100)
				.startpan_(-1 + (2*i));
			};

			/*//original
			newnotes = newins.collect{ |inbus, i|
				synthdef.note(target: group)
				.inbus_(inbus).outbus_(newoutbusses[i].bus)
				.fb_amt_(0).delay_(0.01)
				.bw_(500)
				.pulsetail_(1.2).amp_(amp)
				.limamp_(0.15) // -16.5.dbamp
				.hpf_freq_(100)
				.freqshift_(5)
				.maxpan_((i%2 *1))
				.minpan_((i%2 -1))
				.spread_(2)
				.startpan_(-1 + (2*i));
			};*/

			inbusnums = inbusnums ++ newins;
			outbusses = outbusses ++ newoutbusses;
			notes = notes ++ newnotes;

			if(isPlaying, {newnotes.do(_.play)});

			server.sync;
			loadCond !? {loadCond.test_(true).signal}; // for external loading process
		}
	}

	play {
		notes.do(_.play);
		isPlaying = true;
	}
	stop {
		notes.do(_.release);
		isPlaying = false;
	}

	amp_{ |ampDB|
		notes.do{|note| note.amp_(ampDB.dbamp)};
		amp = ampDB.dbamp;
	}

	// open the gate for source into the fbdelay
	sendPulse { |which, fadeout|
		which.notNil.if({
			if( which <= (notes.size-1),{
				fadeout !? {notes[which].pulsetail_(fadeout)};
				notes[which].t_gate_(1);
				},{ "'which' out of range".warn }
			)
			},{
				fadeout.notNil.if(
					{ notes.do{|nt| nt.pulsetail_(fadeout).t_gate_(1)} },
					{ notes.do{|nt| nt.t_gate_(1)} }
				);
			}
		);
	}

	fbamt_{ |which, amount|
		which.notNil.if({
				if( which <= (notes.size-1),
				{ notes[which].fb_amt_(amount.clip(0,0.9)) },
				{ "'which' out of range".warn }
			)
			},{ notes.do{|nt| nt.fb_amt_(amount.clip(0,0.9))} }
		);
	}

	delay_{ |which, deltime|
		which.notNil.if({
			if( which <= (notes.size-1),
				{ notes[which].delay_(deltime) },
				{ "'which' out of range".warn }
			)
			},{ notes.do{|nt| nt.delay_(deltime)} }
		);
	}

	bw_{ |which, bw|
		which.notNil.if({
			if( which <= (notes.size-1),
				{ notes[which].bw_(bw) },
				{ "'which' out of range".warn }
			)
			},{ notes.do{|nt| nt.bw_(bw)} }
		);
	}

	fbdecaytime_{ |which, fbdecaytime=5|
		which.notNil.if({
			if( which <= (notes.size-1),
				{ notes[which].fbdecaytime_(fbdecaytime) },
				{ "'which' out of range".warn }
			)
			},{ notes.do{|nt| nt.fbdecaytime_(fbdecaytime)} }
		);
	}

	fillResPeaks { |freqs, amps|
		var resfreqs, resamps;
		//debug
		// postf("freqs submitted to fillResPeaks: %\n", freqs);

		if( amps.size > numres, {
			while({amps.size > numres}, {
				var rmvdex;
				rmvdex = amps.minIndex;
				amps.removeAt(rmvdex);
				freqs.removeAt(rmvdex);
			});
			amps = amps.normalizeSum; // renormalize
		});

		//debug
		// postf("freqs after chopping to numres: %\n", freqs);

		if(amps.size < numres, {
			resfreqs = freqs.extend(numres, 10);	// pad 10Hz res
			resamps = amps.extend(numres, 0);	// pad 0 amp
			//debug
			// postf("had to extend freqs to numres: %\n", resfreqs);
			},{
				resfreqs = freqs;
				resamps = amps;
				//debug
				// postln("no need to extend freqs");
			}
		);
		// debug
		postf("new freqs: %\nnew amps: %\n", resfreqs.round, resamps.round(0.01));
		^[resfreqs, resamps]
	}

	// for updating a running fbdelay synth
	resonances_{ |which, freqs, amps|
		var resfreqs, resamps;

		#resfreqs, resamps = this.fillResPeaks(freqs, amps);
		which.notNil.if(
			{
				if( which <= (notes.size-1),{
					notes[which].resfreqs_(resfreqs).resamps_(resamps)
				},{ "'which' out of range".warn })
			},{
				notes.do{|nt| nt.resfreqs_(resfreqs).resamps_(resamps)} }
		)
	}

	// for REPLACING a running fbdelay synth with a new one
	newResonance { | which, freqs, amps, pulsetail=4, echodist |
		var resfreqs, resamps, rplcnote, newnote;

		if( which <= (notes.size-1), {
			#resfreqs, resamps = this.fillResPeaks(freqs, amps);
			rplcnote = notes[which];

			// hoping to use this one...
			//start new note
			newnote = synthdef.note(target: group)
			.inbus_(rplcnote.inbus)
			.outbus_(rplcnote.outbus)
			.fb_amt_(rplcnote.fb_amt)
			.delay_(echodist ?? {rplcnote.delay})
			.bw_(rplcnote.bw)
			.spread_(rplcnote.spread)
			.startpan_(rplcnote.startpan)
			.pulsetail_(pulsetail)
			.fbdecaytime_(rplcnote.fbdecaytime)
			.amp_(amp)
			.limamp_(0.4) // -16.5.dbamp
			.attack_(0.01)
			.resfreqs_(resfreqs)
			.resamps_(resamps)
			.t_gate_(1) // send pulse
			.play;

			/*//original
			//start new note
			newnote = synthdef.note(target: group)
			.inbus_(rplcnote.inbus)
			.outbus_(rplcnote.outbus)
			.fb_amt_(rplcnote.fb_amt)
			.delay_(echodist ?? {rplcnote.delay})
			.bw_(rplcnote.bw)
			.spread_(rplcnote.spread)
			.startpan_(rplcnote.startpan)
			.pulsetail_(pulsetail)
			.amp_(amp)
			.limamp_(0.5) // was originally -16.5.dbamp
			.resfreqs_(resfreqs).resamps_(resamps)
			.t_gate_(1) // send pulse
			.play;*/

			// release old note
			rplcnote.release;
			// replace old note with new in notes
			notes[which] = newnote;
			},{"'which' index out of range".warn}
		);
	}

	free {
		outbusses.do(_.free);
		group.freeAll;
	}
}

/*
synthdef = CtkSynthDef(\fbdelay, { arg outbus=0, inbus,
				bw = 1500, spread=0.3, t_gate,
				pulsetail = 1.2, // tail of pulse
				reltime=2.8, // fade time when synth released
				fbdecaytime = 5, // decay on the feedback amount to avoid ringing
				amp = 1, limamp = 0.5, attack=0.01,
				hpf_freq=130, fb_amt=0, delay=0.01, gate=1,
				minpan = -0.5, maxpan = 0.5,
				pitchDriftFreq=0.0833, freqshift = 5;
				// wSize=0.2, pRatio=0.1, pDispersion=0.01, tDispersion=0.05;

				var source, env, local, rfreqs, ramps, sndout, pan, fbdecay;
				// env just used for freeing
				env = EnvGen.kr(
					Env([1, 1, 0], [0.1, reltime], \sin,  releaseNode:1),
					gate, doneAction: 2
				);

				source = InFeedback.ar(inbus,1);// source = In.ar(inbus,1);
				// envelope/gate the input with a trigger
				source = source * EnvGen.kr(
					Env([0, 1, 0], [attack, pulsetail], [-3,1.5],  loopNode:1),
					t_gate, doneAction: 0
				);

				fbdecay = EnvGen.kr(
					Env([1,1, 0], [0.01, fbdecaytime], [1, 1.5],  loopNode:1),
					t_gate, doneAction: 0
				);

				/* feedbacknetwork */
				local = LocalIn.ar(2) + [source, 0]; // read feedback, add to source
				local = DelayL.ar(local, 0.9, delay); // delay sound
				rfreqs = Lag.kr( Control.names([\resfreqs]).kr(
					[ 56.23, 221.83, 447.73, 568.91, 678.17, 900.92, 1123.47, 2023.22, 2244.35 ]
				));
				ramps = Lag.kr( Control.names([\resamps]).kr(
					numres.collect{numres.reciprocal}
				));
				local = [
					Mix.ar( RLPF.ar(local[0], rfreqs, bw/rfreqs, ramps) ),
					Mix.ar( RLPF.ar(local[1], rfreqs, bw/rfreqs, ramps) );
				];
				local = FreqShift.ar(local, LFDNoise3.kr(pitchDriftFreq) * freqshift);
				/*local = PitchShift.ar(
				local, windowSize: wSize,
				pitchRatio: LFDNoise3.kr(pitchDriftFreq) * pRatio + 1,
				pitchDispersion: pDispersion, timeDispersion: tDispersion
				);*/
				local = LeakDC.ar(local);
				local = HPF.ar(
					Limiter.ar(local, limamp), hpf_freq
				);
				// reverse channels to give ping pong effect, apply decay factor
				LocalOut.ar( local.reverse *
					Lag.kr(fb_amt.clip(0.0, 0.9)) * fbdecay
				);

				pan = LFDNoise3.kr(0.36).range(minpan, maxpan); // drift in panning
				sndout = Mix.ar([
					Pan2.ar(local[0], pan+(spread * -0.5)),
					Pan2.ar(local[1], pan+(spread * 0.5))
				]);
				Out.ar(outbus, sndout * amp * env);
			});
*/

/* test
~sd = SiteFBDelay([s.options.numOutputBusChannels])
~sd.play
s.scope(1, ~sd.outbusses[0].bus);

~sd.outbusses[0].bus
x = {Out.ar(0, InFeedback.ar(~sd.outbusses[0].bus, 2)) }.play


~sd.note.pRatio_(0.005)
~sd.note.pDispersion_(0.01)
~sd.note.tDispersion_(0.005)
~sd.note.wSize_(0.4)

~sd.delay_(0, 0.1102)
~sd.fbamt_(0, 0.3)
~sd.bw_(0,250)
~sd.sendPulse(0, 6)

(// change resonance
var num, frqs, amps;
num = rrand(5,14).postln;
frqs = 100 * Array.series(num,1,1)*rrand(1,4.0);
frqs.round.postln;
amps = num.collect{rrand(0, 1.0)};
~sd.delay_(0, rrand(0.08,0.1102).postln);
~sd.resonances_(0,frqs, amps);
~sd.sendPulse(0, 6)
)
~sd.fbamt_(0, 0.1)
~sd.bw_(0,250)
~sd.notes.do(_.spread_(0.8))

~sd.notes.do{|nt| nt.freqshift_(5)}
~sd.notes.do{|nt| nt.spread.postln}
~sd.notes.do{|nt| nt.spread_(0.8)}




~sd.stop
~sd.free
*/

