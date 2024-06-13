MBCompressor {
	classvar <>compSD, <>autogainSD;
	// copyArgs
	var <inbusnum, <outbus, <floor, <ceil, <targ, <gainrolloff, <server, <compAddAction, <compTarget;

	var <synth, <agsynth, <compbus, <gainbus,
	<freqcntls, <threshcntls, <compcntls, <gaincntls,
	<getgains, <getfreqs, <getthreshs, <getcomps, <tempgains,
	<defXOverFreqs, <ui;

	/* UNCOMMENT TO USE THIS CLASS */
	*initClass {
		StartUp.add({
			this.loadSynthDefs;
		});
	}

	*new { |inbusnum, outbus, floor = -40, ceil = -10, targ = 0, gainrolloff = 0, server, compAddAction = \tail, compTarget = 1|
		^super.newCopyArgs( inbusnum, outbus, floor, ceil, targ, gainrolloff, server, compAddAction, compTarget ).init;
	}

	init {
		server = server ?? Server.default;
		outbus = outbus ?? {3};
		defXOverFreqs = [20, 750, 1500, 3000, 6000, 20000];
		tempgains = [0,0,0,0,0]; // temp gains in amp for soloing
		// init presets dictionary if not already
		Archive.global[\mbcPresets] ?? {Archive.global.put(\mbcPresets, IdentityDictionary.new(know: true))};


		//	the synth.set convention doesn't allow retrieval of updated parameter
		//	from a CTK note (bug), so had to fall back on a bit sloppier method

		//	gaincntls =
		//		[\gain1, \gain2, \gain3, \gain4, \gain5].collect({ |param, i|
		//			{	|val|
		//				synth.set(0.0, param, val.dbamp);
		//				this.changed(\gain, i, val)
		//			}
		//		});

		gaincntls =
		[
			{ |val| synth.gain1_(val.dbamp); this.changed(\gain, 0, val) },
			{ |val| synth.gain2_(val.dbamp); this.changed(\gain, 1, val) },
			{ |val| synth.gain3_(val.dbamp); this.changed(\gain, 2, val) },
			{ |val| synth.gain4_(val.dbamp); this.changed(\gain, 3, val) },
			{ |val| synth.gain5_(val.dbamp); this.changed(\gain, 4, val) }
		];

		threshcntls =
		[
			{ |val| synth.thresh1_(val.dbamp); this.changed(\thresh, 0, val) },
			{ |val| synth.thresh2_(val.dbamp); this.changed(\thresh, 1, val) },
			{ |val| synth.thresh3_(val.dbamp); this.changed(\thresh, 2, val) },
			{ |val| synth.thresh4_(val.dbamp); this.changed(\thresh, 3, val) },
			{ |val| synth.thresh5_(val.dbamp); this.changed(\thresh, 4, val) }
		];

		compcntls =
		[
			{ |val| synth.comp1_(val.reciprocal); this.changed(\comp, 0, val) },
			{ |val| synth.comp2_(val.reciprocal); this.changed(\comp, 1, val) },
			{ |val| synth.comp3_(val.reciprocal); this.changed(\comp, 2, val) },
			{ |val| synth.comp4_(val.reciprocal); this.changed(\comp, 3, val) },
			{ |val| synth.comp5_(val.reciprocal); this.changed(\comp, 4, val) }
		];

		freqcntls = [
			{ |val| synth.freq1_(val); this.changed(\freq, 0, val) },
			{ |val| synth.freq2_(val); this.changed(\freq, 1, val) },
			{ |val| synth.freq3_(val); this.changed(\freq, 2, val) },
			{ |val| synth.freq4_(val); this.changed(\freq, 3, val) },
			{ |val| synth.freq5_(val); this.changed(\freq, 4, val) },
			{ |val| synth.freq6_(val); this.changed(\freq, 5, val) }
		];

		getgains = [
			{ synth.gain1.ampdb },
			{ synth.gain2.ampdb },
			{ synth.gain3.ampdb },
			{ synth.gain4.ampdb },
			{ synth.gain5.ampdb }
		];

		getfreqs = [
			{ synth.freq1 },
			{ synth.freq2 },
			{ synth.freq3 },
			{ synth.freq4 },
			{ synth.freq5 },
			{ synth.freq6 }
		];

		getthreshs = [
			{ synth.thresh1.ampdb },
			{ synth.thresh2.ampdb },
			{ synth.thresh3.ampdb },
			{ synth.thresh4.ampdb },
			{ synth.thresh5.ampdb }
		];

		getcomps = [
			{ synth.comp1.reciprocal },
			{ synth.comp2.reciprocal },
			{ synth.comp3.reciprocal },
			{ synth.comp4.reciprocal },
			{ synth.comp5.reciprocal }
		];

		compbus = inbusnum.notNil.if(
			{CtkAudio.play(1, inbusnum, server: server)},
			{   "Creating default compression bus".postln;
				CtkAudio.play(1, server: server);
			}
		);

				// compbus = CtkAudio.play(1, server: server);
		gainbus = CtkAudio.play(1, server: server);


		synth = compSD.note( addAction: compAddAction, target: compTarget, server: server )
		.inbus_(compbus).outbus_(outbus).agbus_(gainbus).outsrc_(0);

		agsynth = autogainSD.note(addAction: \after, target: synth, server: server)
		.inbus_(gainbus).outbus_(outbus);
	}

	*loadSynthDefs {
		"MBCompressor synthdefs loading".postln;
		MBCompressor.compSD = CtkSynthDef( \mbComp, {
			arg	outbus = 3, inbus, agbus, outsrc = 0, bypass = 0,
			freq1=20, freq2=750, freq3=1500, freq4=3000, freq5=6000, freq6=20000,
			gainscl=1, gain1=1, gain2=1, gain3=1, gain4=1, gain5=1,
			comp1 = 1, comp2 = 1, comp3 = 1, comp4 = 1, comp5 = 1,
			thresh1 = 1, thresh2 = 1, thresh3 = 1, thresh4 = 1, thresh5 = 1,
			att = 0.007, rel = 0.25;

			var sig, xfreqs, bgain, bthresh, bcomp, order, sndout, imp, delimp;

			sig = In.ar(inbus, 1);

			xfreqs = [ freq1, freq2, freq3, freq4, freq5, freq6 ];
			bgain = [ gain1, gain2, gain3, gain4, gain5 ];
			bthresh = [thresh1, thresh2, thresh3, thresh4, thresh5];
			bcomp = [comp1, comp2, comp3, comp4, comp5];
			order = 6;

			// band processing
			sndout = 5.collect({ |i|
				var hpf, lpf, comp, out;
				hpf = BHPF.ar( sig, order, xfreqs[i] );
				lpf = BLPF.ar( hpf, order, xfreqs[i+1] );
				// hpf = PMHPF.ar( sig, xfreqs[i] );
				// lpf = PMLPF.ar( hpf, xfreqs[i+1] );

				comp = Compander.ar(lpf, lpf,
					bthresh[i], 1,
					bcomp[i],
					att, rel
				);
				out = comp * bgain[i];

				/* metering */
				imp = Impulse.kr(20);
				delimp = Delay1.kr(imp);
				SendReply.kr(imp, '/blevels',
					[
						RunningSum.rms(out).lag(0.3,0.3),
						K2A.ar(Peak.ar(out, delimp).lag(0, 3)), //  rms, peak
					], i
				);
				/* end metering */
				out;
			});
			sndout = sndout.sum;
			//sndout = Limiter.ar(sndout, 0.dbamp, 0.01);
			ReplaceOut.ar(
			//Out.ar(
				Select.kr(outsrc, [outbus, agbus]),
				Select.ar(bypass, [sndout, sig]) * gainscl;
			);

		});

		MBCompressor.autogainSD = CtkSynthDef(\autogain, {
			arg	outbus = 0, inbus, buf, inscale=1, gainscale=0.3, lookahead=0.1, lagbehind=0.15,
			muteL=0, muteR=0, att = 0.01, rel = 0.25, compthresh = 0, comprat = 1,
			targ=0, floor= -40, dbrolloff= 6;

			var	in, out, numsamp, rmswin, delorig, delsig, compsig;
			var	itarget, ifloor, idbrolloff;
			var	gain, gain_scaled, env;
			var	avrms, rms1, rms2, rms3;
			var	imp, delimp;

			//in = PlayBuf.ar(1, buf, loop: 1) * inscale;
			in = In.ar(inbus, 1) * inscale;
			//in = WhiteNoise.ar(SinOsc.ar(15.reciprocal, pi/2)) * inscale;
			//in = Limiter.ar(in, -0.3.dbamp, 0.01);

			rmswin = 0.125;
			numsamp = SampleRate.ir * rmswin;

			rms1 = (RunningSum.ar(in.squared, numsamp) * numsamp.reciprocal).sqrt;
			rms2 = (RunningSum.ar(in.squared, numsamp*0.5) * (numsamp * 0.5).reciprocal ).sqrt;
			rms3 = (RunningSum.ar(in.squared, numsamp*0.25) * (numsamp * 0.25).reciprocal ).sqrt;
			avrms = [rms1,rms2,rms3].sum / 3;
			avrms.ampdb.poll;

			// REMEMBER TO UPDATE THESE IN THE SYNTH ARGS TOO FOR GUI TO ACCESS
			itarget = 0;		// target amp for output (db)
			ifloor = -40;		// signal floor (db)
			idbrolloff = 6;	// rolloff toward tranfer function elbow (+db)

			gain = IEnvGen.ar(
				Env(
					[-120, itarget-ifloor-idbrolloff, itarget],
					[ifloor.abs, (itarget-ifloor).abs],
					[4, 2]
				),
				// avrms.clip(0.0, ceil.dbamp)
				(ifloor.abs + (itarget-ifloor)) - (avrms.ampdb).neg
			).dbamp;

			// avrms = avrms.clip(0.1,0.8);
			// gain = 1 / (avrms);

			gain_scaled =
			LagUD.ar(
				gain * gainscale,		//signal
				lookahead,			//lag up
				lagbehind+lookahead	//lag down
			);

			delsig = DelayN.ar( in, 2, lookahead ) * gain_scaled;

			delorig = DelayN.ar( in, 2, lookahead ); // debug

			// compression for peak control
			compsig =
			Compander.ar(delsig, delsig,
				compthresh.dbamp, 1,
				comprat.reciprocal,
				att, rel
			);

			//Out.ar(0, [ delorig, delsig ] * (1-[muteL, muteR]) );
			Out.ar( outbus, compsig );

			// metering
			imp = Impulse.kr(30);
			delimp = Delay1.kr(imp);
			SendReply.kr(imp, '/autogainlevels',
				[
					Amplitude.kr(delorig).lag(0, 0.3),
					K2A.ar(Peak.ar(delorig, delimp).lag(0, 1)), // in amp, peak

					Amplitude.kr(delsig).lag(0, 0.3),
					K2A.ar(Peak.ar(delsig, delimp).lag(0, 1)),  // out amp, peak

					gain_scaled,
					avrms,

					Amplitude.kr( compsig ).lag(0, 0.3),
					K2A.ar(Peak.ar( compsig, delimp).lag(0, 1)),  // out amp, peak
			]);

		})
	}

	play {
		{
			//agsynth.play;
			synth.play;
		}.fork;
	}

	amp_ { |amp|  // amp in db
		synth.gainscl_( amp.dbamp );
	}

	outbus_ { |busnum|
		synth.outbus_(busnum);
		outbus = busnum;
		this.changed( \iobus, nil, nil );
	}

	// inbus {
	// ^this.compbus.bus
	// }

	// auto-gain controls

	addAutoGain {
		agsynth.play;
		ui.makeAGView;
	}

	gainscale_ { |sclr|
		agsynth.gainscale_( sclr );
		this.changed(\gainscale, nil, sclr);
	}

	lookahead_ { |time|
		agsynth.lookahead_( time );
		this.changed(\lookahead, nil, time);
	}

	lagbehind_ { |time|
		agsynth.lagbehind_(time);
		this.changed(\lagbehind, nil, time);
	}

	pkthresh_ { |pk|
		agsynth.compthresh_(pk);
		this.changed(\pkthesh, nil, pk);
	}

	pkcomp_ { |compratio|
		agsynth.comprat_(compratio);
		this.changed(\pkcomp, nil, compratio);
	}

	bypassAGain { |flag|
		switch (flag)
		{1} { agsynth.pause; synth.outsrc_(0) }
		{0} { agsynth.run; synth.outsrc_(1); }
	}

	gui { |windowLabel| ^ui = MBCompressorView.new( this, windowLabel: windowLabel ) }

	freqScope { FreqScope.new( busNum: outbus ) }

	scope { |numChans=1, argBusNum| server.scope(numChans, argBusNum ?? {outbus}) }

	storePreset { |name|
		var archive, pset;
		block { |break|
			name.isNil.if({"provide a preset name".warn; break.value});
			archive = Archive.global[\mbcPresets];
			Archive.global[\mbcPresets].at(name.asSymbol).notNil.if({"provide a different preset name".warn; break.value});
			pset = IdentityDictionary(know: true);

			[\b1, \b2, \b3, \b4, \b5].do({ |key, i|
				pset.put(key,
					IdentityDictionary(know: true)
					.put(\gain, getgains[i].value)
					.put(\thresh, getthreshs[i].value)
					.put(\comp, getcomps[i].value)
				);
			});

			[\x1, \x2, \x3, \x4, \x5, \x6].do({ |key, i|
				pset.put(key, getfreqs[i].value )
			});

			archive.put( name.asSymbol, pset);
		}
	}

	recallPreset { |name|
		var archive, pset;
		block { |break|
			name.isNil.if({"provide a preset name".warn; break.value});
			archive = Archive.global[\mbcPresets];
			pset = archive.atFail(name.asSymbol, {"no preset of that name".postln; break.value});

			//compression settings
			[\b1, \b2, \b3, \b4, \b5].do({ |key, i|
				var band;
				band = pset[key];

				threshcntls[i].value( band.thresh );
				compcntls[i].value( band.comp );
				gaincntls[i].value( band.gain );
			});
			//xover settings
			[\x1, \x2, \x3, \x4, \x5, \x6].do({ |key, i|
				freqcntls[i].value( pset[key]);
			});
		}
	}

	free {
		[agsynth,synth].do(_.free);
		ui !? {ui.close};
	}

}

/*
//auto-gain
/*
noise gate > expander(band boos/cut multi-vand?) > limiter(multiband?)
-or-
noise gate > param EQ > expander
*/
Server.default = s = Server.internal;
s.options.sampleRate_(96000);
s.boot

b= CtkBuffer.playbuf("/Users/admin/Documents/Ultrasound/soundfiles/Sitting1_96k_norm.aif").load(sync: true);
b.free

/* Auto-gain - basic rms scaling - crude */
(
m = CtkSynthDef(\autogain, {arg inscale=1, rmstime=0.5, gainscale=0.2, buf, smooth = 0.5, onsetdly=2, muteL=0, muteR=0, ceil = 0, att = 0.01, rel = 0.25, compthresh = -15, comprat = 4;
var in, out, numsamp, rms, dly, newin, orig_in_del, post_gain;
var floor, ceil, targ, lookupenv, gain, gain_scaled, gainrolloff;
var avrms, rms1, rms2, rms3, newrms, newrms1, newrms2, newrms3;
var imp, delimp;
//test
//		in = Decay2.ar(
//			Impulse.ar(8, 0,LFSaw.kr(0.3, 0, -0.3, 0.3)),
//			0.001, 0.3,
//			Mix.ar(Pulse.ar([800,810], 0.3)) // env
//			);
in = PlayBuf.ar(1, buf, loop: 1) * inscale;
in = Limiter.ar(in, -0.3.dbamp, 0.01);

numsamp = SampleRate.ir * rmstime;
dly = rmstime*0.25*0.5; // delay corresponds to shortest running sum window / 2

rms1 = (RunningSum.ar(in.squared, numsamp) / numsamp).sqrt;
rms2 = (RunningSum.ar(in.squared, numsamp*0.5) / (numsamp * 0.5)).sqrt;
rms3 = (RunningSum.ar(in.squared, numsamp*0.25) / (numsamp * 0.25)).sqrt;
avrms = [rms1,rms2,rms3].sum / 3;

//		floor = -40; // lowest input signal rms in db
//		ceil = -10; // highest expected input signal rms in db
//		targ = -10;	// target rms to normalize signal
//		gainrolloff = 0; // gain rolloff scales down the amp boost at the lowest input threshold

//		gain = IEnvGen.ar(
//			InterplEnv(
//				[-160, targ-floor-gainrolloff, targ-ceil].dbamp, // gain amt
//				[floor.dbamp, ceil.dbamp - floor.dbamp], // breakpoints
//				[5, 0]
//				).plot,
//			avrms.clip(0.0, ceil.dbamp) // pointer (input amplitude)
//			);

lookupenv = Control.names(\xferenv).kr(
//default
InterplEnv(
[-160, 30, 0].dbamp, // gain amt
[-40.dbamp, -10.dbamp - -40.dbamp], // breakpoints
[5, 0]
)
);

gain = IEnvGen.ar( lookupenv, avrms.clip(0.0, ceil.dbamp) );// pointer (input amplitude)

// smooth the transfer function gain and delay the amp-modulated source
gain_scaled = LagUD.ar( gain * gainscale, dly*smooth, dly*smooth*3);

post_gain = DelayN.ar(in, 2, dly*onsetdly) * gain_scaled;
orig_in_del = DelayN.ar(in, 2, (dly*onsetdly));
//newin= Limiter.ar(newin, -0.3.dbamp, 0.1);

// compression for peak control
newin = Compander.ar(post_gain, post_gain,
compthresh.dbamp, 1,
comprat.reciprocal,
att, rel
);


Out.ar(0, [ orig_in_del, newin ] * (1-[muteL, muteR]) );

// for testing
newrms1 = (RunningSum.ar(newin.squared, numsamp) / numsamp).sqrt;
newrms2 = (RunningSum.ar(newin.squared, numsamp*0.5) / (numsamp * 0.5)).sqrt;
newrms3 = (RunningSum.ar(newin.squared, numsamp*0.25) / (numsamp * 0.25)).sqrt;
newrms = [newrms1,newrms2,newrms3].sum / 3;

//		avrms.poll(label: \rms);
//		newrms.poll(label: \NEW);

//		gain.poll(label: \gain);
//		gain_scaled.poll(label: \g_scld);

//		Out.kr(0, [avrms, newrms]);
//		Out.kr(0, [
//			LagUD.ar( gain * gainscale, dly*smooth, dly*smooth),
//			gain
//			] * 0.01)

// metering
imp = Impulse.kr(30);
delimp = Delay1.kr(imp);
SendReply.kr(imp, '/autogainlevels',
[
Amplitude.kr(orig_in_del),
K2A.ar(Peak.ar(orig_in_del, delimp).lag(0, 3)), // in amp, peak

Amplitude.kr(post_gain),
K2A.ar(Peak.ar(post_gain, delimp).lag(0, 3)),  // out amp, peak

gain_scaled,
avrms,

Amplitude.kr(newin),
K2A.ar(Peak.ar(newin, delimp).lag(0, 3)),  // out amp, peak
]);

})
)

(
var targ, floor, gainrolloff, ceil;

floor = -60; // lowest input signal rms in db
ceil = -10; // highest expected input signal rms in db
targ = 0;	// target rms to normalize signal
gainrolloff = 0; // gain rolloff scales down the amp boost at the lowest input threshold

n.xferenv_(
InterplEnv(
[-160, targ-floor-gainrolloff, targ-ceil].dbamp, // gain amt
[floor.dbamp, ceil.dbamp - floor.dbamp], // breakpoints
[5, 0]
) );
)
n.xferenv.plot
// smooth parameter increases the lag on the gain compensation
// smooth of 0.5 makes the lag the minimum delay/2, so more responsive to transients
// smooth of 3 makes the lag the minimum delay*3, so less responsive to transients

n = m.note.buf_(b).rmstime_(0.125).smooth_(0.5).gainscale_(0.2).play;
n.onsetdly = 8; // multiply the delay to catch onsets
n.rmstime_(0.25)
n.rmstime_(0.025)
n.rmstime_(0.05)
n.smooth_(0.50)
n.smooth_(100)
n.gainscale_(1)

// compression settings
n.compthresh_(-5);

n.compthresh_(-15);
n.comprat_(2.3)
n.comprat_(1.3)
n.comprat_(1)
n.att_(0.007)
n.rel_(0.25)
n.rel_(0.15)

n.rmstime_(0.5)
n.smooth_(4)

n.free

n.muteL=0;
n.muteR=1;

n.muteL=1;
n.muteR=0;

n.compscale_(1)
n.inscale_(0.dbamp)
n.inscale_(-6.dbamp)
n.inscale_(-10.dbamp)
n.inscale_(-15.dbamp)
n.inscale_(-25.dbamp) // input too quiet
n.rmstime_(0.05)

n.free
*/
/*
Server.default = s = Server.internal.waitForBoot({c = MBCompressor.new}).boot;

c.scope
c.freqScope

c.play
v = c.gui
v= c.addAutoGain

c.inbus

c.outbus_(~usInbus.bus)
c.outbus_(0)
c.synth.bypass_(0)

x = { Out.ar(c.compbus.bus, WhiteNoise.ar(SinOsc.ar(15.reciprocal))) }.play
x.free

b= CtkBuffer.playbuf("/Users/admin/Documents/Ultrasound/soundfiles/Sitting1_96k_norm.aif").load(sync: true);
b.free

x = {Out.ar(c.compbus, PlayBuf.ar(1, b, loop: 1))}.play
x.free


c.bypassAGain(1)
c.bypassAGain(0)
v.close

c.free

c.agsynth.compthresh_(0).comprat_(1).gainscale_(1)
c.agsynth.rmstime_(0.15).ceil_(0)
c.agsynth.inscale_(0.2)
c.agsynth.smooth_(0.2)
*/
