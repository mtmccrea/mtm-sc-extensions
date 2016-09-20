// TODO: add \centered mode

LevelMeter : View {
	var orientation, showMaxMin, showValue;
	var <meterView, <masterLayout, valTxtView;
	var valTxt, pkTxt, minTxt, maxTxt;
	var >pkLineSize = 4;

	var protoRangeVal, rangeFont;
	var protoLevelVal, levelFont;

	var <value=1, <peakValue = 1;
	var <spec, roundTo=0.1;

	*new { |parent, bounds, orientation = \vert, showMaxMin=true, showValue=true|
		^super.new(parent, bounds).init(orientation, showMaxMin, showValue)
	}

	init { |i_orient, i_showMaxMin, i_showValue|
		var txtSize;
		orientation = i_orient;
		showMaxMin = i_showMaxMin;
		showValue = i_showValue;

		valTxt = StaticText().string_("0").background_(Color.rand);
		pkTxt = StaticText().string_("0").background_(Color.rand);
		minTxt = StaticText().string_("0").background_(Color.rand);
		maxTxt = StaticText().string_("1").background_(Color.rand);

		this.levelTxtFont_(Font.default, "-00.00");
		this.rangeTxtFont_(Font.default, "-000");

		meterView = UserView().minWidth_(5);

		this.makeMeterView;

		masterLayout = VLayout(
			[pkTxt.align_(\right), a: \topRight],
			[valTxt.align_(\right), a: \bottomRight],
			HLayout(
				VLayout(
					[maxTxt.align_(\right), a: \topRight],
					nil,
					[minTxt.align_(\right), a: \bottomRight]
				),
				meterView
			)
		).margins_(0);

		this.layout_(masterLayout);
	}

	makeMeterView {
		meterView.drawFunc_({ |uv|
			var bnds;
			bnds = uv.bounds;
			// draw level
			Pen.fillRect(
				Size(bnds.width, bnds.height*this.value).asRect.bottom_(bnds.height);
			);
			// draw peak
			Pen.fillRect(
				Size(bnds.width, pkLineSize).asRect.top_(bnds.height*(1-this.peakValue));
			);
		}).resize_(5);
	}

	value_ { |val, refresh=true|
		// set txt before mapping
		valTxt.string_(val.round(roundTo).asString);
		spec !? {val = spec.unmap(val)};
		value = val;
		refresh.if{this.refresh};
	}

	peakValue_ { |val, refresh=true|
		// set txt before mapping
		pkTxt.string_(val.round(roundTo).asString);
		spec !? {val = spec.unmap(val)};
		peakValue = val;
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

		controlSpec.isNil.if({
			minTxt.string = "0";
			maxTxt.string = "1";
		},{
			minTxt.string = controlSpec.minval.asString;
			maxTxt.string = controlSpec.maxval.asString;
		});
	}

	rangeFontSize_ { |num|
		rangeFont.size_(num);
		this.rangeTxtFont_(rangeFont)
	}

	levelFontSize_ { |num|
		levelFont.size_(num);
		this.levelTxtFont_(levelFont)
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
	}

	meterWidth_ { |width|
		meterView.fixedWidth_(width);
	}
}

/*
(
w = Window().front;
l = 12.collect{LevelMeter()};
l.do(_.spec_(ControlSpec(-100, 0)));
w.view.layout_(HLayout(*l));
)
(
r = fork ({
	inf.do{

		l.do{|mtr|
			var val = rrand(-100, 0.0);
			mtr.valueAndPeak_(val, val*rrand(0.3, 0.6))};
		0.1.wait;
	}
},AppClock)
)
r.stop

l.do(_.levelFontSize_(8))
l.do(_.rangeFontSize_(8))
l.do(_.minWidth_(10))
l.do({|mtr| mtr.minWidth_(65)})
w.view.layout.add(nil)

*/