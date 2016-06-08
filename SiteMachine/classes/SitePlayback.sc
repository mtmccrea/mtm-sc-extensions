SitePlayback {
	var <inbusnums, <initGroup, <server, <loadCond;
	var <numplaybacks, <group, <rmsGroup, <outbus, <synthdef, <rmsDef, <notes, <isPlaying = false;

	// busnums an array of hardware input busses to create a playback stream from
	*new {|inbusnums, group, server, loadCond|
		^super.newCopyArgs(inbusnums, group, server, loadCond).init;
	}

	init {
		fork {
			server = server ?? Server.default;
			numplaybacks = inbusnums.size;

			group = initGroup.notNil.if(
				{CtkGroup.play(target: initGroup)},
				{CtkGroup.play(addAction: \head, server: server)}
			);
			server.sync;
			// for rms synths
			rmsGroup = CtkGroup.play(addAction: \tail, target: group);

			this.loadSynths;

			// outbusses = numplaybacks.collect{ CtkAudio.play(1) };
			outbus = CtkAudio.play(numplaybacks);
			server.sync;

			loadCond !? {loadCond.test_(true).signal};
		}
	}

	play {
		if(isPlaying.not, {
			notes = inbusnums.collect{ |inbusnum, i|
				synthdef.note(target: group)
				.inbus_(inbusnum).outbus_(outbus.bus+i)
				.inboostamp_(24.dbamp) // pre-compression boost
				.cthresh_(-24.dbamp) // 0.3 default
				.cslopeabove_(7.5.reciprocal) // 3.reciprocal default
				.cslopebelow_(6.reciprocal) // 3.reciprocal default
				.cclamptime_(0.01)
				.crelaxtime_(0.4)
				.limamp_(-9.dbamp)
				.amp_(1)
				.play;
			};
			isPlaying = true;
		},{"SitePlayback already playing".warn})
	}

	stop {
		notes.do(_.free);
		isPlaying = false;
	}

	free {
		outbus.free;
		group.freeAll;		// only free group if this instance created the group
		isPlaying = false;
	}

	loadSynths {
		synthdef = CtkSynthDef(\site_playback, { arg outbus, inbus, inboostamp=1, limamp=1, amp=1,
			cthresh=0.1778, cslopebelow=0.333, cslopeabove=0.333, cclamptime=0.01, crelaxtime=0.05;
			var in, snd, comp, lim;
			in = SoundIn.ar(inbus, inboostamp);
			// add in normalizing processing
			comp = Compander.ar( in,
				control: in,
				thresh: cthresh,
				slopeBelow: cslopebelow,
				slopeAbove: cslopeabove,
				clampTime: cclamptime,
				relaxTime: crelaxtime
			);

			lim = Limiter.ar(comp, limamp);
			Out.ar(outbus, lim * amp);
		});

		rmsDef = CtkSynthDef(\rms, { arg busnum=2, amp = 1;
			var si;
			si = In.ar(busnum, 1) * amp;
			RunningSum.rms(si).ampdb.round(0.1).poll;
		});
	}

	getCamRMS { |cam=0|
		rmsDef.note(addAction: \tail, target: rmsGroup).busnum_(outbus.bus+cam).play;
	}

	stopCamRMS {
		rmsGroup.children.do{|nodeID| server.sendMsg(\n_free, nodeID)};
	}
}

/* test
~sp = SitePlayback.new(s.options.numOutputBusChannels + [0,0,0]);
~sp.play
~sp.stop

~sp.inbusnums
~sp.group
~sp.outbus
~sp.notes
*/