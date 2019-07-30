// MultiTap, but allowed to multi-channel expand
// MultiTap2  {
//
// 	*ar { arg timesArray, levelsArray, in = 0.0, mul = 1.0, add = 0.0,bufnum;
//
// 		var sampleRate, rBuf;
// 		// if(in.numChannels > 1){"Multichannel input found—MultiTap2 only supports mono!".warn};
//
// 		in.postln;
//
// 		timesArray = timesArray.dereference;
// 		levelsArray = levelsArray.dereference;
// 		rBuf = RecordBuf.ar(in,bufnum,0.0, run: -1.0);
// 		sampleRate = BufSampleRate.kr(bufnum);
//
// 		rBuf.poll;
//
// 		// ^Mix.fill(timesArray.size,{ arg i;
// 		^timesArray.collect{|del,i|
// 			PlayBuf.ar(1, //in.numChannels,
// 				bufnum, -1.0 + rBuf,1.0,
// 				del * sampleRate,
// 			loop: 1)
// 			// .madd(levelsArray.at(i) ? 1.0)
// 			// .madd(mul,add)
// 		}
// 		// }).madd(mul,add);
// 	}
// }

MultiTapN  {

	*ar { arg timesArray, levelsArray, in = 0.0, mul = 1.0, add = 0.0,bufnum, t_reset=0;
		var sampleRate, wrBuf, wrapAt;
		timesArray = timesArray.dereference;
		levelsArray = levelsArray.dereference;
		sampleRate = BufSampleRate.kr(bufnum);
		wrapAt = BufFrames.kr(bufnum); // convert to sample _index_

		wrBuf = BufWr.ar(in, bufnum,
			Phasor.ar(0, -1 * BufRateScale.kr(bufnum), 0, wrapAt)
		);

		^BufRd.ar(1, bufnum,
			(wrBuf + (timesArray*sampleRate)) % wrapAt
		);
	}
}