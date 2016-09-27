// TODO:
// add \centered mode
// add: level and peak rects can be either separate colors
//      (depending on threshold), same color: follow peak,
//      same color: follow level
// Add an alpha layer to create a "trace" (with "clear" trace button)
// Make a separate class, LevelRangeMeter, for generating a view displaying
//      the meter range, aligned with there the meter view is in it's
//      corresponding LevelMeter
// Add optional clip hold indicator
// LED mode: with LED size specification
// Add help file with examples
// Add mode for peak or value only, both in meter and level label

LevelMeter : View {
	var orientation, rangeLabelAlign, levelLabelAlign;
	var <>stepped = true;
	var <meterView, <masterLayout, valTxtView;
	var valTxt, pkTxt, minTxt, maxTxt;
	var >pkLineSize = 4;

	var label, labelTxt, labelFont, labelAlign;
	var protoRangeVal, rangeFont;
	var protoLevelVal, levelFont;

	var labelView, levelView;

	var <>valueNorm=1, <>peakValueNorm = 1;
	var <spec, roundTo=0.1;
	var <thresholds, <thresholdsNorm, <thresholdColors;
	var <>defaultColor;

	*new {
		arg
		parent, bounds, orientation = \vert, label, labelAlign = \top,
		rangeLabelAlign = \left, levelLabelAlign = \top;

		^super.new(parent, bounds).init(orientation, label, labelAlign, rangeLabelAlign, levelLabelAlign)
	}

	init { |i_orient, i_label, i_labelAlign, i_rangeLabelAlign, i_levelLabelAlign|
		var txtSize;
		// "copyArgs"
		orientation = i_orient;
		label = i_label.asString;
		labelAlign = i_labelAlign;
		rangeLabelAlign = i_rangeLabelAlign;
		levelLabelAlign = i_levelLabelAlign;

		spec = ControlSpec();

		thresholds = List();		// color thresholds, specified in spec units
		thresholdColors = List();	// colors for meter (above) each threshold
		thresholdsNorm = List();	// color thresholds, normalized (unmapped from spec), used in drawing of meter
		defaultColor = Color.green; // color for below lowest threshold (or if no thresholds specified)

		label !? {
			labelTxt = StaticText().string_(label.asString).align_(\center);/*.background_(Color.rand);*/
		};
		valTxt = StaticText().string_("0");/*.background_(Color.rand);*/
		pkTxt = StaticText().string_("0");/*.background_(Color.rand);*/
		minTxt = StaticText().string_("0");/*.background_(Color.rand);*/
		maxTxt = StaticText().string_("1");/*.background_(Color.rand);*/

		this.labelFont_(Font.default);
		this.levelFont_(Font.default, "-00.00");
		this.rangeFont_(Font.default, "-000");

		this.makeMeterView;
		this.assebleElements;

		this.layout_(masterLayout);
	}

	assebleElements {
		var rangeTxtLayout, meterLayout;
		// if (orientation.asString[0].asSymbol == \v) {

		masterLayout = VLayout().margins_(0);

		meterLayout = HLayout(
			meterView
		).margins_(0).spacing_(2);

		rangeLabelAlign !? {
			switch(rangeLabelAlign,
				\left, {
					[maxTxt, minTxt].do(_.align_(\right));
					rangeTxtLayout = VLayout(
						[maxTxt, a: \topRight],
						nil,
						[minTxt, a: \bottomRight]
					);
					meterLayout.insert(rangeTxtLayout);
				},
				\right, {
					[maxTxt, minTxt].do(_.align_(\left));
					rangeTxtLayout = VLayout(
						[maxTxt, a: \topLeft],
						nil,
						[minTxt, a: \bottomLeft]
					);
					meterLayout.add(rangeTxtLayout);
				}
			);
		};

		masterLayout.add(meterLayout, stretch: 2);

		levelLabelAlign !? {
			var align, levelLayout;
			levelLayout = VLayout().margins_(0);
			levelView = View().layout_(levelLayout);

			align = switch (levelLabelAlign,
				\bottomLeft, {\left}, \bottom, {\center}, \bottomRight, {\right},
				\topLeft, {\left}, \top, {\center}, \topRight, {\right},
				{\center}
			);

			[pkTxt, valTxt].do{|txt| levelLayout.add(txt.align_(align), align: align)};

			switch (levelLabelAlign,
				\bottomLeft, {masterLayout.add(levelView, align: \left)},
				\bottom, {masterLayout.add(levelView, align: \center)},
				\bottomRight, {masterLayout.add(levelView, align: \right)},

				\topLeft, {masterLayout.insert(levelView, align: \left)},
				\top, {masterLayout.insert(levelView, align: \center)},
				\topRight, {masterLayout.insert(levelView, align: \right)}
			);
		};

		labelTxt !? {
			switch (labelAlign,
				\bottomLeft, {masterLayout.add(labelTxt, align: \left)},
				\bottom, {masterLayout.add(labelTxt, align: \center)},
				\bottomRight, {masterLayout.add(labelTxt, align: \right)},

				\topLeft, {masterLayout.insert(labelTxt, align: \left)},
				\top, {masterLayout.insert(labelTxt, align: \center)},
				\topRight, {masterLayout.insert(labelTxt, align: \right)}
			);
		};
	}

	getThreshAbove { |val|
		^thresholdsNorm.indexOfGreaterThan(val);
	}

	// val is the normalized level
	getColorByVal { |val|
		var idxAbove = this.getThreshAbove(val);
		^case
		// exceeds top thresh
		{idxAbove.isNil} {thresholdColors.last}
		// below lowest thresh
		{idxAbove == 0} {defaultColor}
		// value is between indeces
		{thresholdColors[idxAbove-1]};
	}

	// meter value is made of multiple rects:
	// each threshold crossed is it's assigned color
	drawSteppedMeter { |bnds|
		var idxAbove, threshIdx;
		idxAbove = this.getThreshAbove(valueNorm);

		// index of topmost crossed threhold
		threshIdx =
		case
		// exceeds top thresh
		{idxAbove.isNil} {thresholds.size-1}
		// below lowest thresh
		{idxAbove == 0} {-1}
		// value is between indeces
		{ idxAbove-1 };

		// draw level
		if (threshIdx>=0) {
			var pxThresholds, pxBtwnThreshs, protoRect;
			// TODO: move this vars out, update on resize and thresh setting changes
			pxThresholds = thresholdsNorm * bnds.height;
			pxBtwnThreshs = pxThresholds.differentiate.drop(1);

			protoRect = Size(bnds.width, bnds.height).asRect;

			// bottommost, default color
			Pen.fillColor_(defaultColor);
			Pen.fillRect(
				protoRect.height_(pxThresholds[0]).bottom_(bnds.height);
			);

			// up through thresh steps
			threshIdx.do{ |i|
				Pen.fillColor_(thresholdColors[i]);
				Pen.fillRect(
					protoRect.height_(pxBtwnThreshs[i]).bottom_(bnds.height - pxThresholds[i]);
				);
			};

			// topmost, inbetween threshold steps
			Pen.fillColor_(thresholdColors[threshIdx]);
			Pen.fillRect(
				protoRect.height_(
					(valueNorm - thresholdsNorm[threshIdx]) * bnds.height
				).bottom_(bnds.height - pxThresholds[threshIdx]);
			);

		} {
			Pen.fillColor_(defaultColor);
			Pen.fillRect(
				Size(bnds.width, bnds.height*valueNorm).asRect.bottom_(bnds.height);
			);
		}
	}

	// meter value is made of one rect color
	// corresponding to the uppermost threshold crossed
	drawSolidMeter { |bnds|
		// draw level
		Pen.fillColor_(this.getColorByVal(valueNorm));
		Pen.fillRect(
			Size(bnds.width, bnds.height*valueNorm).asRect.bottom_(bnds.height);
		);
	}

	makeMeterView {

		meterView = UserView()
		.resize_(5)
		.minWidth_(4)
		.drawFunc_({ |uv|
			var bnds;
			bnds = uv.bounds;
			if(thresholds.size > 0) {
				// draw level
				if (stepped) {
					this.drawSteppedMeter(bnds);
				} {
					this.drawSolidMeter(bnds);
				};

				// draw peak
				Pen.fillColor_(this.getColorByVal(peakValueNorm));
				Pen.fillRect(
					Size(bnds.width, pkLineSize).asRect.top_(bnds.height*(1-peakValueNorm));
				);
			} {
				// no thresholds specified, just draw default color
				Pen.fillColor_(defaultColor);
				// draw level
				Pen.fillRect(
					Size(bnds.width, bnds.height*valueNorm).asRect.bottom_(bnds.height);
				);
				// draw peak
				Pen.fillRect(
					Size(bnds.width, pkLineSize).asRect.top_(bnds.height*(1-peakValueNorm));
				);
			}
		});
	}


	value_ { |val, refresh=true|
		// set txt before mapping
		valTxt.string_(val.round(roundTo).asString);
		val = spec.unmap(val);
		valueNorm = val;
		refresh.if{this.refresh};
	}

	peak_ { |val, refresh=true|
		// set txt before mapping
		pkTxt.string_(val.round(roundTo).asString);
		val = spec.unmap(val);
		peakValueNorm = val;
		refresh.if{this.refresh};
	}

	valuePeak_ { |val, pkval, refresh=true|
		this.value_(val, false);
		this.peak_(pkval, false);
		refresh.if{this.refresh};
	}

	// conveniencemethods for the above three,
	// specifying input as amp, but meter is in dB
	ampdbValue_ { |amp, refresh=true|
		this.value_(amp.ampdb, refresh)
	}
	ampdbPeak_ { |amp, refresh=true|
		this.peak_(amp.ampdb, refresh)
	}
	ampdbValuePeak_ { |amp, pkamp, refresh=true|
		this.valuePeak_(amp.ampdb, pkamp.ampdb, refresh)
	}

	// refresh the userView meter
	refresh {
		meterView.refresh;
	}

	decimals_{ |num|
		roundTo = if (num > 0)
		{("0."++"".padRight(num-1,"0")++"1").asFloat}
		{1}
	}

	spec_ { |controlSpec|
		var min, max, minString, maxString;
		spec = controlSpec;

		min = controlSpec.minval;
		max = controlSpec.maxval;
		minString = min.asString;
		maxString = max.asString;

		minTxt.string = minString;
		maxTxt.string = maxString;

		// update thresholds, NOTE: can't collect into a List!
		thresholds.do{|thresh, i|
			thresholdsNorm[i] = spec.unmap(thresh)
		};
		// update the size of the range text views
		rangeLabelAlign !? {
			var strSizes;
			strSizes = [minString.size, maxString.size];
			this.rangeFont_(
				protoString: [minString, maxString].at(strSizes.indexOf(strSizes.maxItem)));
		};
	}

	rangeFontSize_ { |num|
		rangeFont.size_(num);
		this.rangeFont_(rangeFont)
	}

	levelFontSize_ { |num|
		levelFont.size_(num);
		this.levelFont_(levelFont)
	}

	labelFont_ { |font|
		var txtSize;
		if (label.notNil and: font.notNil) {
			labelFont = font;
			txtSize = label.bounds(rangeFont).size;
			labelTxt.font_(labelFont).fixedSize_(txtSize);
		};
	}

	rangeFont_ { |font, protoString|
		var txtSize;
		protoString !? {protoRangeVal = protoString};
		font !? {
			rangeFont = font;
			[minTxt, maxTxt].do(_.font_(font));
		};

		txtSize = protoRangeVal.bounds(rangeFont).size;
		[minTxt, maxTxt].do(_.fixedSize_(txtSize));
		// [minTxt, maxTxt].do(_.maxSize_(txtSize));
	}

	levelFont_ { |font, protoString|
		var txtSize;
		protoString !? {protoLevelVal = protoString};
		font !? {
			levelFont = font;
			[valTxt, pkTxt].do(_.font_(font));
		};

		txtSize = protoLevelVal.bounds(levelFont).size;
		[valTxt, pkTxt].do(_.fixedSize_(txtSize));
		// [valTxt, pkTxt].do(_.maxSize_(txtSize));
	}

	meterWidth_ { |width|
		meterView.fixedWidth_(width);
	}

	setThreshold { |index, thresh, color|
		if (index < thresholds.size) {
			this.removeThresh(index);
			this.addThresh(thresh, color);
		} {
			this.addThresh(thresh, color);
		}
	}

	addThreshold { |thresh, color|
		var greaterIdx, idx;
		thresholds.includes(thresh).if{
			format(
				"Threshold % already set. Use setThresh to update the color, or add a unique threshold.\n",
				thresh
			).warn
			^this // break
		};

		greaterIdx = thresholds.indexOfGreaterThan(thresh);

		idx = case
		// exceeds top thresh
		{greaterIdx.isNil} {thresholds.size}
		{greaterIdx};

		thresholds.insert(idx, thresh);
		thresholdColors.insert(idx, color ?? defaultColor);
		thresholdsNorm.insert(idx, spec.unmap(thresh));
	}

	removeThreshold { |index|
		[thresholds, thresholdsNorm, thresholdColors].do(_.removeAt(index));
	}

	clearThresholds {
		[thresholds, thresholdsNorm, thresholdColors].do(_.clear);
	}
}

/*
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

*/
//
// f = {|arg1 = 12, arg2 = 34| [arg1, arg2].postln }
// f.()        // -> [ 12, 34 ]
// f.(56, 78)  // -> [ 56, 78 ]  // expected
// f.(56, nil) // -> [ 56, nil ] // expected
//
// // different arg default specification
// // (useful for negative values)
// f = {|arg1(12), arg2(34)| [arg1, arg2].postln }
// f.()        // -> [ 12, 34 ]
// f.(56, 78)  // -> [ 56, 78 ]  // expected
// f.(56, nil) // -> [ 56, 34 ]  // arg2 isn't nil?
