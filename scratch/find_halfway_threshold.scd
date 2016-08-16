/*
This work file was for trimming the drawing machine path to be before or
after a certain point in time, to be able to show the last X seconds
of data points in HistoryList
*/

(
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

	postf("copyFrom: % (%)\n", res, ~times[res]);
	cnt
};

// ~times = Array.series(50000, 35, 3.5419);
~times = Array.series(1, 35, 3.5419);

)
~itemsAfter.(~times[~times.size.half.asInt])
~itemsAfter.(rrand(~times[0], ~times.last).postln)
~times[17]



(

~itemsBefore = { |seconds|
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
			res = if(~times[halfIdx] <= seconds) {
				halfIdx
			} {
				halfIdx - 1
			}
		} {
			prevIdx = halfIdx;
			cnt = cnt+1;
		}
	}
	);

	postf("copyFrom: % (%)\n", res, ~times[res]);
	cnt
};

// ~times = Array.series(50000, 35, 3.5419);
~times = Array.series(1, 35, 3.5419);
)
~itemsBefore.(~times[~times.size.half.asInt])
~itemsBefore.(rrand(~times[0], ~times.last).postln)
~times[17]


// go to halfway
// is time over threshold?
// if yes, jump back half
// if no, jump forward half