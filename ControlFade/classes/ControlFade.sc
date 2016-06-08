// control bus fading between static values and LFO sources, broadcast OSC
// M. McCrea - 2014.05.30

ControlFade {
	classvar <lfoUgens, <lfoDefs, <mixDef;
	var <busnum, <controlBus, <lfoBus, <>fadeTime, <lfoSynths, <mixSynth;
	var <plotter, server, <curLfoUgens;
	var <broadcasting = false, <broadcastAddr, broadcastTask, <broadcastTag, <broadcastRate=10, <broadcastWaittime = 0.1, isPaused = false;

	*new { |fadeTime=0.1, initVal=0, busnum, server, onComplete|
		^super.new.init(fadeTime, initVal, busnum, server, onComplete);
	}

	init { |argfadeTime, initVal, argbusnum, argserver, onComplete|
		block { |break|
			server = argserver ?? Server.default;
			server.serverRunning.not.if {warn("Server isn't running"); onComplete.(); break.value};
			fork {
				controlBus = argbusnum.notNil.if(
					{CtkControl(1, bus: argbusnum, server: server).play},
					{CtkControl(1, server: server).play}
				);
				lfoBus = CtkControl(2, server: server).play;
				server.sync;

				lfoDefs.isNil.if{ // if ugens haven't been loaded yet
					lfoUgens = [ SinOsc, Impulse, LFSaw, LFPar, LFTri, LFCub, LFNoise0, LFNoise1, LFNoise2, LFDNoise0, LFDNoise1, LFDNoise3 ];
					lfoDefs = IdentityDictionary(know:true);
					lfoUgens.do{ |ugen|
						lfoDefs.put( ugen.asSymbol,
							CtkSynthDef(\controlfader_LFO_ ++ ugen.asSymbol, {
								|outbus, freq=0.1, low=0, high=1, lag=0|
								var frq,lo,hi;
								frq	= Lag.kr(freq,lag);
								lo	= Lag.kr(low,lag);
								hi	= Lag.kr(high,lag);
								Out.kr(outbus, ugen.kr(frq).range(lo, hi));
							});
						);
					};

					mixDef = CtkSynthDef(\controlfader_mix, {
						arg
						outbus, lfoInbus, staticVal=0, staticLag=0.1, scaleLag=0.1,
						lfoDex = 0, lfoLag=0.1, ctlSrcDex=0,
						fadeIn=0.1, ctlFade=0.1, curve = 0, scale = 1, amp = 1, offset = 0, gate=1;
						var
						env, staticSrc, lfo1, lfo2, lfoSrc, mix;
						var lfoRatio, mixRatio;

						env = EnvGen.kr(Env([1,1,0],[fadeIn,ctlFade],\lin,1), gate, doneAction:0);
						staticSrc = VarLag.kr(staticVal, staticLag, curve);
						#lfo1, lfo2 = In.kr(lfoInbus,2);

						// linear crossfade approach
						lfoRatio = VarLag.kr(lfoDex, lfoLag, curve);
						lfoSrc = (lfo1 * (1- lfoRatio)) + (lfo2 * lfoRatio);

						mixRatio = VarLag.kr(ctlSrcDex, ctlFade, curve);
						mix = (staticSrc * (1- mixRatio)) + (lfoSrc * mixRatio);

						// // equal power crossfade approach - can overshoot
						// lfoSrc = SelectX.kr(
						// 	VarLag.kr(lfoDex, lfoLag, curve),
						// [lfo1, lfo2]);
						// mix = SelectX.kr(
						// 	VarLag.kr(ctlSrcDex, ctlFade, curve),
						// [staticSrc, lfoSrc]);

						Out.kr( outbus,
							mix * env
							* VarLag.kr(scale, scaleLag, curve)
							+ VarLag.kr(offset, scaleLag, curve)
							* VarLag.kr(amp, scaleLag, curve) );
					});
					server.sync;
				};

				busnum = controlBus.bus;
				fadeTime = argfadeTime;

				lfoSynths = 2.collect{ |i|
					lfoDefs[lfoUgens[0].asSymbol].note
					.outbus_(lfoBus.bus+i).freq_(1).low_(-1).high_(1)
					.lag_(fadeTime).play
				};

				curLfoUgens = lfoUgens[0].asSymbol.dup(2);

				mixSynth = mixDef.note
				.outbus_(busnum).lfoInbus_(lfoBus.bus)
				.staticVal_(initVal ?? 0).lfoDex_(0).ctlSrcDex_(0)
				.staticLag_(fadeTime).lfoLag_(fadeTime).ctlFade_(fadeTime)
				.play;

				server.sync;
				onComplete.();
			}
		}
	}

	// for static values
	value_ { |val, thisFadeTime|
		val !? {
			switch( mixSynth.ctlSrcDex,
				// static > static
				0, { mixSynth.staticLag_(thisFadeTime ?? fadeTime).staticVal_(val) },
				// lfo > static
				1, { mixSynth.staticLag_(0).staticVal_(val) }
			);
			this.changed(\staticVal, val);
			this.source_('static', thisFadeTime);
		}
	}

	// add a scale/offset to the mixed output signal
	scale_ {|mul, thisFadeTime| mixSynth.scaleLag_(thisFadeTime ?? fadeTime).scale_(mul); this.changed(\scale, mul) }
	scale { ^mixSynth.scale }
	offset_ {|add, thisFadeTime| mixSynth.scaleLag_(thisFadeTime ?? fadeTime).offset_(add); this.changed(\offset, add) }
	offset {^mixSynth.offset }
	amp_ {|mul, thisFadeTime| mixSynth.scaleLag_(thisFadeTime ?? fadeTime).amp_(mul); this.changed(\amp, mul) }
	amp {^mixSynth.amp }

	freq_ { |freq, thisFadeTime| freq !? {
		lfoSynths[mixSynth.lfoDex].lag_(thisFadeTime ?? fadeTime).freq_(freq);
		this.changed(\freq, freq)
	}}
	low_ { |low, thisFadeTime| low !? {
		lfoSynths[mixSynth.lfoDex].lag_(thisFadeTime ?? fadeTime).low_(low);
		this.changed(\low, low)
	}}
	high_ { |high, thisFadeTime| high !? {
		lfoSynths[mixSynth.lfoDex].lag_(thisFadeTime ?? fadeTime).high_(high);
		this.changed(\high, high)
	}}

	// for updating both high and low
	lfoBounds_ { |low, high, thisFadeTime|
		this.low_(low, thisFadeTime);
		this.high_(high, thisFadeTime);
	}

	// for updating more than one param
	lfoParams_ { |freq, low, high, thisFadeTime|
		this.freq_(freq, thisFadeTime);
		this.lfoBounds_(low, high, thisFadeTime);
	}

	// set the lfo, new or individual params
	lfo_ { |ugen, freq, low, high, thisFadeTime, onComplete|
		lfoDefs[ugen.asSymbol].notNil.if({

			fork {
				// check if the current lfo synth is already using this ugen
				if( curLfoUgens[ mixSynth.lfoDex ] == ugen.asSymbol,

					{	// just update current lfo params

						"UGen already current, just updating its params".postln;
						// switch from static to lfo if necessary
						if( mixSynth.ctlSrcDex == 0 )
						{
							this.lfoParams_(freq, low, high, 0);
							this.source_('lfo', thisFadeTime);
						}{
							this.lfoParams_(freq, low, high, thisFadeTime);
						};
						server.sync;

					},{	// start a new lfo

						var curLfoDex, curLfoSynth, nextLfoDex, frq, lo, hi;

						(ugen.asSymbol == 'LFNoise2').if{
							warn("Note LFNoise2 can overshoot bounds! Consider LFDNoise3 if this is a problem");
						};

						curLfoDex = mixSynth.lfoDex;
						curLfoSynth = lfoSynths[curLfoDex];
						frq	= freq	??	{ curLfoSynth.freq };
						lo	= low	??	{ curLfoSynth.low };
						hi	= high	??	{ curLfoSynth.high };

						nextLfoDex = (curLfoDex - 1).abs; // wrap btwn 0 & 1

						lfoSynths[nextLfoDex].free;

						lfoSynths[nextLfoDex] = lfoDefs[ugen.asSymbol].note
						.freq_(frq).low_(lo).high_(hi)
						.outbus_(lfoBus.bus+nextLfoDex).play;

						this.changed(\freq, frq);
						this.changed(\low, lo);
						this.changed(\high, high);

						server.sync;

						curLfoUgens[ nextLfoDex ] = ugen.asSymbol;

						switch( mixSynth.ctlSrcDex,
							// static > lfo
							0, {
								mixSynth.lfoLag_(0).lfoDex_(nextLfoDex);
								this.source_('lfo', thisFadeTime);
							},
							// lfo > lfo
							1, {
								mixSynth.lfoLag_(thisFadeTime ?? fadeTime).lfoDex_(nextLfoDex)
							}
						);
						server.sync;
						this.changed(\lfo, ugen.asSymbol);
					}
				);
				// (thisFadeTime ?? fadeTime).wait?
				onComplete !? {onComplete.value};
			};

		},{ warn("Ugen not found in list of available LFO types. Ugen required for new LFO")});
	}

	freq { ^lfoSynths[mixSynth.lfoDex].freq }
	low { ^lfoSynths[mixSynth.lfoDex].low }
	high { ^lfoSynths[mixSynth.lfoDex].high }

	// get the current lfo
	lfo {
		^if( mixSynth.ctlSrcDex == 0 )
		{ 'static' }
		{ curLfoUgens[ mixSynth.lfoDex ] }
	}

	value { ^mixSynth.staticVal }

	// toggle between the Lfos
	toggleLfo { |thisFadeTime|
		var nextLfoDex;
		nextLfoDex = mixSynth.lfoDex + 1 % 2; // wrap btwn 0 & 1
		mixSynth.lfoLag_(thisFadeTime ?? fadeTime).lfoDex_(nextLfoDex);
		this.changed(\lfo, curLfoUgens[ nextLfoDex ]);
	}

	// toggle between the static and lfo sources
	toggleSource { |thisFadeTime|
		var nextCtlDex;
		nextCtlDex = mixSynth.ctlSrcDex + 1 % 2; // wrap btwn 0 & 1
		this.source_(nextCtlDex, thisFadeTime ?? fadeTime);
	}

	// which: 'static' or 0, 'lfo' or 1
	source_ { |which, thisFadeTime|
		case
		{(which == 'static') or: (which == 0)}{
			mixSynth.ctlFade_(thisFadeTime ?? fadeTime).ctlSrcDex_(0);
			this.changed(\ctlSrcDex, 0);
			this.changed(\lfo, 'static');
		}
		{(which == 'lfo') or: (which == 1)}{
			mixSynth.ctlFade_(thisFadeTime ?? fadeTime).ctlSrcDex_(1);
			this.changed(\ctlSrcDex, 1);
			this.changed(\lfo, curLfoUgens[ mixSynth.lfoDex ]);
		};
	}

	plot { |plotLength = 75, updateRate = 15|
		plotter = ControlPlotter(busnum, plotLength: plotLength, refresh_rate: updateRate).start;
	}

	controlKind {
		^switch(mixSynth.ctlSrcDex, 0,{'static'},1,{'lfo'})
	}

	// zero out control
	clear { |thisFadeTime|
		mixSynth.ctlFade_(thisFadeTime ?? fadeTime).release;
	}
	// bring control back up
	resume { |thisFadeTime|
		mixSynth.fadeIn_(thisFadeTime ?? fadeTime).gate_(1);
	}

	broadcast { |aNetAddr, tag, rate|

		aNetAddr.notNil.if({this.broadcastAddr_(aNetAddr)},{
			broadcastAddr ?? {"No NetAddr provided for OSC destination".error}
		});
		tag.notNil.if({this.broadcastTag_(tag)},{
			broadcastTag ?? {"No OSC tag provided".error}
		});
		rate.notNil.if({this.broadcastRate_(rate)});

		// define the broadcast task if first time
		broadcastTask ?? {
			broadcastTask = Task({
				inf.do{
					controlBus.get({|busnum, val| broadcastAddr.sendMsg(broadcastTag, val) });
					broadcastWaittime.wait
				}
			});
		};

		broadcastTask.play;
		broadcasting = true;

	}

	broadcastTag_ {  |tag| broadcastTag = tag.asSymbol }
	broadcastRate_{  |hz| broadcastRate = hz; broadcastWaittime = hz.reciprocal }
	broadcastAddr_{  |aNetAddr| "setting broadcastAddr".postln; broadcastAddr = aNetAddr }

	stopBroadcast { broadcastTask !? {broadcastTask.stop; broadcasting = false} }

	pause { mixSynth.pause; lfoSynths.do(_.pause); isPaused = true; }
	run { mixSynth.run; lfoSynths.do(_.run); isPaused = false; }

	// fade to zero then free
	release { |thisFadeTime, freeBus=true|
		fork {
			mixSynth.ctlFade_(thisFadeTime ?? fadeTime).release;
			(thisFadeTime ?? fadeTime).wait;
			this.free(freeBus)
		};
	}
	// cleanup immediately
	free { |freeBus=true|
		broadcastTask !? { broadcastTask.stop.clock.clear };
		( lfoSynths ++ [mixSynth, lfoBus] ).do(_.free);
		freeBus.if{ controlBus.free };
		plotter !? {plotter.cleanup}
	}

	/*get { |action|
		// add oneshot OSCdef(\ctlfade, {action})
		// send trigger to mixer synth to send its value to the oneshotter
	}*/
}

/*
TESTING
s.boot
s.scope(3, 0, rate:'control')

c = ControlFade(fadeTime: 0.1, initVal:0);
c.lfo_(LFTri, 2, -0.5, 0)
c.low_(0.15)
c.high_(1, 5)
c.freq_(30, 3)
c.lfoParams_(3, -0.95, 0.85, 6)
c.fadeTime
c.fadeTime_(5)
c.lfo_(SinOsc, thisFadeTime:1)
c.lfo_(LFTri, 0.4,thisFadeTime:6,onComplete: {"finished!".postln})
c.toggleLfo(3)
c.toggleSource
c.value_(-0.8888)
c.controlKind

c.clear(5)
c.resume
c.release

c.mixSynth
c.release
c.free
*/
/* SCRATCH

~cMixBus = CtkControl(1).play;
~cInBus = CtkControl(2).play;

d = CtkSynthDef(\control_mix, { arg outbus, inbus, ctlSrcDex=0, staticVal=0, staticLag=0.1, lfoDex = 0, lfoLag=0.1, ctlFade=0.1;
	var staticSrc, lfo1, lfo2, mix;
	staticSrc = Lag.kr(staticVal, staticLag);
	#lfo1, lfo2 = In.kr(inbus,2);
	lfoSrc = SelectX.kr(Lag.kr(lfoDex, lfoLag), [lfo1, lfo2]);
	mix = SelectX.kr(Lag.kr(ctlSrcDex, ctlFade), [staticSrc, lfoSrc]);
	Out.kr(outbus, mix);
})
(
~lfoDefs = [ SinOsc, Impulse, LFSaw, LFPar, LFTri, LFCub, LFNoise0, LFNoise1, LFNoise2 ].collect{|ugen|
	CtkSynthDef(\controlfader_LFO_ ++ ugen.asSymbol, {
		|outbus, freq=0.1, low=0, high=1|
		Out.kr(outbus, ugen.kr(freq).range(low, high));
	})
}
)
~cMixer = d.note.outbus_(~cMixBus).inbus_(~cInBus).play;

~lfo1 = ~lfoDefs[0].note.outbus_(~cInBus.bus).freq_(132).low_(-0.5).high_(1).play
~lfo2 = ~lfoDefs[7].note.outbus_(~cInBus.bus+1).freq_(9).low_(-0.75).high_(0.5).play

~cMixer.val_(0.7777)
~cMixer.ctlFade_(3)
~cMixer.mixDex_(1)

~cMixer.mixDex_(2)
~cMixer.mixDex_(1)

s.scope(2,0,rate:'control')

~cMixer.free
~lfo1.free
~lfo2.free

curCtlDex
curLfoDex

~lfoDefs.do{|me|me.synthdefname.postln}




(
var curCtlType = 0, // 0: static, 1: LFO
curLfoDex = 1,
nextCtlType = 0; // 0: static, 1: LFO

switch( nextCtlType,
	0, { // static next
		switch( curCtlType,
			0, { // static value > static value
				~cMixer.ctlLag_(ctlLag).staticVal_(newVal);
			},
			1, { // lfo > static value

			}

		)
	},
	1 { // LFO next
			switch( curCtlType,
			0, { // static val > LFO
				~lfoDefs[lfoType].outbus_(~cInBus.bus + curLfoDex)
				~cMixer.ctlLag_(ctlLag).lfoSwitchLag_(0) },
				}, // static now
			1, { // LFO > LFO

			}, // lfo already now
		)
	}
);
*/