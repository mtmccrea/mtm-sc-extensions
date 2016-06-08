+ SiteMachine {

	loadSynthDefs {

		synthLib = CtkProtoNotes(
			SynthDef(\lrSrcSwitch, {
				arg outbus, historybus, inbus, lsrc=0, rsrc=0, fadein=5.5, fadeout=2,
				ampbus, ampmax=1, ampmin=0.1, amp=1, gate=1;
				var env, in, switch;
				env = EnvGen.kr(Env([0,1,0], [fadein, fadeout], \sin, releaseNode: 1), gate, doneAction:2);
				in = In.ar(inbus, 6);
				switch = [
					SelectX.ar(Lag.kr(lsrc, 0.05), [in[0],in[1],in[2]]),
					SelectX.ar(Lag.kr(rsrc, 0.05), [in[3],in[4],in[5]])
				];
				Out.ar(outbus,
					switch * env * amp * LinLin.kr(In.kr(ampbus), 0.0, 1.0, ampmin, ampmax)
				);
				Out.ar(historybus, switch); // no amp mod on history bus so buffered steadily
			}),

			// 2 inbusses for rotate panning
			SynthDef(\showMaterial, {
				arg outbus=0, inbus1, inbus2,
				fadein=0.5, fadeout=2, rotFreq=0.16667,
				ampbus, ampmax=1, ampmin=0.1, amp=1, gate=1;
				var env, in, w,x,y,a,b,c,d, pan;
				env = EnvGen.kr(
					Env([0,1,0], [fadein, fadeout], \sin, releaseNode: 1),
					gate, doneAction:0); // note doneAction: 0
				in = [In.ar(inbus1, 1), In.ar(inbus2, 1)];
				in = in * env * amp * LinLin.kr(In.kr(ampbus), 0.0, 1.0, ampmin, ampmax);
				// B-format encode 2 signals at opposite poles of the circle
				#w, x, y = PanB2.ar(in[0], -0.5) + PanB2.ar(in[1], 0.5); // equivalet to BiPanB2
				#x, y = Rotate2.ar(x, y, LFDNoise3.kr(rotFreq));
				// B-format decode to quad
				#a, b, c, d = DecodeB2.ar(4, w, x, y); // quad decode
				Out.ar(outbus, [a, c]); // 180 degree spread, [a,b/d] for 90
			}),

			SynthDef(\showConv, {
				arg outbus=0, inbus, fadein=5, fadeout=2,
				ampbus, ampmax=1, ampmin=0.1, amp = 1, gate=1;
				var env, in;
				env = EnvGen.kr(
					Env([0,1,0], [fadein, fadeout], \sin, releaseNode: 1),
					gate, doneAction:2
				);
				in = In.ar(inbus, 1);
				in = in * env * amp * LinLin.kr(In.kr(ampbus), 0.0, 1.0, ampmin, ampmax);
				Out.ar(outbus,
					Pan2.ar(in, LFDNoise3.kr(3.reciprocal))
				)
			}),

			SynthDef(\showFBDelay, {
				arg outbus=0, inbus, fadein=0.5, fadeout=2, gate=1, amp = 1;
				var env, in;
				env = EnvGen.kr(
					Env([0,1,0], [fadein, fadeout], \sin, releaseNode: 1),
					gate, doneAction:2
				);
				in = In.ar(inbus, 2);
				Out.ar(outbus, in);
			}),

			SynthDef( \history_pointer, { arg bufnum, wrphasor_bus, rphasor_bus, wrrate=1, read_phsr = -1, t_reset_rd;
				var wrphasor, rphasor;
				// can pause with wrrate
				wrphasor = Phasor.ar(0, BufRateScale.ir(bufnum)*wrrate, 0, BufFrames.ir(bufnum));
				// continuously running read head
				rphasor = Phasor.ar(t_reset_rd, BufRateScale.ir(bufnum), 0, BufFrames.ir(bufnum), wrphasor);

				Out.ar( wrphasor_bus, wrphasor );
				Out.ar( rphasor_bus, rphasor);
			}),

			SynthDef( \history_writer_1ch, { arg inbus=0, wrphasor_bus, bufnum, gate=1;
				var env, src, phasor;
				env = EnvGen.kr(
					Env([0,1,0], [0.1,0.1], \sin, releaseNode: 1),
					gate, doneAction:0
				);
				src = In.ar(inbus, 1)*env; // read in audio source to write to master buffer
				phasor = In.ar( wrphasor_bus, 1 );
				BufWr.ar( src, bufnum, phasor, 1 );
			}),

			SynthDef( \history_bufgrn, {
				arg out=0, sendout, phasor_bus, amp = 1, delay=0.1,
				grn_dur=0.3, sndbuf, bufnum, pntr_lag_back=0.025, pntr_lag_forward=1.5, rate=1,
				freezePntr=0, roffset=0, nudge=0, randur=0.55, gate=1, dust=1, grn_rate = 3,
				envbuf1, envbuf2, ifac=1;
				// sendsweepfreq = 0.22, sendsweepwidth = 1, sendsweeprange = 2;

				var env, bufg, inpos, durRatio, del, pos, trig, sndout;
				var g_rate, g_dur;

				env = EnvGen.kr( Env( [0,1,0],[0.5, 0.5], \sin, 1 ), gate, doneAction: 2 );

				inpos = In.ar(phasor_bus, 1) * (BufFrames.kr(bufnum).reciprocal);
				durRatio = BufDur.kr(bufnum).reciprocal;
				del = LagUD.kr(delay+0.001, pntr_lag_back, 0) * durRatio; // at least 0.0025 delay, convert 0>1

				// only offsets negative values to avoid overtaking master pntr
				inpos = Select.kr(freezePntr, [ inpos, Latch.kr( inpos, freezePntr ) ]);
				pos = (inpos - del).wrap(0.0,1.0);

				pos = ( pos +
					((LFNoise1.kr(20).range(roffset.neg, 0) + nudge) * durRatio)
				).wrap(0,1); // currently no protection from nudging over the master pointer

				// #g_rate, g_dur = LagUD.kr([grn_rate, grn_dur], pntr_lag_back, pntr_lag_forward);
				g_rate = LagUD.kr(grn_rate, pntr_lag_back, pntr_lag_forward);
				// g_dur = LagUD.kr(grn_dur, pntr_lag_forward*0.1, pntr_lag_back);

				trig = Select.kr(dust, [ Impulse.kr(g_rate), Dust.kr(g_rate) ]);

				bufg = BufGrainI.ar(
					trig,
					// randur is a percent of variance in the grain dur, keep low
					grn_dur * LFNoise1.kr(40).range(1, (1+randur)),
					sndbuf, rate, pos,
					envbuf1:envbuf1, envbuf2:envbuf2, ifac:ifac,
					interp:2
				);

				sndout = bufg * env * amp;
				Out.ar( out, sndout ); // need the Env and amp bus? probz not..
				/*Out.ar( sendout,
					PanAz.ar(
						3, // number of processing channels
						in:sndout,
						pos:LFNoise2.kr(sendsweepfreq).range(0,sendsweeprange),
						width:sendsweepwidth,
						orientation:0
					)
				);*/
			}),

			SynthDef(\lrMixer, {arg sterinbus1, sterinbus2, outbus = 0, whichsrc=0;
				var in1, in2;
				in1 = In.ar(sterinbus1, 2); // lr gate switch
				in2 = In.ar(sterinbus2, 2); // history granulator
				Out.ar(outbus,
					SelectX.ar(whichsrc, [in1, in2])
				)
			}),

			SynthDef(\lrModControl, {arg outbus, modfreq=0.07;
				var env;
				env = Env([1,0,1], [0.5,0.5], \sin);
				Out.kr(outbus, IEnvGen.kr( env,
					(LFDNoise3.kr(modfreq).range(0,1) + [0, 2pi/3, 2*2pi/3]).wrap(0.0,1.0)
					)
				);
			}),

			SynthDef(\showAll, {
				arg outbus=0, inbus, sustainamp = 0.13, fadein=0.001, fadeout=2, amp = 1, lpf_freq = 6000, gate=1;
				var env, amps, in;
				env = EnvGen.kr(Env([0,1,sustainamp,0], [fadein, 10, fadeout], \sin, releaseNode: 2), gate, doneAction:2);
				amps = Control.names([\amps]).kr([1,1,1,1,0.5,0.5]);
				in = In.ar(inbus, 6);
				in = LPF.ar(in, lpf_freq);
				in = in * amps * env * amp;
				in = Mix.ar( Pan2.ar(in[0..2]++in[5..3], Array.series(6,-1, 2/5)) * 0.2 );
				Out.ar( outbus, in );
			}),
		)
	}
}