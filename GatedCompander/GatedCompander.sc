/*
g = GatedCompander(s.options.numOutputBusChannels, 2)
*/
GatedCompander {
	classvar synthDefs;
	// copyArgs
	var <inbus, <outbus, <server;
	var <synth, <monitorSynth;
	var <meter, <win, <meterResponder;

	*new { |inbus, outbus, addAction, target, server, makeWin=false, finishCond|
		^super.newCopyArgs(inbus, outbus, server).init(addAction, target, makeWin, finishCond);
	}


	init { |addAction, target, makeWin, finishCond|
		server = server ?? Server.default;
		fork ({
			synthDefs ?? {
				synthDefs = CtkProtoNotes(
					SynthDef(\gatedCompander, {
						arg inbus, outbus, inAmp = 1,
						boostThresh = 0.1, boostRatio = 1, compRatio = 1,
						gateThresh = 0.001, gateRatio = 1,
						attack = 0.01, release=0.1, amp=1;

						var in, comp, lim;

						in = In.ar(inbus, 1) * inAmp;

						// boost low levels, compress high levels
						comp = Compander.ar(in, in,
							thresh: boostThresh,
							slopeBelow: boostRatio,
							slopeAbove: compRatio,
							clampTime: attack,
							relaxTime: release
						);
						// gate very low levels
						comp = Compander.ar(comp, in, // monitor the INPUT
							thresh: gateThresh,
							slopeBelow: gateRatio,
							clampTime: attack,
							relaxTime: release
						);

						lim = Limiter.ar(LeakDC.ar(comp), 1);

						ReplaceOut.ar( outbus, lim * amp);
					}),

					SynthDef(\gCompanderMonitor, { arg inbus, updateRate=10;
						var in, trig, rms, peak;
						in = In.ar(inbus, 1);
						trig = Impulse.ar(updateRate);
						rms = RunningSum.rms(in, numsamp: updateRate.reciprocal * SampleRate.ir);
						peak = Peak.ar(in, Delay1.ar(trig));
						SendReply.ar(trig, '/gcCompLevels', [rms, peak]);
					})

				);
				server.sync;
			};

			synth = synthDefs[\gatedCompander]
			.note(addAction: addAction ?? \head, target: target ?? 1)
			.inbus_(inbus)
			.outbus_(outbus);

			monitorSynth = synthDefs[\gCompanderMonitor]
			.note(addAction: \after, target: synth)
			.inbus_(inbus);

			server.sync;
			finishCond !? {finishCond.test_(true).signal};
			makeWin.if{this.makeMeter; this.makeWin;};
		}, AppClock)
	}

	play {synth.play}

	// tell the monitor synth to broadcast it's levels
	// TODO: create a mode on the meter that monitors both in and out at the same time
	// meterInput: boolean–meter input to compander (true), or output (false)
	broadcast { |meterInput|
		meter ?? {this.makeMeter};

		meterInput !? {
			monitorSynth.inbus_(if (meterInput) {inbus} {outbus});
			this.changed(\meterInput, meterInput);
		};

		monitorSynth.isPlaying.not.if{monitorSynth.play};

		meterResponder ?? {
			meterResponder = OSCFunc({
				|msg|
				// msg.postln;
				// meter.rmsPeak_(*msg[3..4])
				this.changed(\rmsPeak, *msg[3..4]);
			}, '/gcCompLevels',
			argTemplate: [monitorSynth.node]
			)
		};

	}

	stopBroadcasting {
		monitorSynth.free;
	}

	meterInput {
		this.broadcast(true)
	}

	meterOutput {
		this.broadcast(false)
	}

	// create the meter view for embedding
	makeMeter {
		meter = GatedCompanderView(this);
		this.addDependant(meter);
		this.broadcast;
	}

	// make it's own window with meter embedded
	makeWin {
		meter ?? {this.makeMeter};
		this.broadcast;
		win = Window(
			format("Gated Compander - in: %  out: %", inbus, outbus),
			Size(400, 400).asRect
		).front;
		win.view.layout_(HLayout(meter.view).margins_(0));
		win.onClose_{
			this.removeDependant(meter);
			meter = nil;
		};
	}

	// synth params
	inbus_ { |bus|
		synth.inbus_(bus);
		monitorSynth.inbus_(bus);
		this.changed(\inbus, bus);
	}

	outbus_ { |bus|
		synth.outbus_(bus);
		this.changed(\outbus, bus);
	}

	boostThresh_ { |amp|
		synth.boostThresh_(amp);
		this.changed(\boostThresh, amp);
	}

	boostRatio_ { |ratio|
		synth.boostRatio_(ratio);
		this.changed(\boostRatio, ratio);
	}

	compRatio_ { |ratio|
		synth.compRatio_(ratio);
		this.changed(\compRatio, ratio);
	}

	gateThresh_{ |amp|
		synth.gateThresh_(amp);
		this.changed(\gateThresh, amp);
	}

	gateRatio_ { |ratio|
		synth.gateRatio_(ratio);
		this.changed(\gateRatio, ratio);
	}

	attack_ { |secs|
		synth.attack_(secs);
		this.changed(\attack, secs);
	}

	release_ { |secs|
		synth.release_(secs);
		this.changed(\release, secs);
	}

	amp_ { |amp|
		synth.amp_(amp);
		this.changed(\amp, amp);
	}

	inAmp_ { |amp|
		synth.inAmp_(amp);
		this.changed(\inAmp, amp);
	}

	free {
		synth.free;
		monitorSynth.free;
		meterResponder !? {meterResponder.free};
		meter !? {this.removeDependant(meter)};
		if (win.notNil) {win.close; win = nil} {meter !? {meter=nil}};
	}


}