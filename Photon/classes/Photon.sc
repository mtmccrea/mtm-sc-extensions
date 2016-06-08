// handles to build in:
// likelihood of add vs. even partials?
// weighting of partial low toward high
// attack vs decay length
// weighting consonant vs. dissonant partials

Photon {
	classvar parDef, trigDef, phaseDef;
	//copyArgs
	var <numpars, <dur, <amp, <dec_curve, <dec_scale, <attack, <outbus, <server;
	var <sunBus, <group, <trigBus, <gustBus, <trigSynth;
	var <phaseSynth, <phaseBus;
	var <keylist, <harms, <key, scale, <partials, <isPlaying = true;


	*new { | numpars, dur, amp, dec_curve, dec_scale, attack, outbus, server |
		^super.newCopyArgs(numpars, dur, amp, dec_curve, dec_scale, attack, outbus).init(server)
	}


	*loadSynths {

		phaseDef = CtkSynthDef( \photon_phase, { arg outbus, trigBus, modRate = 13, modMax = 2pi, decayTime = 2, curve = 3;
			var trig, mod, phase;
			trig = In.kr(trigBus);
			mod = EnvGen.kr(
				Env( [modMax, modMax, 0], [0, decayTime], curve, loopNode: 0 ),
				trig
			);
			phase = LFDNoise0.kr( modRate ).range( 0, mod );
			Out.kr( outbus, phase );
		});

		parDef = CtkSynthDef( \photon_par, {
			arg outBus, gustBus, trigBus, parfreq, harmonic, decay_scale=1,
			transpose=1, decay_curve= -4, parAmpScale=1, amp = 1, attack = 0.00001,
			phaseBus;

			var par, gustSpd, trig, sig, env, freq;
			var scaleByHarmonic;
			var randPhase;

			// windspd = LFDNoise3.kr(LFNoise1.kr(windspeed, 0.5, 0.5), 0.5, 0.5);
			gustSpd = In.kr( gustBus );

			trig = In.kr(trigBus);

			// parfreq is key * harmonic
			freq = parfreq * transpose;
			// add drift to freq to cause some beating
			freq = freq + LFDNoise1.kr(32.reciprocal).range(-7, 7);

			// par = SinOscFB.ar( freq, MouseX.kr(0, pi/12), 0.5 );
			// par = SinOsc.ar( freq, Rand(0, 2pi), 0.5 );

			randPhase = In.kr(phaseBus, 1);
			par = SinOsc.ar( freq, Rand(0, 2pi) + randPhase, 0.5 );

			// scaleByHarmonic = harmonic.reciprocal;
			// scaleByHarmonic = (2**(harmonic*0.5)).reciprocal;
			scaleByHarmonic = (2**(harmonic*0.35)).reciprocal;

			// partial env
			env = EnvGen.kr(
				Env.perc(
					attack,
					// release time
					gustSpd * 15 * decay_scale * scaleByHarmonic + TRand.kr(-0.2, 0.2, trig),
					// peak level - high wind = louder
					gustSpd * 17 * scaleByHarmonic,
					// curve - higher partials = faster rolloff
					// (decay_curve - harmonic)
					decay_curve
				),
				// gate
				trig,
				// levelScale
				(gustSpd * TRand.kr(0.9, 1.0, trig)) * harmonic.reciprocal
			);

			sig = par * env * amp * parAmpScale;

			// Out.ar( outBus, sig.dup );
			Out.ar( outBus, Pan2.ar(sig, Rand(-1.0, 1.0) ) );
		});


		trigDef = CtkSynthDef( \photon_trig, {
			arg gustBus, trigBus, fluxRate=1, chaos = 0.1, gustSpeed = 1, minGust = 0.2;
			var gustRate, trig;

			gustRate = LFDNoise3.kr(LFNoise1.kr(gustSpeed, 0.5, 0.5)).range(minGust, 1.0);
			trig = GaussTrig.kr(freq: gustRate * fluxRate, dev: chaos);

			Out.kr( gustBus, gustRate );
			Out.kr( trigBus, trig);
		});
	}


	init { | argserver |
		{
			server = argserver ?? Server.default;
			group = CtkGroup.play(server: server); // group for just this bell, at tail of master group
			trigBus = CtkControl.play(1);
			gustBus = CtkControl.play(1);
			phaseBus = CtkControl.play(1);
			0.05.wait; server.sync;

			trigSynth = trigDef.note(addAction: \head, target: group)
			.gustBus_(gustBus)
			.trigBus_(trigBus)
			.fluxRate_(1)
			.chaos_(0.1)
			.gustSpeed_(2);
			server.sync;

			phaseSynth = phaseDef.note(addAction: \after, target: trigSynth)
			.outbus_( phaseBus )
			.trigBus_( trigBus )
			.modRate_( 13 )
			.modMax_( 2pi )
			.decayTime_( 2 )
			.curve_( 3 );
			server.sync;

			// // Parch 11-tone limit scale
			// scale = [
			// 	1/1, 12/11, 11/10, 10/9, 9/8, 8/7, 7/6, 6/5, 11/9,
			// 	5/4, 14/11, 9/7, 4/3, 11/8, 7/5, 10/7, 16/11, 3/2,
			// 	14/9, 11/7, 8/5, 18/11, 5/3, 12/7, 7/4, 16/9, 9/5,
			// 	20/11, 11/6, 2/1
			// ];

			// equal temperment
			scale = [
				1/1, 16/15, 9/8, 6/5, 5/4, 4/3, 7/5,
				3/2, 8/5, 5/3, 16/9, 15/8, 2/1
			];

			// from well-tuned piano
			scale = [1/1, 567/512, 9/8, 147/128, 21/16, 1323/1024, 189/128, 3/2, 49/32, 7/4, 441/256, 63/32];

			// key = 340 * [1/1, 3/2, 4/3, 6/5, 16/9];
			// key = 140 * [1/1, 3/2, 4/3, 6/5, 16/9];

			key = PitchClass('c', 4).freq * scale;

			// odd harmonics comprise the partials for now
			// harms = numpars.collect({ |i| (i*2)+1 });
			// odd harmonics more spread between them
			// harms = numpars.collect({ |i| (i*4)+1 });
			// even
			// harms = numpars.collect({ |i| (i*3)+2 });
			// odd and even
			harms = numpars.collect({ |i| (i*4)+ (2.rand+1) });

			("Bell Harmonics: " ++ harms).postln;

			// gather partials
			partials = harms.collect({ | harm, i |

				parDef.note(addAction: \after, target: trigSynth)
				.outBus_(outbus)
				.gustBus_(gustBus).trigBus_(trigBus)
				// each partial stems from a different note in the key
				.parfreq_(
					{ var root;
						// root = key.choose; // simple random
						// weight toward consonant (parch scale)
						root = key.wchoose({|i| (key.size - (i/4)) / key.size} ! key.size);
						keylist=keylist.add(root);
						(root*harm).postln
				}.value)
				.harmonic_(harm)
				.decay_curve_(dec_curve)
				.decay_scale_(dec_scale)
				.parAmpScale_(numpars.reciprocal)
				.amp_(amp)
				.attack_(attack)
				.phaseBus_( phaseBus )
			});

			0.3.wait;
			trigSynth.play;
			server.sync;
			phaseSynth.play;

			0.1.wait;
			partials.do(_.play);
			// schedule this photon stream's release
			SystemClock.sched( dur, { this.free } );

		}.fork;
	}

	stop { |releaseTime=3| this.free(releaseTime) }

	free { |releaseTime=3|
		{	"freeing a photon".postln;
			releaseTime.wait;
			group.deepFree;
			[trigBus, gustBus, phaseBus].do(_.free);
			isPlaying = false;
		}.fork
	}

	amp_ { | ampDB | // in dB
		partials.do( _.amp_(ampDB.dbamp) );
	}

	decay_curve_ { |curveNum| partials.do( _.decay_curve_(curveNum) ) }

	decay_scale_ { |scaler| partials.do(_.decay_scale_(scaler)) }

	attack_ { |att| partials.do(_.attack_(att)) }

	// a multiplying factor to the trigger rate of the photon stream
	// 1 is default
	flux_ { | flux |
		trigSynth.fluxRate_(flux);
	}

	// control the rate at which gust speed changes between max and min
	// i.e. how fast the gust speed changes within its bounds
	gustSpeed_ { |speed|
		trigSynth.gustSpeed_(speed);
	}

	// chaos of the photon trigger 0 is periodic, 1 is random
	chaos_ { |chaos|
		trigSynth.chaos_(chaos);
	}

	minGust_ { |minDB| trigSynth.minGust_(minDB.dbamp) }
	// as as above, but defined as dbrange spread from min>max
	gustSpread_ { |spreadDB| trigSynth.minGust_(1 - spreadDB.neg.dbamp) }

	transpose_ { | ratio=1 | partials.do(_.transpose_(ratio)) }

	// spd is an LFNoise freq
	// windspeed_ { | spd=1 |
	// 	partials.do(_.windspeed_(spd)); trigSynth.windspeed_(spd)
	// }

	parfreqs {
		^partials.collect({ |partialnote| partialnote.parfreq });
	}

	keyfreqs { ^keylist }

	getHarms { ^harms }

	setKey_ { |key = 'c6', scale = 'major', weight = 0|
		var scl, sclSize, scaleDict;

		scaleDict = IdentityDictionary(know: true).putPairs([
			// Parch 11-tone limit scale
			'parch', [
				1/1, 12/11, 11/10, 10/9, 9/8, 8/7, 7/6, 6/5, 11/9,
				5/4, 14/11, 9/7, 4/3, 11/8, 7/5, 10/7, 16/11, 3/2,
				14/9, 11/7, 8/5, 18/11, 5/3, 12/7, 7/4, 16/9, 9/5,
				20/11, 11/6, 2/1
			],
			// equal temperment
			'equal', [
				1/1, 16/15, 9/8, 6/5, 5/4, 4/3, 7/5,
				3/2, 8/5, 5/3, 16/9, 15/8, 2/1
			],

			// from well-tuned piano
			'lmyoung', [
				1/1, 567/512, 9/8, 147/128, 21/16, 1323/1024,
				189/128, 3/2, 49/32, 7/4, 441/256, 63/32
			]
		]);

		scl = scaleDict[scale].notNil.if(
			{
				scaleDict[scale] * key.notemidi.midicps
			},{
				Array.makeScaleMidi( key.notemidi, scale ).midicps;
			}
		);

		sclSize = scl.size;

		partials.do{ |par|
			var harm = par.harmonic;
			par.parfreq_( {
				var root;
				root = (weight > 0).if(
					{
						scl.wchoose(
							sclSize.collect({ |i|
								(sclSize - (i/weight)) / sclSize}
							).normalizeSum
						)
					},{ scl.choose } // simple random
				);
				(root*harm).postln
			}.value );
		};
	}

}

/*
(
s.waitForBoot({

Photon.loadSynths;
s.sync;
g = CtkGroup.new;
	s.sync;
	"done".postln;
	s.scope(2,0)
})
)

(
b= BoatBell(
	numpars:5,
	dur: 10,
	amp: 1,
	dec_curve: -10,
	dec_scale: 1.6,
	attack: 0.0001,
	outbus: 0,
	// sunBus: 100,
	m_group: g,
	// ghostbus: 101
)
)

b.flux_(8)

(
b= 4.collect{ BoatBell(
	numpars:5,
	dur: 10,
	amp: 1,
	dec_curve: -10,
	dec_scale: 1.6,
	attack: 0.0001,
	outbus: 0,
	m_group: g,
)
}
)


f = {|speed| b.do(_.flux_(1.0.rand * speed))}

f.(135)
f.(13)

.distort
.tanh
.pow(2)
bandpassed Saw.ar
{SinOsc.ar.wrap(-0.5, 0.5)}.scope
{SinOsc.ar.fold(-0.5, 0.5)}.scope
{SinOsc.ar.clip(-0.5, 0.5)}.scope

*/