/*
LevelMeter example
*/




/*
LevelMeter behaves like a View (and is in fact a subclass of View).
So you can use it in layouts like any other view, which is the
recommended usage.
*/

/* --- Appearance ---

The meter is actually made up of a view with an internal layout that
allows various arrangements of it's core components:

- A label
- Level "labels"
- Range "labels"
- The level meter

Each label is a StaticText() and will grow the size of the whole view
unless it's constrained by the .fixedWidth_() method, which constrains the
topmost view. For this reason, each label has a
.xFont_() and .xFontSize_() method for controlling the size. Unless
the topmost view is constrained, the view will grow to accomodate each label
and meter.

If setting the font (not just the size) directly, you can also set
a "protoString" which is used to determine the space that is allocated
for the text.  For example, if you expect values between 0 and 100, you
might set the .labelFont_(protoString: "000.0"), which allocates enough
space for a 3-digit number with one decimal point resolution.  Relatedly,
the .decimals_() method will ensure the proper rounding of values.

Because the meter itself (a UserView) doesn't have an inherent size,
it tends to shrink if the labels (particularly range labels) shrink.
For this reason, there's a .meterWidth_() method to ensure a fixed
width of the meter. Remember that LevelMeter.fixedWidth_() will limit the
size of sub-views, like the meter and labels, if set.

When instantiating (and only when instantiating) the meter, you can
choose the arrangement of the various labels through the creation method arguments.
The meter (name) label and the levels label can be either
\bottomLeft, \bottom, \bottomRight, \topLeft, \top, or \topRight.
The range labels can be either \left or \right of the meter.
An argument value of nil will omit that meter.
This allows a large number of configurations.

If you want to show only the value or the peak,
set .showPeak_(bool) or .showValue_(bool).

Then just use the .value_ or .peak_ instead of .valuePeak_ setters
(or the ampdb variants) when setting the value.

*/

/* --- Behavior ---

You update the meter in the same way you update the LevelIndicater,
i.e. through the language, by updating it's value (and peak).

The meter uses a ControlSpec for displaying your value, and it is by
default 0>1, \linear.  Any value you set is mapped through the spec
and any threshold is defined in the spec units.
So if you're metering in dB, it makes sense for something like
.spec_(ControlSpec(-80, 0, \db))

~Colors~
Be default, the color of both the peak and value are .defaultColor, which
has a setter.

Otherwise, you can set "thresholds" beyond which the meter shows a different
color. For this there are the
.addThreshold(thresh, color), to add a threshold, and
.setThreshold(index, color), to modify a threshold, by its index
in the .thresholds list. The threshold value is in the units of the spec.

The thresholds can change the color of the entire value meter
or in steps that align with the thresholds, according to the
.stepped_(bool) method.

The peak color can be set independently with .peakColor_(). Setting this
to nil restores the default behavior.
*/


s.boot;


// --------------------------------------------
// Generic update method for Control Busses
// update with a routine and .getSynchronous
// Basic meter, no labels

(
~ctl !? {~ctl.free};
~ctl = CtkControl.lfo(LFSaw, 0.3).play;

w = Window().front;

~meter = LevelMeter(w, Rect(w.bounds.width/2-20,0,40, w.bounds.height))
.stepped_(true)
.spec_(ControlSpec(-1,1))
;
// add color thresholds
[-1, -0.5, 0, 0.5].do{|level, i|
	~meter.addThreshold(level, Color.black.alpha_(1-(i/4)))
};
)

( // task to poll bus and update meter
t !? {t.stop};
t = Task({
	inf.do{
		defer{~meter.value_(~ctl.getSynchronous)};
		12.reciprocal.wait;
	}
});
)

t.play;
// change ctl sig freq
~ctl.freq = 2

// cleanup
t.stop
~ctl.free;




// --------------------------------------------
// Meter style sampler: showcases some of the many
// label arrangement and display options
// Uses OSC update method for monitoring the peaks
// and rms of a signal (similar to Level Indicator)

// run this big block of code...

(
var numMeters, meterListener, meters_1;
var steppedMeters, pkMeters, colMeters;

~numChans = 6;

// sound source = 6 channels
~sig !? {~sig.free};
~sig = { |updateFreq=10, peakHoldDelay = 0|
	var sig, trig, rms, peak;

	sig = WhiteNoise.ar * LFTri.kr(~numChans.collect{|i|0.2+(i*0.2)}).range(0,1);
	trig = Impulse.kr(updateFreq);
	rms = MovingAverage.rms(sig, 0.05*SampleRate.ir, 0.05*SampleRate.ir);

	peak = Peak.ar(
		sig,
		Delay1.kr(
			(0 + Stepper.kr(trig, max: peakHoldDelay)) + trig
		)
	);

	SendReply.kr(trig,
		'/ampPkVals',
		rms++peak
	);
	Out.ar(
		s.options.numOutputBusChannels + s.options.numInputBusChannels,
		sig
	);
}.play;

// listener to get amp/peak vals and update meters
meterListener = OSCdef(\meterRelay, { |msg|
	var ampPkVals;
	ampPkVals = msg[3..].clump(~numChans).lace(~numChans*2).clump(2);

	~metersArr.do{ |meters|
		meters.do{ |meter, i|
			meter !? {
			// set the amp value, meter converts to dB
			// with .ampdb variant setter
			defer { meter.ampdbValuePeak_(*ampPkVals[i]) }
			}
		}
	};
}, '/ampPkVals'
);

// hold all these meters here
~metersArr = List();


// --- 1 --------------------------------------------
// basic meters

~metersArr.add(
	~numChans.collect({
		LevelMeter()
		.fixedHeight_(80)
	})++[nil] // adds nil to layout to compress left
);



// --- 2 --------------------------------------------
// basic meters - label bottom and level top

~metersArr.add(
	meters_1 =~numChans.collect({ |i|
		LevelMeter(label: i, labelAlign: \bottom, levelLabelAlign: \top)
	})
);

meters_1.do({|mtr|
	mtr !? {

		// setting the meter's background can help illustrate
		// it's range. Access the .meterView (UserView) directly
		mtr.meterView.background_(Color.gray.alpha_(0.2));

		// change the value text color by accessing the
		// valTxt (StaticText) directly
		mtr.valTxt.stringColor_(Color.blue.alpha_(0.4));
	}
});


// --- 3 --------------------------------------------
// label bottom, level/peak topRight, range left
// colorized (stepped) by threshold,

~metersArr.add(
	colMeters = ~numChans.collect({ |i|
		LevelMeter(label: i, labelAlign: \topLeft, rangeLabelAlign: \right, levelLabelAlign: \bottomLeft)
		.showPeak_(true)
		.levelFontSize_(10)
		.rangeFontSize_(8)
		.rangeFont_(protoString: "-00") // -90 is lowest value, so only need this much space
		.defaultColor_(Color.green.alpha_(0.5))
		.addThreshold(-26, Color.yellow.alpha_(0.5))
		.addThreshold(-10, Color.red.alpha_(0.5))
		.decimals_(3) // more resolution in the value display
		.stepped_(false) // no steps in threshold, whole meter follows value
	})
);

colMeters.do({|mtr|
	mtr !? {
		mtr.meterView.background_(Color.gray.alpha_(0.1));
	}
});



// --- 4 --------------------------------------------
// just one meter on left with range shows
// enough for the whole row

~metersArr.add(
	steppedMeters = (~numChans-1).collect({ |i|
		// meters without range
		LevelMeter(label: i+1, labelAlign: \bottom, rangeLabelAlign: nil, levelLabelAlign: \top)
	}).insert(0,
		// add the first meter with a range
		LevelMeter(label: 0, labelAlign: \bottom, rangeLabelAlign: \left, levelLabelAlign: \topRight)
	).do({|meter|
		meter
		.showPeak_(true)
		.levelFontSize_(10)
		.rangeFontSize_(12).rangeFont_(protoString: "-00") // -90 is lowest value, so only need this much space
		.levelFont_(protoString: "-00.0") // -90 is lowest value, so only need this much space
		.meterWidth_(20) // make sure all meters have the same width
		.decimals_(0) // round values to integers
		.spec_(ControlSpec(-90, 0)) // set the spec before setting the thresholds below
	})++[nil] // adds nil to layout to compress left
);

// more elaborate thresholding
// set 25 thresholds, each with an algorithmically generated color
steppedMeters.do{|mtr|
	var numsteps=25;
	var dbstep, colstep;
	mtr !? {
		dbstep = mtr.spec.range/numsteps;
		colstep= numsteps.reciprocal;
		numsteps.do{|i|
			mtr.addThreshold(
				mtr.spec.minval+(dbstep*(i)),
				Color.hsv(0.5-(colstep*i/2),1,1)
			)
		}
	}
};



// --- 5 --------------------------------------------
// "peak" only
// note if using only the peak, the setter method could
// just be .ampdbPeak_() in the responder above, setting
// value wouldn't really be necessary because it isn't
// displayed

pkMeters = ~numChans.collect({ |i|
	// meters without range
	LevelMeter(label: i, labelAlign: \bottom, rangeLabelAlign: nil, levelLabelAlign: \top)
	.showPeak_(true)
	.showValue_(false)
	.levelFontSize_(10)
	.rangeFontSize_(8).rangeFont_(protoString: "-00") // -90 is lowest value, so only need this much space
	.levelFont_(protoString: "-00.0") // -90.0 is lowest value, so only need this much space
	.meterWidth_(20) // make sure all meters have the same width
	.decimals_(1) // round values to integers
	.peakLineSize_(2) // make peak line a bit thicker
	.peakColor_(Color.black) // set peak color explicityly
})++[nil]; // adds nil to layout to compress left

pkMeters.do({ |mtr|
	mtr !? {mtr.meterView.background_(Color.gray.alpha_(0.2))}
});

~metersArr.add(pkMeters);

// set the spec for all meters
~metersArr.do(_.do({ |me|
	me !? {	me.spec_(ControlSpec(-90, 0)) }
})
);

w = Window(bounds: Rect(0,0,480,Window.screenBounds.height)).front.view.layout_(
	VLayout(
		*~metersArr.size.collect({ |i|
			HLayout(*~metersArr[i])
		})
	).spacing_(12)
)
)

// change the spec range of all the sliders
~metersArr.do(_.do({|mtr| mtr !? {mtr.spec_(ControlSpec(-60, 0))}}))

~sig.set(\updateFreq, 15)    // drive meter faster or slower

~sig.set(\peakHoldDelay, 13) // wait to update peak every 'n' rms updates
~sig.set(\peakHoldDelay, 1)  // only update peak every other rms update
~sig.set(\peakHoldDelay, 0)  // no update delay, update peak with every rms update

s.quit;
