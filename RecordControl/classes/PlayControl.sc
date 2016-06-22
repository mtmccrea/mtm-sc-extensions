PlayControl {
	//copyArgs
	var path;
	var <buffer, <server, <gui;
	var <synthdef, <synth, <playBus, <playhead=0;
	var defname, playheadResp;
	var <trimStart, <trimEnd, trimLength;
	var pauseResp, resetResp;

	// synth args
	var <rate=1, <start, <end, <resetPos=0, <replyRate=10;

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
					postf("NOTE: Buffer sample rate and server sample rate do not match.\n  This is OK, as long as recording and playback blocksizes match.\n  Server fs: %\n  Buffer fs: %\n",
						server.sampleRate, buffer.sampleRate
					);
				};

				playBus = Bus.control(server, this.numChannels);

				trimStart = 0;
				trimEnd = this.numFrames;
				start = trimStart;
				end = trimEnd;

				this.prBuildSynthDef(cond);
				cond.wait;

				if (makeGui) {this.makeGui};
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
		var nch, msg;
		nch = this.numChannels;
		defname = (\playCntrlBuf_++nch++'ch').asSymbol;

		synth !? {synth.free};

		// create the node, without putting it on the server,
		synth = Synth.basicNew(defname, server);
		NodeWatcher.register(synth);

		 buffer.bufnum.postln;

		// create a newMsg, to be sent following
		// SynthDef being received by server
		msg = synth.newMsg(nil, [
			\outbus, playBus,
			\buffer, buffer.bufnum,
			\rate, 0, 		// start with a paused playhead
			\start, start,
			\end, end,
			\resetPos, resetPos,
			\replyRate, replyRate
		]);

		// this func listens for the synth to start,
		// immediately pauses it, and restores it's rate from 0
		pauseResp = OSCFunc(
			{
				server.makeBundle(nil, {
					synth.run(false);
					synth.set(\rate, rate);
				});
				// "found running".postln; // debug
			},
			'/n_go', server.addr, nil, [synth.asNodeID]
		).oneShot;

		NodeWatcher.register(synth);

		synthdef = SynthDef(defname, {arg outbus, bufnum, rate=1, start=0, end,  t_reset=0, resetPos=0, replyRate=10;
			var phsr, bfrd;
			phsr = Phasor.kr(t_reset, BufRateScale.kr(bufnum) * rate, start, end, resetPos);
			SendReply.kr(Impulse.kr(replyRate), '/playhead', phsr);
			bfrd = BufRd.kr(nch, bufnum, phsr);
			Out.kr(outbus, bfrd);
		}).send(server, msg);

		this.prBuildPlayheadResponder;

		finishCondition !? {finishCondition.test_(true).signal};
	}

	prBuildPlayheadResponder {
		playheadResp !? {playheadResp.free};

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
				\end, end,
				\resetPos, resetPos,
				\replyRate, replyRate
			]);
			msg.postln;
			server.sendBundle(nil, msg );
		} {
			synth.isRunning.not.if {
				synth.run(true);

			}
		};
	}

	busnum { ^playBus.index }

	/* synth setters */

	rate_ { |rateRatio|
		synth !? { synth.set(\rate, rateRatio) };
		rate = rateRatio;
	}
	// pos in frames
	resetPos_ { |frame|
		synth !? { synth.set(\resetPos, frame) };
		resetPos = frame;
	}
	// start of the phasor, in frames
	start_ { |frame|
		synth !? { synth.set(\start, frame) };
		start = frame;
		this.resetPos_(frame);
	}
	// end of the phasor, in frames
	end_ { |frame|
		synth !? { synth.set(\end, frame) };
		end = frame;
	}
	// rate that the playhead variable is updated
	replyRate_ { |rate|
		synth !? { synth.set(\replyRate, rate) };
		replyRate = rate;
	}


	stop {
		synth.isRunning.if { synth.run(false) }
	}

	reset {
		synth !? { synth.set(\t_reset, 1) };
		playhead = resetPos; // push the playhead to reset point in case synth isn't running
	}

	// set loop selection, normalized to trim size
	selStart_{ |pos|
		// this.start_(pos * this.numFrames);
		this.start_(trimStart + (pos * this.trimLength));
	}
	selEnd_{ |pos|
		// this.end_(pos * this.numFrames);
		this.end_(pos * this.trimLength);
	}
	// pos normalized to trim size
	jumpTo_{ |pos|
		var temp_rpos;
		temp_rpos = resetPos;
		this.resetPos_(trimStart + (pos * this.trimLength));
		this.reset;
		if (synth.isRunning) {
			// playhead will jump to resetPos, then restore it
			fork{ 0.2.wait; this.resetPos_(temp_rpos) };
		} {
			// need to schedule reset after the synth resumes
			// this func listens for the synth to run,
			// immediately restore resetPos
			resetResp !? {resetResp.free};
			resetResp = OSCFunc(
				{
					this.resetPos_(temp_rpos);
					resetResp !? {resetResp.free};
				},
				'/n_on', server.addr, nil, [synth.asNodeID]
			).oneShot;
		}
	}

	// trim beginning and end, in frames
	trimStart_{ |frame|
		trimStart = frame;
	}
	trimEnd_{ |frame|
		trimEnd = frame;
	}
	trimLength { ^(trimEnd - trimStart) }

	clearSelection {
		this.start_(trimStart);
		this.end_(trimEnd);
	}

	makeGui { |plotWidth(600), plotHeight(400)|
		gui = PlayControlView(this, plotWidth, plotHeight);
	}

	free {
		synth !? {synth.free};
		playBus.free;
		buffer.free;
		playheadResp !? {playheadResp.free};
		pauseResp !? {pauseResp.free};
		resetResp !? {resetResp.free};
	}
}

/* usage

~path = "/Users/admin/Desktop/test/us_rec_session1/amp_1_kr.WAV";
~path = "/Users/admin/Desktop/test/us_session2/pos_kr.WAV";
~path = "/Users/admin/Desktop/test/us_session2/amp_kr.WAV";
~player = PlayControl(~path)
~player2 = PlayControl(~path)

~player.makeGui;
~player.gui.plot
~player.play

c = ControlPlotter(~player.playBus.index, ~player.numChannels, 50, plotMode: \linear, overlay: false).start

~player.free
*/