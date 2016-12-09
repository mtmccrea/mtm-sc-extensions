// strokeColor and fillColor variables: nil disables stroke/fill

// TODO
// user defines startAngle/End/Sweep as radian or deg?
// fix continuous circular motion
// make this function like a real view
// consider ticks with annularWedge instead of lines
// consolidate variables and method: value vs. level controls, a higher level property mechanism?
// ctl to un/link level from handle: handle is value, level is set independently (still share the same spec?)
// --> handle could be "peak" so functions like a peak/value meter
// height/width adjusts with min(view.height, view.width)
// fully build out and test tick position specification methods
// consider giving level a different spec when levelFollowsValue.not
// add snapToClick flag: value jumps to where the mouse clicks (\circular mode only?)
Rotary : View {

	var <spec;
	var <action;
	var <levelFollowsValue;

	// dimensions
	var innerRadiusRatio; // radius,

	// movement
	var <direction, <startAngle, <sweepLength, <orientation;
	var valuePerPixel, valuePerRadian;
	var >wrap;
	var clickMode;

	//range
	var fillRange, strokeRange, rangeFillColor, rangeStrokeColor, rangeStroke, rangeStrokeWidth;
	// level
	var showLevel, levelFillColor, levelStrokeColor, strokeLevel, levelStroke, fillLevel, levelStrokeWidth;

	// handle
	var showHandle, handleColor, handleWidth;
	// value txt
	var showValue, valuePosition, valueFontSize, valueFont, valueFontColor, round;

	// ticks
	var showTicks, tickAlign, majorTickRatio, minorTickRatio,
	<majTicks, <minTicks, majTickVals, minTickVals,
	tickColor, majorTickWidth, minorTickWidth;

	var <rotaryView; // the rotary view
	var <value, <input;
	var <levelValue, <levelInput;
	var dirFlag; // changes with direction: cw=1, ccw=-1
	var prStartAngle; // start angle used internally, reference 0 to the RIGHT, as used in addAnnularWedge
	var prSweepLength; // sweep length used internally, = sweepLength * dirFlag
	var moveRelative = true;  // false means value jumps to click, TODO: disabled for infinite movement
	// var <view; // master view:
	// private
	var cen;
	var stValue, stInput;
	var mDownPnt;

	*new { arg parent, bounds, spec, innerRadiusRatio, startAngle=0, sweepLength=2pi;
		^super.new(parent, bounds).init(spec, innerRadiusRatio, startAngle, sweepLength)
	}

	init {|argSpec, argInnerRadiusRatio, argStartAngle, argSweepLength|

		spec = argSpec ?? \unipolar.asSpec;
		value = levelValue = spec.default;
		input = levelInput = spec.unmap(value);
		levelFollowsValue = true;
		action = {};

		// radius = argRadius ?? {this.bounds.width*0.5};
		innerRadiusRatio = argInnerRadiusRatio ?? {0};

		startAngle = argStartAngle; // reference 0 is UP
		sweepLength = argSweepLength;
		direction = \cw;
		dirFlag = 1;
		orientation = \vertical;
		wrap = false;
		clickMode = \relative; // or \absolute, in which case value snaps to where mouse clicks

		valuePerPixel = spec.range / 200; // for interaction: movement range in pixels to cover full spec range
		valuePerRadian = spec.range / sweepLength;

		// range
		fillRange = true;
		strokeRange = true;
		rangeFillColor = Color.gray;
		rangeStroke = \around;		// \inside, \outside, \around
		rangeStrokeColor = Color.black;
		rangeStrokeWidth = 1;

		// level
		showLevel = true;
		strokeLevel = true;
		levelStroke = \around;
		levelStrokeColor = Color.green;
		levelStrokeWidth = 2;
		fillLevel = true;
		levelFillColor = Color.white;

		// value
		showValue = true;
		valuePosition = \center; // \top, \bottom, \center, \left, \right, or Point()
		valueFontSize = 12;
		valueFont = Font("Helvetica", valueFontSize);
		valueFontColor = Color.black;
		round = 0.1;

		// handle
		showHandle = true;
		handleColor = Color.red;
		handleWidth = 2;

		// ticks
		showTicks = false;
		majTicks = [];
		minTicks = [];
		majTickVals = [];
		minTickVals = [];
		majorTickRatio = 0.25;
		minorTickRatio = 0.15;
		tickAlign = \outside;
		majorTickWidth = 1;
		minorTickWidth = 0.5;
		tickColor = nil; // default to rangeStrokeColor

		//view = View(this, this.bounds.extent.asRect).resize_(5);

		rotaryView = UserView(this, this.bounds.origin_(0@0)).resize_(5);

		this.onResize_({rotaryView.bounds_(this.bounds.origin_(0@0))});

		this.direction = direction; // this initializes prStarAngle and prSweepLength

		this.defineDrawFunc;
		this.defineInteraction;

	}

	// DRAW
	defineDrawFunc {
		var bnds, radius;
		var wedgeWidth, innerRad;
		var drRange, drLevel, drTicks, drRangeStroke, drHandle, drValueTxt, drLocalTicks, drWedgeStroke;

		rotaryView.drawFunc_({|v|
			bnds = v.bounds;
			bnds.postln;
			cen  = bnds.center;
			postf("cen: %\n\n", cen);
			radius = min(cen.x, cen.y);

			innerRad = radius*innerRadiusRatio;
			wedgeWidth = radius - innerRad;

			// order of drawing here is important for proper layering
			if (fillRange) {drRange.()};
			if (showLevel and: fillLevel) {drLevel.(\fill)};
			// if (showLevel and: strokeLevel) {drLevel.(\stroke)};
			if (showTicks) {drTicks.()};
			if (strokeRange) {drRangeStroke.()};
			if (showLevel and: strokeLevel) {drLevel.(\stroke)};
			if (showHandle) {drHandle.()};
			if (showValue) {drValueTxt.()};

		});

		drRange = {
			Pen.fillColor_(rangeFillColor);
			Pen.addAnnularWedge(cen, innerRad, radius, prStartAngle, prSweepLength);
			Pen.fill;
		};

		drLevel = { |fillOrStroke|
			var swLen; //, inset;
			swLen = prSweepLength * levelFollowsValue.if({input},{levelInput});
			Pen.push;
			switch (fillOrStroke,
				\fill, {
					Pen.fillColor_(levelFillColor);
					Pen.addAnnularWedge(cen, innerRad, radius, prStartAngle, swLen);
					Pen.fill;
				},
				\stroke, {
					Pen.strokeColor_(levelStrokeColor);
					Pen.width_(levelStrokeWidth);
					drWedgeStroke.(levelStroke, levelStrokeWidth, swLen);
					Pen.stroke;
				}
			);
			Pen.pop;
		};

		drTicks = {
			Pen.push;
			Pen.translate(cen.x, cen.y);
			Pen.rotate(prStartAngle);
			drLocalTicks.(majTicks, majorTickRatio, majorTickWidth);
			drLocalTicks.(minTicks, minorTickRatio, minorTickWidth);
			Pen.pop;
		};

		drHandle = {
			Pen.push;
			Pen.translate(cen.x, cen.y);
			Pen.width_(handleWidth);
			Pen.strokeColor_(handleColor);
			Pen.moveTo(innerRad@0);
			Pen.lineTo(radius@0);
			Pen.rotate(prStartAngle+(prSweepLength*input));
			Pen.stroke;
			Pen.pop;
		};

		drRangeStroke = {
			Pen.push;
			Pen.strokeColor_(rangeStrokeColor);
			Pen.width_(rangeStrokeWidth);
			drWedgeStroke.(rangeStroke, rangeStrokeWidth, prSweepLength);
			Pen.stroke;
			Pen.pop;
		};

		drValueTxt = {
			var v, r, half;
			v = value.round(round).asString;
			Pen.push;
			Pen.fillColor_(valueFontColor);
			if (valuePosition.isKindOf(Point)) {
				r = bnds.center_(bnds.extent*valuePosition);
				Pen.stringCenteredIn(v, r, valueFont, valueFontColor);
			} {
				r = switch (valuePosition,
					\center, {bnds},
					\left, {bnds.width_(bnds.width*0.5)},
					\right, {
						half = bnds.width*0.5;
						bnds.width_(half).left_(half);
					},
					\top, {bnds.height_(bnds.height*0.5)},
					\bottom, {
						half = bnds.height*0.5;
						bnds.height_(half).top_(half)
					},
				);
				Pen.stringCenteredIn(v, r, valueFont, valueFontColor)
			};
			Pen.fill;
			Pen.pop;
		};

		// helper
		drLocalTicks = {|ticks, tickRatio, strokeWidth|
			var penSt, penEnd;
			penSt = switch (tickAlign,
				\inside, {innerRad},
				\outside, {radius},
				\center, {innerRad + (wedgeWidth - (wedgeWidth * tickRatio) * 0.5)},
				{radius} // default to outside
			);

			penEnd = if (tickAlign == \outside) {
				penSt - (wedgeWidth * tickRatio)
			} { // \inside or \center
				penSt + (wedgeWidth * tickRatio)
			};

			Pen.push;
			Pen.strokeColor_(tickColor ?? rangeStrokeColor);
			ticks.do{|tickRad|
				Pen.width_(strokeWidth);
				Pen.moveTo(penSt@0);
				Pen.push;
				Pen.lineTo(penEnd@0);
				Pen.rotate(tickRad * dirFlag);
				Pen.stroke;
				Pen.pop;
			};
			Pen.pop;
		};

		// helper
		drWedgeStroke = {|borderType, strokeWidth, swLength|
			var inset;
			inset = strokeWidth*0.5;
			switch (borderType,
				\around, {
					Pen.addAnnularWedge(cen, innerRad, radius-inset, prStartAngle, swLength);
				},
				\inside, {
					Pen.addArc(cen, innerRad+inset, prStartAngle, swLength);
				},
				\outside, {
					Pen.addArc(cen, radius-inset, prStartAngle, swLength);
				},
				\insideOutside, {
					Pen.addArc(cen, innerRad+inset, prStartAngle, swLength);
					Pen.addArc(cen, radius-inset, prStartAngle, swLength);
				},
			);
		};

	}

	// INTERACT
	defineInteraction {
		var respondToLinearMove, respondToCircularMove;
		rotaryView.mouseDownAction_({|v, x, y|
			mDownPnt = x@y; // set for moveAction
			stValue = value;
			stInput = input;
		});

		rotaryView.mouseMoveAction_({|v, x, y|
			switch (orientation,
				\vertical, {respondToLinearMove.(mDownPnt.y-y)},
				\horizontal, {respondToLinearMove.(x-mDownPnt.x)},
				\circular, {respondToCircularMove.(x@y)}
			);
		});

		respondToLinearMove = {|dPx|
			if (dPx != 0) {this.valueAction_(stValue + (dPx * valuePerPixel))};
			this.refresh;
		};

		// radial change, relative to center
		respondToCircularMove = {|mMovePnt|
			var stPos, endPos, stRad, endRad, dRad;

			stPos = (mDownPnt - cen);
			stRad = atan2(stPos.y,stPos.x);
			// postf("downAngle: %\n", stRad);

			endPos = (mMovePnt - cen);
			endRad = atan2(endPos.y, endPos.x);
			// postf("moveAngle: %\n", endRad);

			// dRad = endRad - stRad * dirFlag ;

			// stRad.isNegative.if({
			// 	dRad = endRad.abs.neg - stRad * dirFlag ;
			// 	},{
			// 		dRad = endRad.abs - stRad * dirFlag ;
			// });

			dRad = (endRad - stRad).fold(0, pi) * dirFlag * (endRad - stRad).sign;
			// postf("move % degrees\n", dRad.raddeg);

			if (dRad !=0) {
				this.input_(stInput + (dRad/sweepLength)); // causes refresh
				this.doAction;
				// this.valueAction_(stValue + (dRad * valuePerRadian)); // causes refresh
			};

			// allow continuous updating of relative start point
			mDownPnt = mMovePnt;
			stValue = value;
			stInput = input;
		};
	}

	refresh {
		rotaryView.refresh;
	}

	value_ {|val|
		value = if (wrap) {
			val.wrap(spec.minval, spec.maxval);
		} {
			spec.constrain(val);
		};
		input = spec.unmap(value);

		if (levelFollowsValue) {
			levelValue = value;
			levelInput = input;
		};

		// notify dependants
		this.changed(\value, value);
		this.changed(\input, input);
		this.refresh;
	}

	// set the value by normalized value 0>1
	input_ {|normValue|
		input = normValue.clip(0,1);
		value = spec.map(input);
		// notify dependants
		this.changed(\value, value);
		this.changed(\input, input);
		this.refresh;
	}

	action_ { |actionFunc|
		action = actionFunc;
	}

	valueAction_ {|val|
		this.value_(val);
		this.doAction;
	}

	doAction {action.(value)}

	spec_ {|controlSpec|
		var rangeInPx;
		rangeInPx = spec.range/valuePerPixel; // get old pixels per range
		spec = controlSpec;
		this.rangeInPixels_(rangeInPx); // restore mouse scaling so it feels the same
		this.value_(value); // updates input
		// TODO: update ticks
	}

	// seaparate level from handle
	// could be used as or similar to rms/peak meter
	levelFollowsValue_ {|bool, resetLevel=true|
		levelFollowsValue = bool;
		if (bool) {this.levelValue_(value)};
		if (bool.not and: resetLevel) {this.levelValue_(spec.default)};
	}

	levelValue_ { |val|
		if (levelFollowsValue.not) {
			levelValue = if (wrap) {
				val.wrap(spec.minval, spec.maxval);
			} {
				spec.constrain(val);
			};
			levelInput = spec.unmap(levelValue);
		};
		this.refresh;
	}

	// set the value by normalized value 0>1
	levelInput_ {|normValue|
		if (levelFollowsValue.not) {
			levelInput = normValue.clip(0,1);
			levelValue = spec.unmap(levelInput);
		};
		this.refresh;
	}

	/* Orientation and Movement */

	direction_ {|dir=\cw|
		direction = dir;
		dirFlag = switch (direction, \cw, {1}, \ccw, {-1});
		this.startAngle_(startAngle);
		this.sweepLength_(sweepLength); // updates prSweepLength
		this.refresh;
	}

	startAngle_ {|radians=0|
		startAngle = radians;
		prStartAngle = -0.5pi + startAngle; //*dirFlag); // start angle always relative to 0 is up, cw
		this.ticksAtValues_(majTickVals, minTickVals); // refresh the list of maj/minTicks positions
		// this.refresh;
	}

	sweepLength_ {|radians=2pi|
		sweepLength = radians;
		prSweepLength = sweepLength * dirFlag;
		valuePerRadian = spec.range / sweepLength;
		this.ticksAtValues_(majTickVals, minTickVals); // refresh the list of maj/minTicks positions
		// this.refresh;
	}

	orientation_ {|vertHorizOrCirc = \vertical|
		orientation = vertHorizOrCirc;
	}

	valuePerPixel_ { |val|
		valuePerPixel = val
	}

	rangeInPixels_ { |px|
		valuePerPixel = spec.range/px;
	}

	innerRadiusRatio_ {|ratio|
		innerRadiusRatio = ratio;
		this.refresh
	}

	/* Ticks */

	showTicks_ {|bool|
		showTicks = bool;
		this.refresh;
	}

	majorTickRatio_ {|ratio = 0.25|
		majorTickRatio = ratio;
		this.refresh;
	}

	minorTickRatio_ {|ratio = 0.15|
		minorTickRatio = ratio;
		this.refresh;
	}

	// \inside, \outside, \center
	tickAlign_ {|insideOutSideCenter|
		case
		{
			(insideOutSideCenter == \inside) or:
			(insideOutSideCenter == \outside) or:
			(insideOutSideCenter == \center)
		} {
			tickAlign = insideOutSideCenter;
			this.refresh;
		}
		{ "Rotary:tickAlign_ : Invalid align argument. Must be 'inside', 'outside' or 'center'".warn }
	}

	// arrays of radian positions, reference from startAngle
	ticksAt_ {|majorRadPositions, minorRadPositions|
		majTicks = majorRadPositions;
		minTicks = minorRadPositions;
		majTickVals = spec.map(majTicks / sweepLength);
		minTickVals = spec.map(minTicks / sweepLength);
		showTicks = true;
		this.refresh;
	}

	// ticks at values unmapped by spec
	ticksAtValues_ {|majorVals, minorVals|
		majTicks = spec.unmap(majorVals)*sweepLength;
		minTicks = spec.unmap(minorVals)*sweepLength;
		majTickVals = majorVals;
		minTickVals = minorVals;
		showTicks = true;
		this.refresh;
	}

	// ticks values by value hop, unmapped by spec
	ticksEveryVal_ {|valueHop, majorEvery=2|
		var num, ticks, numMaj, majList, minList;
		num = (spec.range / valueHop).floor.asInt;
		ticks = num.collect{|i| spec.unmap(i * valueHop) * sweepLength};
		numMaj = num/majorEvery;
		majList = List(numMaj);
		minList = List(num-numMaj);
		ticks.do{|val, i| if ((i%majorEvery) == 0) {majList.add(val)} {minList.add(val)} };
		this.ticksAt_(majList, minList);
		this.refresh;
	}


	ticksEvery_ {|radienHop, majorEvery=2|
		this.refresh;
	}

	// evenly distribute ticks
	numTicks_ {|num, majorEvery=2, endTick=true|
		var hop, ticks, numMaj, majList, minList;
		hop = if (endTick) {sweepLength / (num-1)} {sweepLength / num};
		// drawNum = if (sweepLength==2pi) {num-1} {num}; // don't draw overlaying ticks in the case of full circle
		ticks = num.collect{|i| i * hop};
		numMaj = num/majorEvery;
		majList = List(numMaj);
		minList = List(num-numMaj);
		ticks.do{|val, i| if ((i%majorEvery) == 0) {majList.add(val)} {minList.add(val)} };
		this.ticksAt_(majList, minList);
	}

	tickColor_ {|color|
		tickColor = color;
		this.refresh;
	}

	/* Level */

	showLevel_ {|bool|
		showLevel = bool;
		this.refresh;
	}

	fillLevel_ {|bool|
		fillLevel = bool;
		this.refresh;
	}

	strokeLevel_ {|bool|
		strokeLevel = bool;
		this.refresh;
	}

	levelFillColor_ {|color|
		levelFillColor = color;
		this.refresh;
	}

	// \inside, \outside, \around
	levelStroke_ {|insideOutsideAround|
		case
		{
			(insideOutsideAround == \inside) or:
			(insideOutsideAround == \outside) or:
			(insideOutsideAround == \insideOutside) or:
			(insideOutsideAround == \around)
		} {
			levelStroke = insideOutsideAround;
			this.refresh;
		}
		{ "Rotary:levelStroke_ : Invalid align argument. Must be 'inside', 'outside', 'insideOutside' or 'around'".warn }
	}

	levelStrokeColor_ {|color|
		levelStrokeColor = color;
		this.refresh;
	}

	levelStrokeWidth_ {|px|
		levelStrokeWidth = px;
		this.refresh;
	}

	/* Range */

	fillRange_ {|bool|
		fillRange = bool;
		this.refresh;
	}
	strokeRange_ {|bool|
		strokeRange = bool;
		this.refresh;
	}
	rangeFillColor_ {|color|
		rangeFillColor = color;
		this.refresh;
	}

	// \inside, \outside, \around
	rangeStroke_ {|insideOutsideAround|
				case
		{
			(insideOutsideAround == \inside) or:
			(insideOutsideAround == \outside) or:
			(insideOutsideAround == \insideOutside) or:
			(insideOutsideAround == \around)
		} {
			rangeStroke = insideOutsideAround;
			this.refresh;
		}
		{ "Rotary:rangeStroke_ : Invalid align argument. Must be 'inside', 'outside', 'insideOutside' or 'around'".warn }
	}

	rangeStrokeColor_ {|color|
		rangeStrokeColor = color;
		this.refresh;
	}

	rangeStrokeWidth_ {|px|
		rangeStrokeWidth = px;
		this.refresh;
	}

	/* Handle */

	showHandle_ {|bool|
		showHandle = bool;
		this.refresh;
	}

	handleColor_ {|color|
		handleColor = color;
		this.refresh;
	}

	handleWidth_ {|px|
		handleWidth = px;
		this.refresh;
	}

	/* Value Text */

	showValue_ {|bool|
		showValue = bool;
		this.refresh;
	}

	// \top, \bottom, \center, \left, \right
	// or Point() normalized to Point(w,h) = Point(1,1)
	valuePosition_ {|position=\center|
		valuePosition = position;
		this.refresh;
	}

	valueFontSize_ {|size|
		valueFontSize = size;
		valueFont.hasPointSize.if({
			valueFont.pointSize_(valueFontSize);
		},{
			valueFont.pixelSize_(valueFontSize);
		});
		this.refresh;
	}

	valueFont_ {|stringOrFont|
		if (stringOrFont.isKindOf(Font)) {
			valueFont = stringOrFont;
		} { // string
			valueFont = Font(stringOrFont, valueFontSize);
		};
		this.refresh;
	}

	valueFontColor_ {|color|
		valueFontColor = color;
		this.refresh;
	}

	round_ {|float = 0.1|
		round = float;
		this.refresh;
	}

}

/*
r = Rotary(bounds: Size(300, 300).asRect, innerRadiusRatio: 0.1).front;
r.spec = [10, 100, \lin].asSpec;
r.startAngle_(-0.75pi);
r.sweepLength = 1.5pi;
r.rangeFillColor = Color.new(0.9,0.9,0.9);
r.strokeRange = false;
r.showHandle_(true).handleColor_(Color.green);
r.levelFillColor = Color.green.alpha_(0.2);
r.strokeLevel = true;
r.levelStroke = \outside;
r.levelStrokeWidth = 3;
r.valueFontSize = 36;
r.valuePosition = \right;
r.showTicks = true;
r.numTicks = 5;
r.tickColor = Color.gray;
r.action = {|val| val.postln};


r.tickAlign = \center
r.tickAlign = \outside
r.tickAlign = \inside
r.majorTickRatio = 0.2
r.strokeLevel =false
r.fillLevel =true
r.strokeRange =true
r.showTicks=false
r.strokeRange = false
r.showHandle = false


// continuous/wrapping knob
r = Rotary(bounds: Size(300, 300).asRect, innerRadiusRatio: 0.4).front;
r.spec = [0, 360, \lin].asSpec;
r.startAngle_(0.0pi);
r.sweepLength = 2pi;
r.rangeFillColor = Color.new(0.9,0.9,0.9);
r.strokeRange = false;
r.showHandle_(true).handleColor_(Color.green);
r.fillLevel = false;
r.strokeLevel = false;
r.valueFontSize = 36;
r.valuePosition = \center;
r.showTicks = true;
r.numTicks_(12, 3, endTick: false);
// r.numTicks_(9, 2);
r.tickColor = Color.gray;
r.tickAlign = \outside;
r.majorTickRatio = 1;
r.action = {|val| val.postln};
r.direction = \cw;
r.wrap = true;
// r.wrap = false;
r.round = 1;

// peak/rms meter

(

r = Rotary(bounds: Size(300, 300).asRect, spec: [-120, 0, \db, 0, -6].asSpec, innerRadiusRatio: 0.4).front;
// r.spec = ControlSpec(-120, 0, \db, default:-6);
r.startAngle = -0.7pi;
r.sweepLength = 1.4pi;
r.rangeFillColor = Color.new(0.9,0.9,0.9);
r.orientation = \circular;
r.strokeRange = false;
r.showHandle_(true).handleColor_(Color.black);
r.fillLevel = true;
r.strokeLevel = false;
r.valueFontSize = 36;
r.valuePosition = \bottom;
r.showTicks = false;
// r.numTicks_(12, 3, endTick: true);
r.tickColor = Color.gray;
r.tickAlign = \center;
// r.majorTickRatio = 0.4;
r.action = {|val| val.postln; ~sig.set(\amp, val.dbamp)};
r.round = 0.1;
r.levelFollowsValue = false;
r.showTicks = true;
r.ticksAtValues_([0, -6, -12, -24], [-3, -9, -16]);

// sound source = 6 channels
~sig !? {~sig.free};
~sig = { |updateFreq=15, amp=0.5|
	var sig, trig, rms, peak, outbus;

	sig = WhiteNoise.ar(amp);

	trig = Impulse.kr(updateFreq);
	rms = RunningSum.rms(sig, 0.05*SampleRate.ir);
	peak = Peak.ar(sig, Delay1.kr(trig));

	SendReply.kr(trig,
		'/ampPkVals',
		[rms, peak]
	);
	// outbus = s.options.numInputBusChannels + s.options.numOutputBusChannels;
	// Out.ar(outbus, sig);
}.play;

// listener to get amp/peak vals and update meters

~meterListener = OSCdef(\meterRelay, { |msg|
	var ampPkVals;
	ampPkVals = msg[3..];
// ampPkVals.postln;
	defer {r.levelValue = ampPkVals[0].ampdb}
}, '/ampPkVals'
);


)



/* in a layout */
(
var setupRot;
setupRot = {|col|
	var r;
	r= Rotary(spec: [0,45].asSpec, innerRadiusRatio: 0.1);
	r.spec = [10, 100, \lin].asSpec;
	r.startAngle_(0.75pi).sweepLength_(1.5pi);
	r.rangeFillColor = Color.new(0.9,0.9,0.9);
	r.strokeRange = false;
	r.showHandle_(true).handleColor_(col);
	r.levelFillColor = col.alpha_(0.2);
	r.strokeLevel = true;
	r.levelStroke = \outside;
	r.levelStrokeWidth = 3;
	r.valueFontSize = 12;
	r.valuePosition = \right;
	r.showTicks = true;
	r.numTicks_(15, 5);
	r.tickColor = Color.gray;
	r.action = {|val| val.postln};
	r;
};

w = Window(bounds:Rect(100,100, 140*8, 150)).front.layout_(HLayout(*8.collect({setupRot.()})
)
)
)




r.spec = [-inf, inf, \lin].asSpec;
r.value
r.input

r.startAngle_(0)
r.direction = \ccw

r.startAngle_(-0.1pi)
r.direction
r.orientation = \circular
r.orientation = \vertical
r.orientation = \horizontal

r.value=0
*/