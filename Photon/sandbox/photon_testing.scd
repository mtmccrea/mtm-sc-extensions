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

({
	p =
	3.collect{
		Photon(
			numpars: 5.rand + 3,
			dur: 140,
			amp: -38.dbamp,
			dec_curve: -10,
			dec_scale: 0.5,
			attack: 0.0001,
			outbus: 0,
		)
	};
	1.wait;
	p.do{|me| me.flux_(2.rand+3).gustSpread_(10).chaos_(0.5)};
}.fork
)
p.do(_.chaos_(1))
p.do(_.chaos_(0))

p.do(_.flux_(15.rand+3))


p.do{ |photon| photon.setKey_( "c5", 'major' ) }
p.do{ |photon| photon.setKey_( "c4", 'minor' ) }
p.do{ |photon| photon.setKey_( "c4", 'fifth-major' ) }

p.do{ |photon| photon.setKey_( "c5", 'major' ) }
p.do{ |photon| photon.setKey_( "c5", 'minor' ) }
p.do{ |photon| photon.setKey_( "c5", 'fifth-major' ) }
p.do{ |photon| photon.setKey_( "c6", 'fifth-major' ) }

( // slow, dark
p.do{|photon|
	photon.flux_(rrand(0.3, 1))
	.attack_(rrand(0.01, 0.1))
	.gustSpread_(20)
	.chaos_(0.2)
	.decay_scale_(5);
	photon.phaseSynth.modMax_( 0 );
	photon.setKey_( "c3", 'melo-minor' );
};
)

( // brightening
p.do{|photon|
	photon.flux_(2.rand+2).gustSpread_(15).chaos_(0.5).decay_scale_(2);
	photon.setKey_( "c5", 'fifth-major' );
	photon.phaseSynth.modMax_( pi/6 ).modRate_( 6 );
};
)

( // high energy
p.do{|photon|
	photon.flux_(3.rand+4).gustSpread_(15).chaos_(0.5).decay_scale_(0.75);
	photon.setKey_( "c6", 'major' );
	photon.phaseSynth.modMax_( pi/3 ).modRate_( 9 );
};
)


(
p.do{ |photon|
	photon.partials.do{ |par|
		var h = par.harmonic;
		par.parfreq_(
			{
				var root;
				root = photon.key.choose; // simple random
				// weight toward consonant (parch scale)
				// root = photon.key.wchoose({|i| (photon.key.size - (i/4)) / photon.key.size} ! photon.key.size);
				root = photon.key.wchoose(({|i| (photon.key.size - (i/19)) / photon.key.size} ! photon.key.size).normalizeSum);
				(root*h).postln
			}.value
		);
	};
	"".postln
}
)

7.do{|i|100.collect({0.0.betarand(1.0, 0.4 * (i+1), i+0.4 * (i+1)).postln}).plot(discrete:true, minval:0, maxval: 1)}

p.gustSpeed_(1)

p.minGust_(-10)

p.free


({
	p =
	4.collect{
		Photon(
			numpars: 2.rand + 3,
			dur: 140,
			amp: -18.dbamp,
			dec_curve: -3,
			dec_scale: 0.5,
			attack: rrand(0.01, 0.7),
			outbus: 0,
		)
	};
	1.wait;
	p.do{|me| me.flux_(2.rand+3).gustSpread_(10).chaos_(0.5)};
}.fork
)
p.do(_.chaos_(1))
p.do(_.chaos_(0))

p.do(_.flux_(15.rand+3))
p.do(_.flux_(0.3))

Resonz


(
SynthDef('dynKlank', { arg
	    freqs (#[100, 200, 300, 400]),
	    amps (#[1, 0.3, 0.2, 0.05]),
	    rings (#[1, 1, 1, 2]),
	outbus = 0;

	Out.ar(outbus,
		DynKlank.ar(`[freqs, Lag.kr(amps, 2.8), rings],
			// WhiteNoise.ar * -70.dbamp
			LPF.ar(Crackle.ar( MouseX.kr(1,2), 0.001 ), 1800);
			// Crackle.ar( LFDNoise1.kr(2.1.reciprocal).range(1.8, 1.95), 0.001 )
			)
		)
}).add
)
(
x = Synth('dynKlank', [\outbus, 1]);
y = Synth('dynKlank', [\outbus, 0]);
)
x = Synth('dynKlank', [\outbus, a.bus]);
y = Synth('dynKlank', [\outbus, a.bus]);

(
var amplo = -4, amphi = -9;
x.setn(\amps, Array.exprand(4, amplo.dbamp, amphi.dbamp).postln);
y.setn(\amps, Array.exprand(4, amplo.dbamp, amphi.dbamp));
)

y.setn(\freqs, Array.rand(4, 500, 2000));
x.setn(\freqs, Array.rand(4, 500, 2000));

y.setn(\rings, 30.dup(4));
x.setn(\rings, 30.dup(4));

y.setn(\rings, 40.dup(4));
x.setn(\rings, 40.dup(4));

y.setn(\rings, 4.dup(4));
x.setn(\rings, 4.dup(4));
(
y.setn(\rings, 4.collect{rrand(0.03,1.0)});
x.setn(\rings, 4.collect{rrand(0.03,1.0).postln});
)
(
y.setn(\rings, 4.collect{rrand(0.08,0.2)});
x.setn(\rings, 4.collect{rrand(0.08,0.2).postln});
)
(
y.setn(\rings, 4.collect{rrand(3,10.2).postln});
x.setn(\rings, 4.collect{rrand(3,10.2).postln});
)
(
y.setn(\rings, 4.collect{rrand(23,36.2).postln});
x.setn(\rings, 4.collect{rrand(23,36.2).postln});
)

// change amplitudes of partials ... some changes sound like attacks
y.setn(\amps, Array.exprand(4, 0.01, 1));
[a,x].do{|me|me.setn(\amps, Array.exprand(4, 0.01, 1)) }
(
var amplo, amphi, waitMul;
r !? {r.stop};
q !? {q.stop};
amplo = 4;
amphi = 22;
waitMul = 3.2;

r = Routine.run({
	inf.do{
		y.setn(\amps, Array.exprand(4, amplo.dbamp, amphi.dbamp).reverse);
		(rrand(0.5, 0.1)* waitMul).wait;
		// rrand(0.05, 0.1).wait;
	}
});
q = Routine.run({
	inf.do{
		x.setn(\amps, Array.exprand(4, amplo.dbamp, amphi.dbamp).reverse);
		(rrand(0.5, 0.1)*waitMul).wait;
		// rrand(0.05, 0.1).wait;
	}
});
)
r.stop; q.stop;

(
var kind, root, scale;
kind =
'major'
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
// 'fifth-minor'
;
root = "c5";
scale = Array.makeScaleMidi( root.notemidi, kind ).midicps;

y.setn(\freqs, 4.collect{scale.choose});
x.setn(\freqs, 4.collect{scale.choose});

// y.setn(\rings, 4.dup(4));
// x.setn(\rings, 4.dup(4));
)

s.recChannels_(2)
s.recSampleFormat_("int24")
s.prepareForRecord
s.record
s.stopRecording



(
play({
	var root, scale, saw, dist, numPartials = 3;

	/* scale */
	root = "d3";
	scale = Array.makeScaleMidi( root.notemidi, 'fifth-major' ).midicps;

	/* saw */
	saw = VarSaw.ar(
		numPartials.collect{scale.choose}, // freq
		// numPartials.collect{|i| scale.choose * (i+1)}, // freq
		// 440, // freq
		// LFPulse.kr(1, 0, 0.35, 200, 200),
		0,
		// LFTri.kr(0.1).range(0,1), //width
		// MouseX.kr(0,1), //width
		// LFDNoise0.kr(MouseX.kr(0.5, 14)).range(0.1, 0.9), // width
		numPartials.collect{LFDNoise0.kr(MouseX.kr(0.1, 40)).range(0.1, 0.9)}, // width
		// 4.collect{LFDNoise1.kr(MouseX.kr(0.5, 40)).range(0.1, 0.9)}, // width
		0.01
	);

	/* stereo */
	saw = Mix.ar( 4.collect{ Pan2.ar(saw, Rand(-1,1) ) } ) * 28.dbamp;

	/* distortion */
	dist = (saw * MouseY.kr(1, 6)).tanh * MouseY.kr(1, -9.dbamp);
	// dist = (saw * MouseY.kr(1, 12)).distort * MouseY.kr(1, -9.dbamp);
	dist = LeakDC.ar(dist);

	/* toggle between saw and distorted saw */
	Select.ar(
		ToggleFF.kr( Impulse.kr(2.reciprocal) ).poll,
		[saw, dist]
	).dup * -28.dbamp;
});
)

(
SynthDef('dynKlank', { arg
	freqs (#[100, 200, 300, 400]),
	amps (#[1, 0.3, 0.2, 0.05]),
	rings (#[1, 1, 1, 2]),
	outbus = 0;

	Out.ar(outbus,
		DynKlank.ar(`[freqs, amps, rings],
			// WhiteNoise.ar * -70.dbamp
			// Crackle.ar( 2.0, 0.001 )
			VarSaw.ar(
				freqs[0] / 2, // root
				0,
				// LFTri.kr(0.1).range(0,1), //width
				// MouseX.kr(0,1), //width
				LFDNoise0.kr(MouseX.kr(0.5, 14)).range(0.1, 0.9), // width
				-90.dbamp
			)
		)
	)
}).add
)

a = Synth('dynKlank', [\outbus, 0]);
x = Synth('dynKlank', [\outbus, 1]);


(
{SinOsc.ar(1440 , LFNoise2.ar(
	MouseX.kr(10,5000),
	MouseY.kr(1,4)), -50.dbamp) !2
}.play
)


// testing phase distortion
(
s.waitForBoot({

	Photon.loadSynths;
	s.sync;
	p = Photon(
		numpars: 4.rand + 3,
		dur: 140,
		amp: -18.dbamp,
		dec_curve: -3,
		dec_scale: 1.5,
		attack: 0.000001,
		outbus: 0,
	);

	1.wait;
	// p.flux_(2.rand+3).gustSpread_(10).chaos_(0.5);
})
)

p.chaos_(1)
p.chaos_(0.5)
p.chaos_(0)

p.flux_(4.rand+19)

p.flux_(4.rand+9)
p.flux_(3.rand+4)
p.flux_(3.rand+1)
p.flux_(0.5)

p.decay_scale_(0.1)

// turn off phase distortion
p.phaseSynth.modMax_( 0 )

(
p.attack_(0.3);
p.phaseSynth
.modRate_( 125 )
.modMax_( 2pi )
.decayTime_( 2.2 )
.curve_( -3.5 );
)
(
p.flux_(4.rand+19);
p.attack_(0.0003);
p.decay_scale_(0.1);
p.phaseSynth
.modRate_( 50 )
.modMax_( 2pi )
.decayTime_( 0.2 )
.curve_( -6 );
)
(
p.phaseSynth
.modRate_( 7 )
.modMax_( 2pi )
.decayTime_( 2.4 )
.curve_( -2 );
)
(
p.phaseSynth
.modRate_( 3333 )
.decayTime_( 1.4 )
)