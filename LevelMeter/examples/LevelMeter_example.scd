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
the .decimals_() method will do the ensure the proper rounding of values.

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

If you only want to show only the value or only the peak,
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

~numChans = 6;

(
~sig = { |updateFreq=12|
	var sig, trig, rms, peak;

	sig = PinkNoise.ar * SinOsc.kr(~numChans.collect{rrand(0.1, 2.5)}).range(0,1);

	trig = Impulse.kr(updateFreq);
	rms = RunningSum.rms(sig);
	peak = Peak.ar(sig, Delay1.kr(trig));

	SendReply.kr(trig,
		'/ampPkVals',
		rms++peak
	)
	Out.ar(2, sig);
}.play
)

(
var numMeters, metersArr, meterListener;

~numChans = 6;
metersArr = List();

meterListener = OSCdef(\meterRelay, { |msg|
	var ampPkVals;
	ampPkVals = msg[3..].clump(~numChans).lace(~numChans*2).clump(2);

	metersArr.do{ |meters|
		meters.do{ |meter, i|
			// set the amp value, meter converts to dB
			// with .ampdb variant setter
			meter.ampdbValuePeak_(*ampPkVals[i])
		}
	};
}, '/ampPkVals'
);



// basic meters
metersArr.add(
	Window().front.view.layout_(HLayout(
	*~numChans.collect({ |i|
		LevelMeter(label: nil)
		.spec_(ControlSpec(-100, 0))
		}).postln
	))
)

(
Window().front.view.layout_(HLayout(
	LevelMeter(label: nil, labelAlign:nil, rangeLabelAlign:nil, levelLabelAlign:nil)
))
)

(
w = Window().front;
l = 12.collect{|i| LevelMeter(label: i)};
l.do(_.spec_(ControlSpec(-100, 0)));
w.view.layout_(HLayout(*l));
)

(
w = Window().front;
l = 12.collect{|i| LevelMeter(label: i, rangeLabelAlign: nil)};
l.do(_.spec_(ControlSpec(-100, 0)));
w.view.layout_(HLayout(*l));
)

(
w = Window().front;
l = 12.collect{|i| LevelMeter(label: i, rangeLabelAlign: nil, labelAlign: \bottom)};
l.do(_.spec_(ControlSpec(-100, 0)));
w.view.layout_(HLayout(*l));
)
(
w = Window().front;
l = 12.collect{|i| LevelMeter(label: i, rangeLabelAlign: nil, levelLabelAlign: \bottomRight, labelAlign: \bottom)};
l.do(_.spec_(ControlSpec(-100, 0)));
w.view.layout_(HLayout(*l));
)

(
w = Window().front;
l = [
LevelMeter(label: "chan0", rangeLabelAlign: \left, levelLabelAlign: \top),
LevelMeter(label: "chan1", rangeLabelAlign: \right, levelLabelAlign: \top),
LevelMeter(label: "chan2", rangeLabelAlign: \left, levelLabelAlign: \bottom),
LevelMeter(label: "chan3", rangeLabelAlign: \right, levelLabelAlign: \bottom),
LevelMeter(label: "chan4", rangeLabelAlign: \left, levelLabelAlign: nil),
LevelMeter(label: "chan5", rangeLabelAlign: nil, levelLabelAlign: \top),
];
w.view.layout_(HLayout(*l))
)

(
w = Window().front;
l = [
LevelMeter(label: "chan0", rangeLabelAlign: \left, levelLabelAlign: \top),
LevelMeter(label: "chan1", rangeLabelAlign: \right, levelLabelAlign: \top),
];
w.view.layout_(HLayout(*l))
)

(
l.do(_.spec_(ControlSpec(-100, 0)));
t = Task({
	inf.do{

		l.do{|mtr|
			var val = rrand(-100, 0.0);
			mtr.valuePeak_(val, val*rrand(0.3, 0.6))};
		0.1.wait;
	}
},AppClock)
)
t.start
t.stop

l.do(_.addThreshold(-40, Color.yellow));
l.do(_.addThreshold(-20, Color.red));
l.do(_.setThreshold(0, -80, Color.blue));
l.do(_.setThreshold(1, -5, Color.red));
l.do(_.removeThreshold(0));

(
l.do{|mtr|
	var numsteps=25;
	var dbstep, colstep;
	dbstep = mtr.spec.range/numsteps;
	colstep= numsteps.reciprocal;
	numsteps.do{|i|
		mtr.addThreshold(
			mtr.spec.minval+(dbstep*(i)),
			// Color.hsv(1-(colstep*i/2),1,1)
			Color.hsv(0.5-(colstep*i/2),1,1)
		)
	}
}
)

// toggle stepped display
l.do(_.stepped_(false))
l.do(_.stepped_(true))

// clear all thresholds
l.do(_.do(_.clearThresholds))

l.do{|mtr| mtr.meterView.background_(Color.gray.alpha_(0.2))}

l.do(_.levelFontSize_(18))
l.do(_.rangeFontSize_(8))
l.do(_.minWidth_(10))
l.do(_.maxWidth_(25))
l.do(_.decimals_(0))
l.do(_.levelFont_(protoString: "-00"))
l.do(_.rangeFont_(protoString: "-00"))
l.do({|mtr| mtr.minWidth_(65)})
l.do({|mtr| mtr.minWidth_(85)})
l.do({|mtr| mtr.maxWidth_(15)})
w.view.layout.add(nil)


(
w = Window().front;
l = [
LevelMeter(label: "Car", rangeLabelAlign: \left, levelLabelAlign: \bottomRight, labelAlign: \topRight),
LevelMeter(label: "SSB", rangeLabelAlign: nil, levelLabelAlign: \bottomRight, labelAlign: \topRight).fixedWidth_(65),
];
w.view.layout_(HLayout(*l));
l[0].meterWidth_(65);
)

l = LevelMeter(label: "Car", rangeLabelAlign: \left, levelLabelAlign: \bottomRight, labelAlign: \topRight)
l.children

(
w = Window().front;
l = [
LevelMeter(label: "chan0", rangeLabelAlign: \left, levelLabelAlign: \topRight, labelAlign: \topRight),
LevelMeter(label: "chan1", rangeLabelAlign: \right, levelLabelAlign: \topLeft, labelAlign: \topLeft)
];
w.view.layout_(HLayout(*l.do(_.meterWidth_(40))));
)
