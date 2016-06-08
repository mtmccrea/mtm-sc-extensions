SiteConvolve {
	var <initInbusnums, <amp, <initGroup, <server, <loadCond;
	var <isPlaying = false, <numconvs, <group, <synthdef;
	var <inbusnums, <outbusses,  <notes;

	// busnums an array of input busses [conv1src1, conv1src2, conv2src1, conv2src2,...]
	*new {|inbusnums, initAmp=1, group, server, loadCond|
		^super.newCopyArgs(inbusnums, initAmp, group, server, loadCond).init;
	}

	init {
		server = server ?? Server.default;

		inbusnums = [];
		outbusses = [];
		notes = [];

		group = initGroup.notNil.if(
			{CtkGroup.play(target: initGroup)},
			{CtkGroup.play(addAction: \tail, server: server)}
		);

		synthdef = CtkSynthDef(\conv_6ins_1conv, {arg outbus=0, kernelbus, inbus, amp = 1, limamp = 0.5, srcswitchfrq = 0.03, gate = 1;
			var env, kernel, input, which, src, conv;
			env = EnvGen.kr(Env([0,1,0], [0.3, 0.3], \sin, releaseNode: 1), gate, doneAction:2);
			kernel = InFeedback.ar(kernelbus, 1);
			input = In.ar(inbus, 6);
			which = LFDNoise3.kr(srcswitchfrq).range(-0.2, 1.2).clip(0.0,1.0);
			// src = SelectX.ar(which, [input[0],input[1],input[2],input[3],input[4],input[5]]);
			src = SelectX.ar(which, input[0..5]);

			conv = Convolution.ar(src, kernel, 1024, amp);
			conv = Limiter.ar(conv, limamp);
			Out.ar(outbus, conv * env);
		});

		this.addConvs(initInbusnums);
	}

	// add busses to convolve, assumes adjacent pairs, i.e. [4,5,6,7]>[[4,5],[6,7]]
	addConvs { |inbusses|
		fork {
			var newins, newoutbusses, newnotes;
			newins = inbusses.clump(2);
			newoutbusses = newins.size.collect{ CtkAudio.play(1) };

			newnotes = newins.collect{ |srcArr, i|
				synthdef.note(target: group)
				.srcswitchfrq_(15.reciprocal) // rate for input source pointer
				.inbus_(srcArr[0]).kernelbus_(srcArr[1])
				.amp_(amp)
				.limamp_(0.5)
				.outbus_(newoutbusses[i]);
			};

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
	}

	free {
		outbusses.do(_.free);
		group.freeAll;
	}
}

/* test

// test with internal mic and noise
x = {Out.ar(3, Decay.ar(Impulse.ar(3), 0.2, PinkNoise.ar(0.25)))}.play
s.scope(1, 3) // the noise
s.scope(1, s.options.numOutputBusChannels) // the live in

~sc = SiteConvolve.new([s.options.numOutputBusChannels,3]);
~sc.play
s.scope(1, ~sc.outbusses[0].bus) // the convolution
~sc.addConvs([s.options.numOutputBusChannels,3]);
s.scope(1, ~sc.outbusses[1].bus) // the convolution
~sc.stop
~sc.free
x.free

~sc.inbusnums
~sc.group
~sp.outbusses
~sp.notes
*/