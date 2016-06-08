// Spreading impulses with a reflection chain

a = CtkAudio.play(1)
s.scope(1, a.bus)
~resBus = CtkAudio.play(1)
~impBus = CtkAudio.play(1) // source impulse

(
d = CtkSynthDef(\verb_localin, {
	arg outbus = 0, inbus, revTime = 3, decayTime = 2, mix = 0.5, apDecay = 0.2, scaleReflections = 1, dampFreq = 1800, impFreq = 0.257, impOutbus;

	var imp, src, combDels, g, lIn, lOut, delay, combs, ap, out;
	var apDelay = 0.095;
	var apOrder = 6;
	var delMod;

	// imp = Impulse.ar(impFreq, 0.25);
	imp = Dust.ar(impFreq, 0.25);


	src = In.ar(inbus, 1);
	src = SelectX.ar( LFDNoise1.kr(4.reciprocal).range(0,1), src );

	// src = In.ar(0, 1);
	// src = Decay.ar(imp, 0.2, PinkNoise.ar, 0);
	// src = Decay.ar(imp, LFDNoise1.kr(4.2.reciprocal).range(0.05, 0.2), PinkNoise.ar, 0);

	combDels = [0.0297, 0.0371, 0.0411, 0.0437] * scaleReflections;
	// calculate feedback coefficient
	g = 10.pow(-3 * combDels / decayTime);

	lIn = LocalIn.ar(4);

	// mod delays a bit
	delMod = 4.collect{LFTri.kr( rrand(12,21.0).reciprocal ).range(0.85, 1.15)};

	combs = DelayC.ar(src + (lIn * g),
		// combDels.maxItem - ControlRate.ir.reciprocal,
		2.5 - ControlRate.ir.reciprocal,
		combDels - ControlRate.ir.reciprocal * delMod
	);

	combs = LPF.ar(combs, dampFreq); // damping

	combs = LeakDC.ar(combs);

	lOut = LocalOut.ar(combs);

	// ap = combs.sum;
	// apOrder.do({|i|
	// 	ap = AllpassC.ar( ap,
	// 		2.0, //apDelay,
	// 		apDelay.rand * LFTri.kr( rrand(8,17.0).reciprocal ).range(0.9, 1), // mod delays a bit
	// 		apDecay
	// 	);
	// });
	//
	delay = DelayN.ar(src, ControlRate.ir.reciprocal, ControlRate.ir.reciprocal); // make up delay
	// out = (mix.sqrt * ap) + ((1 - mix).sqrt * delay);
	out = (mix.sqrt * combs.sum) + ((1 - mix).sqrt * delay);

	// Out.ar(0, out.dup)
	ReplaceOut.ar(outbus, out);
	// send the source impulse out to be used elsewhere as a trigger
	Out.ar(impOutbus, imp);
})
)

// v = d.note(addAction: \tail).inbus_(a).apDecay_(2).mix_(1).play
v = d.note(addAction: \tail).apDecay_(2).mix_(1).outbus_(0).play
// send to resonance
v = d.note(addAction: \head).apDecay_(2).mix_(1).outbus_(~resBus).play
// send to resonance, with impulse as well
v = d.note(addAction: \head).apDecay_(2).mix_(1).outbus_(~resBus).impOutbus_(~impBus).play
s.scope(2, ~resBus.bus)
v.free
(
// scale reflection and feedback on a scale of 0 > 1
var stretch = 0.5;
v.scaleReflections_( Env([1, 27.253971], 0.5.dup).at(stretch) );
v.decayTime_( Env([1, 26], 0.5.dup).at(stretch) );
)

v.impFreq_(1.5.reciprocal)
v.impFreq_(2.1.reciprocal)
v.impFreq_(1.7)
v.impFreq_(13.7)

// note this is still a mix of what's sent to the reverb, not direct out
v.mix_(0.7)
v.mix_(1)


v.free


// TODO: try this with a method that swaps the amps in a linearly
// interpolated way, not external param setting (which is abrupt)

(
~resDef = CtkSynthDef('dynKlank', { arg
	    freqs (#[100, 200, 300, 400]),
	    amps (#[1, 0.3, 0.2, 0.05]),
	    rings (#[1, 1, 1, 2]),
	outbus = 0, inbus, inscale = 1, mix = 1;

	var in, out, res, impIn;

	in = In.ar(inbus, 1) * inscale;

	res = DynKlank.ar(
		`[	freqs,
			Lag.kr(amps, Rand(0.3, 0.8)),
			rings
		],
		in * inscale
		// WhiteNoise.ar * -70.dbamp
		// Crackle.ar( 2.0, 0.001 )
	);

	out = (mix.sqrt * res) + ((1 - mix).sqrt * in);

	// Out.ar(outbus, res);
	Out.ar(outbus, Pan2.ar(res, LFDNoise3.kr(2.reciprocal)));
})
)

~res = ~resDef.note(addAction: \tail).outbus_(0).inbus_(~resBus).inscale_(-70.dbamp).play;
~res.inscale_(-25.dbamp)
// add some source impulse
~res.mix_(0.85)
~res.mix_(1)
~res.rings = Array.exprand(4, 0.35, 0.75);

// change amplitudes of resonantors randomly
(
r !? {r.stop};
r = Routine.run({
	inf.do{
		~res.amps = Array.exprand(4, 0.05, 1);
		(rrand(0.5, 0.1) * 2.5).wait;
	}
});
)
r.stop;

~res.free

// read in impbus to change freqs in the synth

(
~resDef = CtkSynthDef('dynKlank', { arg
	    freqs (#[100, 200, 300, 400]),
	    amps (#[1, 0.3, 0.2, 0.05]),
	    rings (#[1, 1, 1, 2]),
	outbus = 0, inbus, inscale = 1, mix = 1, impInbus;

	var in, out, res, impIn;

	in = In.ar(inbus, 1) * inscale;

	impIn = In.ar(impInbus, 1);

	res = DynKlank.ar(
		`[	freqs,
			//amps,
			TExpRand.ar(0.05.dup(4), 1.0, impIn), // remember .dup(4)
			rings
		],
		in * inscale
		// WhiteNoise.ar * -70.dbamp
		// Crackle.ar( 2.0, 0.001 )
	);

	out = (mix.sqrt * res) + ((1 - mix).sqrt * in);

	Out.ar(outbus, res);
})
)

~res = ~resDef.note(addAction: \tail).outbus_(0).inbus_(~resBus).impInbus_(~impBus).inscale_(-70.dbamp).play;
~res.inscale_(-18.dbamp)
// add some source impulse
~res.mix_(0.5)
~res.rings = Array.exprand(4, 0.5, 0.75);
~res.rings = Array.exprand(4, 0.25, 0.55);
~res.rings = Array.exprand(4, 0.15, 0.25);

~res.rings = Array.exprand(4, 2.5, 2.75);
~res.rings = Array.exprand(4, 1.5, 1.75);


(
var kind, root, scale;
kind =
// 'major'
// 'minor'
// 'harm-minor'
// 'melo-minor'//only up, use 'minor' for down
// 'blues'
// 'blues-major'
// 'pentatonic'
// 'chromatic'
// 'quartertones'
// //// tuning tables:
// 'just'
// 'fifth'
// //// tuning tables from Apple Logic Pro:
// 'pythagorean'
// 'werckmeister'
// 'indian'
// 'arabic'
// //// tuned scales:
// 'just-major'
// 'just-minor'
// 'fifth-major'
'fifth-minor'
;
root = "c4";
scale = Array.makeScaleMidi( root.notemidi, kind ).midicps;

~res.freqs = 4.collect{scale.choose};
)



(
x = {
    var trig = Dust.ar(MouseX.kr(1, 8000, 1));
    SinOsc.ar(
		            TRand.ar(300, 3000, trig)
        ) * 0.1
}.play;
)
x.free