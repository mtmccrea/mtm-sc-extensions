PlayControl {
	//copyArgs
	var path;
	var <buffer, <server;
	var <synthdef, <synth, <playBus, <playhead=0;
	var defname, playheadResp;

	// synth args
	var <rate=1, <start=0, <end, <resetPos=0, <replyRate=10;

	*new { |path, makeGui=true, server|
		^super.newCopyArgs(path).init(makeGui, server)
	}

	init { |makeGui, argServer|
		server = argServer ?? Server.default;

		server.waitForBoot {
			fork{
				var cond = Condition();

				this.loadBuffer(cond);
				cond.wait; cond.test = false;

				if (server.sampleRate != buffer.sampleRate) {
					format("Buffer sample rate and server sample rate do not match!\n Server fs: %\nBuffer fs:%\n",
						server.sampleRate, buffer.sampleRate
					).warn;
				};

				playBus = Bus.control(server, this.numChannels);

				this.prBuildSynthDef(cond);
				cond.wait;

				this.makeGui;
			}
		}
	}

	loadBuffer { |finishCondition|
		buffer = Buffer.read(server, path,
			action: { finishCondition !? {finishCondition.test_(true).signal} }
		);
	}

	numFrames {^buffer.numFrames}
	numChannels {^buffer.numChannels}

	prBuildSynthDef { |finishCondition|
		var nch;
		nch = this.numChannels;
		defname = (\playCntrlBuf_++nch++'ch').asSymbol;

		// create the node, without putting it on the server
		synth !? {synth.free};
		synth = Synth.basicNew(defname, server);
		NodeWatcher.register(synth);

		synthdef = SynthDef(defname, {arg outbus, bufnum, rate=1, start=0, end,  t_reset=0, resetPos=0, replyRate=10;
			var phsr, bfrd;
			phsr = Phasor.kr(t_reset, BufRateScale.kr(bufnum) * rate, start, end);
			SendReply.kr(Impulse.kr(replyRate), '/playhead', phsr);
			bfrd = BufRd.kr(nch, bufnum, phsr);
			Out.kr(outbus, bfrd);
		}).send(server);
		"here".postln;
		this.prBuildPlayheadResponder;
		"here2".postln;
		finishCondition !? {finishCondition.test_(true).signal};
	}

	prBuildPlayheadResponder {
		playheadResp !? {playheadResp.free};
		"here3".postln;
		playheadResp = OSCFunc(
			{ |msg| playhead = msg[3] },
			'/playhead', server.addr, nil, [synth.asNodeID]
		)
	}

	play {
		if (synth.isPlaying.not) {
			// play the synth on the server
			var msg;
			msg = synth.newMsg(nil, [
				\outbus, playBus,
				\buffer, buffer.bufnum,
				\rate, rate,
				\start, start,
				\end, end ?? this.numFrames,
				\resetPos, resetPos,
				\replyRate, replyRate
			]);
			msg.postln;
			server.sendBundle(nil, msg );
		} {
			synth.isRunning.not.if { synth.run(true) }
		};
	}

	busnum { ^playBus.index }

	/* synth setters */
	rate_ { |rateRatio|
		synth !? { synth.set(\rate, rateRatio) };
		rate = rateRatio;
	}
	// pos in frames
	resetPos_ { |pos|
		synth !? { synth.set(\resetPos, pos) };
		resetPos = pos;
	}
	// pos in frames
	start_ { |pos|
		synth !? { synth.set(\start, pos) };
		start = pos;
	}
	// pos in frames
	end_ { |pos|
		synth !? { synth.set(\end, pos) };
		end = pos;
	}
	replyRate_ { |rate|
		synth !? { synth.set(\replyRate, rate) };
		replyRate = rate;
	}

	stop {
		synth.isRunning.if { synth.run(false) }
	}

	// set selection, normalized
	selStart_{ |pos|
		this.start_(pos * this.numFrames);
	}
	selEnd_{ |pos|
		this.end_(pos * this.numFrames);
	}

	makeGui {

	}

	free {
		synth !? {synth.free};
		playBus.free;
		buffer.free;
		playheadResp !? {playheadResp.free};
	}
}

/* usage
~path = "/Users/admin/Desktop/test/ctlTestTwo_1.WAV";
~player = PlayControl(~path)

~player.play

c = ControlPlotter(~player.playBus.index, ~player.numChannels, 50, plotMode: \linear, overlay: false).start

~player.free
*/