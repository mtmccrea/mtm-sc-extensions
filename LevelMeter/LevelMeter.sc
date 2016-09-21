// TODO:
// add \centered mode
// add optional meter label
// levels on top or bottom
// range labels on left or right
// add method to create arbitrary number of color thresholds
// add: level and peak rects can be either separate colors
//      (depending on threshold), same color: follow peak,
//      same color: follow level
// Meter is multi-colored: level colors are represented in
//      tiers, not as a single block that changes color
// Reconsider how values are set externally - make better, more clear
//      use of Spec. Test case, you have a amp signal you want displayed
//      in dB. Set amp value, then internally use Spec with dB scaling to
//      set the meter level. Right now I'm sending in dB values directly
// Add an alpha layer to create a "trace" (with "clear" trace button)

LevelMeter : View {
	var orientation, rangeLabelAlign, levelLabelAlign;
	var <meterView, <masterLayout, valTxtView;
	var valTxt, pkTxt, minTxt, maxTxt;
	var >pkLineSize = 4;

	var label, labelTxt, labelFont, labelAlign;
	var protoRangeVal, rangeFont;
	var protoLevelVal, levelFont;

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
		thresholds = List();
		thresholdColors = List();
		thresholdsNorm = List();
		defaultColor = Color.green;

		label !? {
			labelTxt = StaticText().string_(label.asString).align_(\center).background_(Color.rand);
		};
		valTxt = StaticText().string_("0").background_(Color.rand);
		pkTxt = StaticText().string_("0").background_(Color.rand);
		minTxt = StaticText().string_("0").background_(Color.rand);
		maxTxt = StaticText().string_("1").background_(Color.rand);

		this.labelTxtFont_(Font.default);
		this.levelTxtFont_(Font.default, "-00.00");
		this.rangeTxtFont_(Font.default, "-000");

		meterView = UserView().minWidth_(5);

		this.makeMeterView;
		this.assebleElements;

		// masterLayout = VLayout(
		// 	[pkTxt.align_(\right), a: \topRight],
		// 	[valTxt.align_(\right), a: \bottomRight],
		// 	HLayout(
		// 		VLayout(
		// 			[maxTxt.align_(\right), a: \topRight],
		// 			nil,
		// 			[minTxt.align_(\right), a: \bottomRight]
		// 		),
		// 		meterView
		// 	)
		// ).margins_(0);

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
		// masterLayout.add(meterLayout, stretch: 1);

		levelLabelAlign !? {
			var align;
			align = switch (rangeLabelAlign,
				\left, {\right}, \right, {\left}, nil, {\center}
			);
			[pkTxt, valTxt].do{|txt,i|
				txt.align_(align);
				if (levelLabelAlign == \bottom) {
					masterLayout.add( txt, align: align)
				} {
					masterLayout.insert( txt, i, align: align)
				}
			};
		};

		labelTxt !? {
			switch (labelAlign,
				\bottom, {masterLayout.add(labelTxt, align: \center)},
				{masterLayout.insert(labelTxt, align: \center)} // default to top
			)
		};
	}

	// val is the normalized level
	lookUpColor { |val|
		var valIdx = thresholdsNorm.indexOfGreaterThan(val);
		^case
		// exceeds top thresh
		{valIdx.isNil} {thresholdColors.last}
		// below lowest thresh
		{valIdx == 0} {defaultColor}
		// value is between indeces
		{thresholdColors[valIdx-1]};
	}

	makeMeterView {
		meterView.drawFunc_({ |uv|
			var bnds;
			bnds = uv.bounds;

			if(thresholds.size > 0) {
				var valCol, pkCol;

				// one meter color
				// draw level
				Pen.fillColor_(this.lookUpColor(valueNorm));
				Pen.fillRect(
					Size(bnds.width, bnds.height*valueNorm).asRect.bottom_(bnds.height);
				);
				// draw peak
				Pen.fillColor_(this.lookUpColor(peakValueNorm));
				Pen.fillRect(
					Size(bnds.width, pkLineSize).asRect.top_(bnds.height*(1-peakValueNorm));
				);
			} {
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
		}).resize_(5);
	}

	value_ { |val, refresh=true|
		// set txt before mapping
		valTxt.string_(val.round(roundTo).asString);
		val = spec.unmap(val);
		valueNorm = val;
		refresh.if{this.refresh};
	}

	peakValue_ { |val, refresh=true|
		// set txt before mapping
		pkTxt.string_(val.round(roundTo).asString);
		val = spec.unmap(val);
		peakValueNorm = val;
		refresh.if{this.refresh};
	}

	valueAndPeak_ { |val, pkval|
		this.value_(val, false);
		this.peakValue_(pkval, false);
		this.refresh;
	}

	refresh {
		meterView.refresh;
	}

	decimals_{ |num|
		roundTo = if (num > 0) {
			("0."++"".padRight(num-1,"0")++"1").asFloat
		} {1}
	}

	spec_ { |controlSpec|
		spec = controlSpec;

		minTxt.string = controlSpec.minval.asString;
		maxTxt.string = controlSpec.maxval.asString;

		// update thresholds, NOTE: can't collect into a List!
		thresholds.do{|thresh, i|
			thresholdsNorm[i] = spec.unmap(thresh)
		};
	}

	rangeFontSize_ { |num|
		rangeFont.size_(num);
		this.rangeTxtFont_(rangeFont)
	}

	levelFontSize_ { |num|
		levelFont.size_(num);
		this.levelTxtFont_(levelFont)
	}

	labelTxtFont_ { |font|
		var txtSize;
		if (label.notNil and: font.notNil) {
			labelFont = font;
			txtSize = label.bounds(rangeFont).size;
			labelTxt.font_(labelFont).fixedSize_(txtSize);
		};
	}

	rangeTxtFont_ { |font, protoString|
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

	levelTxtFont_ { |font, protoString|
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

	setThresh { |index, thresh, color|
		if (index < thresholds.size) {
			this.removeThresh(index);
			this.addThresh(thresh, color);
		} {
			this.addThresh(thresh, color);
		}
	}

	addThresh { |thresh, color|
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

		postf("greaterIdx %, idx %\n", greaterIdx, idx);

		thresholds.insert(idx, thresh);
		thresholdColors.insert(idx, color ?? defaultColor);
		"pre: ".post;
		thresholdsNorm.postln;
		thresholdsNorm.insert(idx, spec.unmap(thresh));
		"ppost: ".post;
		thresholdsNorm.postln;

	}

	removeThresh { |index|
		[thresholds, thresholdsNorm, thresholdColors].do(_.removeAt(index));
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
l = [
	LevelMeter(label: 0, rangeLabelAlign: \left, levelLabelAlign: \top),
	LevelMeter(label: 1, rangeLabelAlign: \right, levelLabelAlign: \top),
	LevelMeter(label: 2, rangeLabelAlign: \left, levelLabelAlign: \bottom),
	LevelMeter(label: 3, rangeLabelAlign: \right, levelLabelAlign: \bottom),
	LevelMeter(label: 4, rangeLabelAlign: \left, levelLabelAlign: nil),
	LevelMeter(label: 5, rangeLabelAlign: nil, levelLabelAlign: \top),
];
w.view.layout_(HLayout(*l))
)

(
l.do(_.spec_(ControlSpec(-100, 0)));
r = fork ({
	inf.do{

		l.do{|mtr|
			var val = rrand(-100, 0.0);
			mtr.valueAndPeak_(val, val*rrand(0.3, 0.6))};
		0.3.wait;
	}
},AppClock)
)
r.stop

l.do(_.addThresh(-40, Color.yellow));
l.do(_.add|Thresh(-20, Color.red));
l.do(_.setThresh(0, -80, Color.yellow));
l.do(_.setThresh(1, -5, Color.red));
l.do(_.removeThresh(0));

l.do(_.levelFontSize_(8))
l.do(_.rangeFontSize_(8))
l.do(_.minWidth_(10))
l.do(_.decimals_(0))
l.do(_.levelTxtFont_(protoString: "-00"))
l.do(_.rangeTxtFont_(protoString: "-00"))
l.do({|mtr| mtr.minWidth_(65)})
l.do({|mtr| mtr.maxWidth_(45)})
w.view.layout.add(nil)

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
