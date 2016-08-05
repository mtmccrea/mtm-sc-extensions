(// ~times = (0..5000);
~times = Array.series(50, 35, 3.5419);

~seekToHalf = { |st, end|
		st + (end-st).half.asInt
	};

~itemsAfter = { |seconds|
	var lowIdx=0, halfIdx, highIdx, prevIdx, idxTime, searching=true, cnt=0, res;

	highIdx = ~times.size-1;

	while ( {
		searching
	},{
		halfIdx = ~seekToHalf.(lowIdx, highIdx);

		postf("halfIdx: %, %\n", halfIdx, ~times[halfIdx]);

		idxTime = ~times[halfIdx];

		// #lowIdx, highIdx = [prevIdx, halfIdx].sort;
		if (idxTime < seconds) {
			// landed below threshold
			highIdx = [lowIdx, highIdx].maxItem;
			lowIdx = halfIdx;
		} { // landed above threshold
			lowIdx = [lowIdx, highIdx].minItem;
			highIdx = halfIdx;
		};

		if (halfIdx == prevIdx) {
			searching = false;
			// see which side the index falls on
			res = if(~times[halfIdx] >= seconds) {
				halfIdx
			} {
				halfIdx + 1
			}
		} {
			prevIdx = halfIdx;
			cnt = cnt+1;
		}
	}
	);

	postf("copyFrom: % (%)\n", halfIdx, ~times[halfIdx]);
	cnt
};
)
~itemsAfter.(3000)
~itemsAfter.(rrand(~times[0], ~times.last).postln)

~times[17]


// go to halfway
// is time over threshold?
// if yes, jump back half
// if no, jump forward half