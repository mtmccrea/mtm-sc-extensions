XOver {
	*ar { |in, f=110, lfAmp=1, hfAmp=1|
		var lf, hf;
		lf = LPF.ar(LPF.ar(in, f), f, lfAmp);	// 2-chan
		hf = HPF.ar(HPF.ar(in, f), f, hfAmp);	// 2-chan

	}
}

XOverBands {
	*ar { |in, freqArray, ampArray, k = 1e-06 | // -120.dbamp
		var hpf, lowerBands;

		ampArray = ampArray ?? 4.collect{1};

		if(freqArray.size != (ampArray.size-1)) {
			^(this.asString + format(
				"Mismatch between number of specified xover freqs (%) and band amps (%)",
				freqArray.size, ampArray.size
			)).error
		};

		hpf = in;
		// collect all but the last band (hpf)
		lowerBands = freqArray.collect{|f, i|
			var lpf;
			lpf = RMShelf2.ar(hpf, f, k, ampArray[i]); 	// lowpass
			hpf = RMShelf2.ar(hpf, f, k * -1); 			// highpass, no mul arg yet
			lpf
		};
		lowerBands.postln;
		^lowerBands ++ (hpf * ampArray.last)
		// ^lowerBands;// ++ (hpf * ampArray.last)
		// ^hpf
	}
}

/*
d = CtkSynthDef(\bandSplit, { |f1=120, f2=400, f3=4000, a1=1, a2=1, a3=1, a4=1|
var sig, bands, mix, diff;

	sig = WhiteNoise.ar(0.5);
	// sig = PinkNoise.ar;

	bands = XOverBands.ar( sig,
		[f1,f2,f3],
		[a1,a2,a3,a4]
	);
	mix = Mix.ar(bands);
	diff = sig - bands.sum;

	Out.ar(2, [mix, sig])
})
x = d.note.play

d = CtkSynthDef(\bandSplit, { |f1=120, f2=400, f3=4000, a1=1, a2=1, a3=1, a4=1|
var sig, bands, mix, diff;

	sig = WhiteNoise.ar(0.5);
	// sig = PinkNoise.ar;

	bands = XOverBands.ar( sig,
		[f1,f2],
		[a1,a2,a3]
	);
	mix = Mix.ar(bands);
	diff = sig - bands.sum;

	Out.ar(2, [mix, sig])
})
x = d.note.play

s.scope(2,2)
s.freqscope

x.a1_(-122.dbamp)
x.a2_(-122.dbamp)
x.a3_(-122.dbamp)
x.a4_(-122.dbamp)

x.a1_(-12.dbamp)
x.a2_(-12.dbamp)
x.a3_(-12.dbamp)
x.a4_(-12.dbamp)

x.a1_(0.dbamp)
x.a2_(0.dbamp)
x.a3_(0.dbamp)
x.a4_(0.dbamp)

x.f1_(40)
x.f2_(1500)
x.f3_(8000)

x.free


d = CtkSynthDef(\bandSplit2, { |f1=120, f2=400, f3=4000, a1=1, a2=1, a3=1, a4=1|
var sig, bands, mix, diff;

	sig = WhiteNoise.ar(0.5);
	// sig = PinkNoise.ar;

	bands = BandSplitter4.ar(sig, f1,f2,f3, 8);
	bands = bands * [a1,a2,a3,a4];

	mix = Mix.ar(bands);
	diff = sig - bands.sum;

	Out.ar(2, [mix, sig])
})
x = d.note.play

*/