s.boot;

b.zero
b.free
b = Buffer.alloc(s, s.sampleRate*2);
b = Buffer.alloc(s, s.sampleRate*2+s.options.blockSize);  // This is the current solution

b = Buffer.alloc(s, s.sampleRate*2+s.options.blockSize-1);
b = Buffer.alloc(s, s.sampleRate*2+1);
b = Buffer.alloc(s, s.sampleRate*2+2);
b = Buffer.alloc(s, s.sampleRate*2+(s.options.blockSize/2));

// sample value delays to test, do one below the block size, and one at the end of the ring buffer
[33, 125, 32451] / s.sampleRate

(
x={ |t_reset=0|
	var src, dels;
	src = Decay.ar(Impulse.ar(0.333), 0.1, DC.ar(0.8));
	// src = Decay.ar(Impulse.ar(0.333), 0.1, PinkNoise.ar);
	// src = Impulse.ar(0.2);
	// dels = MultiTap2.ar(`[0.5, 1.0, 1.5], `[1,1,1],
	dels = MultiTapN.ar(

		`[ 0.00074829931972789, 0.0028344671201814, 0.73585034013605, 2 ],
		`[1,1,1,1,1],

		// `[0.75, 1.0, 2],
		// `[1,0.5,0.75, 1],
		src, bufnum: b.bufnum, t_reset: t_reset);
	// dels
	[src]++dels
	// Out.ar(0, dels); // this breaks the function .play
}.play
)
x.set(\t_reset, 1)
b.zero
b.free

s.recChannels_(5)
s.prepareForRecord("~/Desktop/multitap/overlap_test_blocksize-1Pad_mod_negDir.aiff".standardizePath)
s.record
s.stopRecording

s.boot;
b = Buffer.alloc(s, s.sampleRate);
(
{
	    MultiTap.ar(`[1.333, 1.666, 1.99], `[1,1,1],
        Decay.ar(Impulse.ar(0.333), 0.1, PinkNoise.ar), bufnum: b.bufnum)
}.play
)


b = Buffer.alloc(s, s.sampleRate*2);
(
{
	var src, dels;
	src = Decay.ar(Impulse.ar(0.333), 0.1, PinkNoise.ar);
	dels = MultiTap.ar(`[1.333, 1.666, 1.99], `[1,1,1],
		        src, bufnum: b.bufnum);
	// Out.ar(0, dels);
	[src]++dels
}.play
)
(
d = CtkSynthDef(\testTap, { |t_reset=0|
	var src, dels;
	// src = Decay.ar(Impulse.ar(0.333), 0.1, PinkNoise.ar);
	src = Impulse.ar(0.2);
	// dels = MultiTap2.ar(`[0.5, 1.0, 1.5], `[1,1,1],
	dels = MultiTapN.ar(`[0.75, 1.0, 2], `[1,1,1],
		src, bufnum: b.bufnum, t_reset: t_reset);
	// dels
	[src]++dels
	// Out.ar(0, dels); // this breaks the function .play
})
)
d.synthdef.dumpUGens

[33, 125, 32451] / s.sampleRate


/* DelTapWr/Rd */

b = Buffer.alloc(s, s.sampleRate * 2, 1);
// b = Buffer.alloc(s, s.sampleRate * 2 + s.options.blockSize, 1);

(
// write a signal into a delay, tap it at mutiple times
SynthDef(\testDelTap, {arg buffer;
	var src, tapPhase, tap1, tap2, tap3;
	src = Decay.ar(Impulse.ar(0.333), 0.1, DC.ar(0.8));
	tapPhase = DelTapWr.ar(buffer, src);
	#tap1, tap2, tap3 = DelTapRd.ar(buffer, tapPhase,
		[0.00074829931972789, 0.5, 2], // tap times 33, 44100, 88200 samps at Fs 44.1k,
		1, [1,1,1] // muls for each tap
	);
	Out.ar(0, [src, tap1, tap2, tap3])
}).send(s);
)

x = Synth(\testDelTap, [\buffer, b]);

s.recChannels_(4)
s.prepareForRecord("~/Desktop/BAK_EXCLUDE/multitap/delTap1.aiff".standardizePath)
// s.prepareForRecord("~/Desktop/BAK_EXCLUDE/multitap/delTap1_blocksizePad.aiff".standardizePath)
s.record
s.stopRecording

x.free;
b.free;


/* MultiTap */

b = Buffer.alloc(s, s.sampleRate * 2, 1);
// b = Buffer.alloc(s, s.sampleRate * 2 + s.options.blockSize, 1);

(
// write a signal into a delay, tap it at mutiple times
SynthDef(\testMultiTap, {arg buffer;
	var src, taps;
	src = Decay.ar(Impulse.ar(0.333), 0.1, DC.ar(0.8));
	taps = MultiTap.ar(
		`[0.2, 0.5, 2], // tap times
		`[0.707,0.6,0.5], // muls for each tap
		src, bufnum: b.bufnum
	);
	Out.ar(0, [src, taps])
}).send(s);
)

x = Synth(\testMultiTap, [\buffer, b]);

s.recChannels_(2)
s.prepareForRecord("~/Desktop/BAK_EXCLUDE/multitap/MultiTap.aiff".standardizePath)
// s.prepareForRecord("~/Desktop/BAK_EXCLUDE/multitap/MultiTap_blocksizePad.aiff".standardizePath)
s.record
s.stopRecording

x.free;
b.free;


// Compare manually buffered allpass to AllpassN
b.free;
b = Buffer.alloc(s, s.sampleRate*1+s.options.blockSize);  // This is the current solution

(
// write a signal into a delay, tap it at mutiple times
d =SynthDef(\bufApVsAllpass, {arg bufnum, gain=0.5;
	var src, timesArray, levelsArray, rBuf, sampleRate, out, dwr, drd, fb, fb_in,in, ap, del, dsamps;

	// src = Decay.ar(Impulse.ar(0.333), 0.2, DC.ar(1));
	// src = Decay.ar(Impulse.ar(0.333), 0.8, PinkNoise.ar);
	src = Impulse.ar(0.333);
	// src = SinOsc.ar(SampleRate.ir/2, pi/2); // need it to be cosine so samples don't fall on 0
	// src = SinOsc.ar(SampleRate.ir/4, pi/2); // need it to be cosine so samples don't fall on 0

	del = 379 * 29761.reciprocal;// timesArray.dereference;
	sampleRate = BufSampleRate.ir(bufnum);
	dsamps = del * sampleRate;
	dsamps = dsamps.round;

	drd = PlayBuf.ar(1, //in.numChannels,
		bufnum, -1.0, 1.0,
		dsamps,
		loop: 1);

	dwr = drd * gain.neg + src;
	out = drd + (dwr * gain);
	rBuf = RecordBuf.ar(dwr,bufnum,offset:0.0,recLevel:1,preLevel:0, run: -1.0);

	ap = AllpassN.ar(src, 1, dsamps * SampleRate.ir.reciprocal,
		decaytime: (-3 * del)/log10(gain).neg
	);

	Out.ar(0, [src, ap, out])
}).send(s);
)

x = Synth(\bufApVsAllpass, [\bufnum, b.bufnum, \gain, 0.8]);
x.free
s.recChannels_(3)
s.prepareForRecord(("~/Desktop/BAK_EXCLUDE/multitap/apVsbufap" ++ Date.getDate.stamp ++ ".aiff").standardizePath)
// s.prepareForRecord("~/Desktop/BAK_EXCLUDE/multitap/MultiTap_blocksizePad.aiff".standardizePath)
s.record
s.stopRecording
b.free; ~b2.free

x = {SinOsc.ar(22000)}.play
x.free
