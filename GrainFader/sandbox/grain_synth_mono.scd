(
// bf_grain
// granulates a b-format file
// NOTE: channels must be split into four mono buffers
~bf_grain = CtkSynthDef(\bf_grain, {
	arg
	buffer,
	out_bus, 			// main out
	out_bus_rev,		// outbus to reverb
	out_bus_hp, 		// headphone out bus
	start=0, end=1,		// bounds of grain position in sound file
	grain_rand = 0,		// gaussian trigger: 0 = regular at grain_rate, 1 = random around grain_rate
	grain_rate = 10, grain_dur = 0.04,
	pos_disp = 0.01,	// position dispersion of the pointer, in seconds
	pitch_rate=1, interp=4,
	wetmix=0, gain=1,
	fadein = 3, fadeout = 3,
	min_range = 0.01,	// min soundfile pointer, as a percentage of 0>1
	mon_gain=1,			// monitor gain, for headphones
	gate = 1;

	var
	env, grain_dens, amp_scale, trig, buf_dur,
	pos_rate, pos, pos_lo, pos_hi, sig, out, rev;

	// envelope for fading output in and out
	env = EnvGen.kr(Env([0,1,0],[fadein, fadeout], \sin, 1), gate, doneAction: 0);

	// calculate grain_rate
	grain_dens = grain_rate * grain_dur;

	amp_scale = grain_dens.reciprocal.clip(0, 1);

	// lag the amp_scale, we do it more in its way up
	// amp_scale = LagUD.kr(amp_scale,	grain_dur * 100, grain_dur);

	// gaussian trigger
	// grain_rand = 0 regular at grain_rate
	// grain_rand = 1 random around grain_rate
	trig = GaussTrig.ar(grain_rate, grain_rand);

	// calculate buffer duration
	buf_dur = BufDur.kr(buffer);

	// calculate position pointer rate for selected segment
	pos_rate = (buf_dur * ((end - start).abs.max(min_range))).reciprocal;

	// use line to go from start to end in buffer
	pos = LFSaw.ar(pos_rate, 1).range(start, end);

	pos_lo = pos_disp * 0.5.neg;
	pos_hi = pos_disp * 0.5;

	// add randomness to position pointer, make sure it remains within limits

	pos = pos + TRand.ar(pos_lo, pos_hi, trig);
	pos = pos.wrap(start , end);

	sig = GrainBufJ.ar(1, trig, grain_dur, buffer, pitch_rate , pos, 1, interp, grainAmp: amp_scale);

	sig = sig * env;

	Out.ar( out_bus_hp, (sig * mon_gain).dup(2) ); // out to headphones, independent mon_gain

	sig = sig * gain;

	// balance between dry and wet routing
	out = sig * (1 - wetmix).sqrt;
	rev = sig * wetmix.sqrt;

	// send signals to outputs
	Out.ar( out_bus,		out );
	Out.ar( out_bus_rev,	rev );

});
)

~buf = CtkBuffer.playbuf("/Users/admin/Documents/Recordings/Suyama_Space/stone_rub_res.wav").load;
(
x = ~bf_grain.note
.buffer_(~buf)
.out_bus_(0) 			// main out
.out_bus_rev_(1)		// outbus to reverb
.out_bus_hp_(2) 		// headphone out bus
.play
)

x.start_(0.52).end_(0.54)		// bounds of grain position in sound file
x.grain_rand_(0.8)		// gaussian trigger: 0 = regular at grain_rate, 1 = random around grain_rate
x.grain_rate_(14).grain_dur_(0.1)
x.pos_disp_(0.1)		// position dispersion of the pointer, in seconds
x.pitch_rate_(3.4).interp_(4)

x.wetmix_(0.5)
x.gain_(-3.dbamp)
x.mon_gain_(-3.dbamp) // monitor gain, for headphones

gate = 1
x.release



// TouchOSC interface for granulator by Mike McCrea
// requires TouchOSC class
// this code is ready to use 2 ipads for control of two
// independent synths

~tosc.free

~tosc = TouchOSC("169.254.50.164", 9000); // << iPad's ip/port
~tosc.debug_(true) // useful the first time around


/* specifying control types, ranges, labels, etc. */

(
// tosc1 -------------------------------------------------------------------
// add controls to the library, with mapping Spec

// global controls
~tosc.addCtl( \mon_gain,		\fader,		'/1/mon_gain',		ControlSpec(-inf, 6, \db, 0, -90));
~tosc.addCtl( \xfade,			\fader,		'/1/xfade',			ControlSpec(0, 1, \lin, 0,0));

// granulation controls
[1,2].do{ |i|
~tosc.addCtl( \play++i,			\toggle,	'/1/play'++i);
~tosc.addCtl( \gain++i,			\fader,		'/1/gain'++i,		ControlSpec(-inf, 6, \db, 0, -90));
~tosc.addCtl( \grn_rate++i,		\fader, 	'/1/grn_rate'++i,	ControlSpec(1.0, 100.0, \lin, 0, 10.0, "grn/sec"));
~tosc.addCtl( \grn_dur++i,		\fader, 	'/1/grn_dur'++i,	ControlSpec(0.02, 2.0, \exp, 0, 0.5, "sec"));
~tosc.addCtl( \grn_rand++i,		\fader, 	'/1/grn_rand'++i,	ControlSpec(0.0, 1.0, \lin, 0, 0));
~tosc.addCtl( \dispersion++i,	\fader, 	'/1/dispersion'++i,	ControlSpec(0.0, 0.9, \lin, 0, 0));
~tosc.addCtl( \pitch++i,		\fader, 	'/1/pitch'++i,		ControlSpec(0.3, 4.0, \lin, 0, 1));
~tosc.addCtl( \pos_rate++i,		\fader,		'/1/pos_rate'++i,	ControlSpec(0.0, 3.0, \lin, 0.03, 1.0));
~tosc.addCtl( \start++i,		\fader,		'/1/start'++i,		ControlSpec(0.0, 1.0, \lin, 0.03, 0.0));
~tosc.addCtl( \end++i,			\fader, 	'/1/end'++i,		ControlSpec(0.0, 1.0, \lin, 0.03, 1.0));
~tosc.addCtl( \aux++i,			\fader,		'/1/aux'++i,		ControlSpec(0.0, 1.0, \lin, 0, 0), "aux");
~tosc.addCtl( \inv++i,			\toggle,	'/1/inv'++i );
~tosc.addCtl( \mon++i,			\toggle,	'/1/mon'++i );
~tosc.addCtl( \buf_in++i,		\toggle,	'/1/buf_in'++i );
};

~sfnames = ['one', "two", 'three', 'one1', "two1", 'three1', 'one2', "two2", 'three2', 'one3', "two3", 'three3']

( // soundfiles
~addSFButtons = {
	var col = 0;
	~sfnames.do{|name, i|
		var numColumns = 4;
		var rowsPerColumn = 3, row;
		row = (i % rowsPerColumn);
		if( row == 0, { col = col+1 });

		~tosc.addCtl( \sf++i,  \multipush,
			// just using one column per multipush
			format( "/1/multipush%/%/1", col, row +1 ).asSymbol,
			label: name
		);
	}
};
~addSFButtons.();
)

/* mapping controls to the synth */

// NOTE: synth setup is below

// targets that are symbols map straight to synth params
// targets that are functions receive the object and mapped val as args

// function to connect synth parameters automatically
(
~connectSynth = { arg tosc, synth1, synth2;
	[synth1, synth2].do{|synth, i|
		var j;
		j = i+1;
		tosc.connect( synth,
			\buf_in++j,		\recv_buf,
			\play++j,		\gate,
			\gain++j,		{|obj, val| obj.amp_(val.dbamp) },
			\grn_rate++j,	\grain_rate,
			\grn_dur++j,	\grain_dur,
			\grn_rand++j,	\grain_rand,
			\dispersion++j,	\pos_disp,
			\pitch++j,		\pitch_rate,
			\pos_rate++j,	\pos_rate_scale,
			\inv++j,		{ |obj, val|
				obj.pos_rate_scale_(
					switch( val,
						0, { obj.pos_rate_scale.abs },
						1, { obj.pos_rate_scale.abs.neg },
					)
				);
			},
			\start++j,		\start,
			\end++j,		\end,
			\aux++j,		\wetmix,
			\mon++j,		\mon_amp,
		)
	};
};
)
// connecting global settings to the GrainFader object
~tosc.connect( ~GrainFader,
	\monGain,	{|obj, val| obj.monSynth.amp_(val.dbamp) },
	\xfade,		{|obj, val| obj.balance_(val) },
)
// connect soundfile swapping controls
~sfnames.do{ |name, i|
	~tosc.connect( ~GrainFader,
		\sf++i,	{|obj, val|
			(val == 1).if{ obj.swapBuf_( i ) }
		}
	)
}

/* disconnecting controls */

// one at a time
~tosc.disconnect(\gain)
// reconnect..
~tosc.connect( ~synth, \gain, {|obj, val| obj.gain_(val.dbamp) });

// or many at once
~tosc.disconnect(\gain, \play)
// reconnect
~tosc.connect( ~synth, \gain, {|obj, val| obj.gain_(val.dbamp) });
~tosc.connect( ~synth, \play, \gate);


~tosc.disconnectAll // remove all connections of controls to their objects


~tosc.free // frees all mappings and responders

~sfnames.do{ | name, i |
	~tosc.connect( ~GrainFader,
		\sf++i,
		{ |obj, val| (val==1).if{ obj.buffer_(~bufs[i]) } }
	)
};

///////////////////////////////////////////////////////////////////////
// Synth Definitions
// (
// s.waitForBoot({
// 	fork {
// 		thisProcess.interpreter.executeFile("/Users/juan/AeroFS/Files/Projects/Active/SpringConcert2014/synth_v5.scd");

// 		thisProcess.interpreter.executeFile("/Users/juan/AeroFS/Files/Projects/Active/SpringConcert2014/BFRevCtkATK.scd");

// 		s.sync;
// 		~decoder = FoaDecoderKernel.newUHJ;
// 		s.sync;

// 		~uhj_decoder =	CtkSynthDef(\uhj_decoder, { arg outbus, inbus;
// 			var decoder;
// 			Out.ar(outbus, FoaDecode.ar(In.ar(inbus, 4), ~decoder));
// 		});
// 	}
// })
// )

////////////////////////////////////////////////////////////
// boot server load synth and create ouput audio buses
// s.options.numOutputBusChannels_(32);
s.boot
s.makeGui;

// load synth
thisProcess.interpreter.executeFile("/Users/juan/AeroFS/Files/Projects/Active/SpringConcert2014/synth_v5.scd");


// create audio buses OR edit to right output channels
// a = CtkAudio.play(4);
// b = CtkAudio.play(4);
// c = CtkAudio.play(2);
// a = 12;
// b = 16;
// c = 0;
a = 0;
b = 4;
c = 8;

///////////////////////////////////////////////////////////////////////
// buffers
// collect each channel individually from 4-chan file
(
~path = "/Users/juan/AeroFS/Files/Projects/Active/SpringConcert2014/Snd/";
// "/Volumes/DXARTS\ Events/2014_04_17_music_of_today/sc/Snd/";

~files = [
"01_train_fwd_bwd_bf_48k_hpf.wav",
"02_big_low_noise_48k_hpf.wav",
"05_on_platform_48k.wav",
"06_lluvia_01_seg_F_brightness_BF_48k.wav",
"08_lluvia_salta_seg_02_flatness_BF_48k.wav",
"12_marbels_rtt_bf_48k.wav",
"13_rst-1-rtt-bf_48k.wav",
"14_rst-2-rtt-bf_48k.wav",
"ballard_loop.wav",
"bird_showers_loop.wav",
"braid_hp_1_loop.wav",
"braid_hp_2_loop.wav",
"braid_hp_ls_1_loop.wav",
"braid_hp_ls_2_loop.wav",
"cannery_loop.wav",
"forest_ambience_hp_loop.wav",
"forest_wood_1_hp_loop_rtt.wav",
"forest_wood_2_hp_loop_rtt.wav",
"leith_hp_loop.wav",
"th_showers_loop.wav"
];

~bufs = ~files.collect{ arg file;
		4.collect{|i|
			CtkBuffer.playbuf(
				~path ++ file,
				channels: i
			).load(sync: true);
		};
	};
)

///////////////////////////////////////////////////////////////////////
// run synths

// reverb
// (
// ~rev = ~bfReverbAtk.note
// .predelay_(0.05).t60low_(2.2).t60high_(1.5)
// .diffusion_(1.0).diffusionLR_(1.0).diffusionFB_(1.0)
// .mod_(1.0).mix_(1.0).cutoff_(3000)
// .in_bus_(b).out_bus_(a).play;
// )

// synth 1
(
~synth_1 = ~bf_grain.note
.buffer_w_(~bufs[0][0]).buffer_x_(~bufs[0][1]).buffer_y_(~bufs[0][2]).buffer_z_(~bufs[0][3])
.out_bus_(a).out_bus_rev_(b).out_bus_hp_(c)
.grain_trig_(0.5).grain_dur_(0.5).grain_rate_(50).grain_disp_(0.0)
.start_(0.8).end_(0.9)
.angle_(0).azimuth_(0).elev_(0)
.prox_hpf_lo_(25).prox_hpf_hi_(60).prox_dist_lo_(0.2).prox_dist_hi_(5.0)
.mix_(0).gain_scale_(6.dbamp).play;

~synth_2 = ~bf_grain.note
.buffer_w_(~bufs[1][0]).buffer_x_(~bufs[1][1]).buffer_y_(~bufs[1][2]).buffer_z_(~bufs[1][3])
.out_bus_(a).out_bus_rev_(b).out_bus_hp_(c)
.grain_trig_(0.5).grain_dur_(0.5).grain_rate_(50).grain_disp_(0.0)
.start_(0.8).end_(0.9)
.angle_(0).azimuth_(0).elev_(0)
.prox_hpf_lo_(25).prox_hpf_hi_(60).prox_dist_lo_(0.2).prox_dist_hi_(5.0)
.mix_(0).gain_scale_(6.dbamp).play;
)


///////////////////////////////////////////////////////////////////////
// connect synth parameters
~connectSynth.(~tosc, x);
// ~connectSynth.(~tosc2, ~synth_2);

///////////////////////////////////////////////////////////////////////
// freeing synths and memory

// d.free

// ~rev.free;

~synth_1.free;
~synth_2.free;

~bufs.do({arg buf; buf.do(_.free)});

s.quit;

///////////////////////////////////////////////////////////////////////
// SCRATCH - test parameters
(
var n = 12;
~synth_1.buffer_w_(~bufs[n][0]).buffer_x_(~bufs[n][1]).buffer_y_(~bufs[n][2]).buffer_z_(~bufs[n][3]);
)

~synth_1.grain_rate_(50)
~synth_1.grain_dur_(0.5)
~synth_1.grain_disp_(0.0)
~synth_1.grain_trig_(0.0)

~synth_1.start_(0).end_(1)
~synth_1.gain_(6.dbamp)
~synth_1.gain_scale_(6.dbamp)
~synth_1.angle_(0.0pi)
~synth_1.azimuth_(0.0pi)
~synth_1.elev_(0.0pi)

~synth_1.free
~synth_2.free


~synth_1.prox_hpf_lo_(50)
~synth_1.prox_hpf_hi_(120)
~synth_1.prox_dist_lo_(0.2)
~synth_1.prox_dist_hi_(5.0)

~synth_1.prox_hpf_lo_(50).prox_hpf_hi_(120)

// test headphone output
// it should come up when gate is 0
~synth_1.out_bus_(0)
~synth_1.out_bus_rev_(4)
~synth_1.out_bus_hp_(8)

~synth_1.gate_(1)
~synth_1.mix_(1)
~synth_1.free