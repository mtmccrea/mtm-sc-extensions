// strokeColor and fillColor variables: nil disables stroke/fill

// TODO
// user defines startAngle/End/Sweep as radian or deg?
// fix continuous circular motion
// make this function like a real view
// consider ticks with annularWedge instead of lines


Rotary : View {

	var spec;

	// dimensions
	var innerRadiusRatio; // radius,

	// movement
	var <direction, <startAngle, <sweepLength, <orientation;
	var valuePerPixel, valuePerRadian;
	var clickMode;
	//range
	var rangeFillColor, rangeStrokeColor, rangeStroke, rangeStrokeWidth;
	// level
	var levelFillColor, levelStrokeColor;
	// handle
	var showHandle, handleColor, handleWidth;



	// ticks
	var showTicks, tickAnchor, majorTickRatio, minorTickRatio,
	<majTicks, <minTicks, majTickVals, minTickVals,
	tickStrokeColor, majorTickWidth, minorTickWidth;

	var <rView; // the rotary view
	var <value, <normValue;
	var dirFlag; // changes with direction: cw=1, ccw=-1
	var prStartAngle; // start angle used internally, reference 0 to the RIGHT, as used in addAnnularWedge
	var prSweepLength; // sweep length used internally, = sweepLength * dirFlag
	var moveRelative = true;  // false means value jumps to click, TODO: disabled for infinite movement
	var <view; // master view:
	// private
	var rViewCen;
	var stValue;
	var mDownPnt;

	*new { arg parent, bounds, innerRadiusRatio, label, labelAlign, rangeLabelAlign, levelLabelAlign;
		^super.new(parent, bounds).init(innerRadiusRatio, label, labelAlign, rangeLabelAlign, levelLabelAlign)
	}

	init { |argInnerRadiusRatio, label, labelAlign, rangeLabelAlign, levelLabelAlign|

		spec = \unipolar.asSpec;
		value = 0; // TODO: default to spec.default
		normValue = spec.unmap(value);


		// radius = argRadius ?? {this.bounds.width*0.5};
		innerRadiusRatio = argInnerRadiusRatio ?? {0};

		startAngle = 0; // reference 0 is UP
		sweepLength = 2pi;
		direction = \cw;
		dirFlag = 1;
		orientation = \vertical;
		clickMode = \relative; // or \absolute, in which case value snaps to where mouse clicks

		valuePerPixel = spec.range / 300;
		valuePerRadian = spec.range / sweepLength;

		// range
		rangeFillColor = Color.gray;
		rangeStroke = \around;		// \inside, \outside, \around
		rangeStrokeColor = Color.black;
		rangeStrokeWidth = 2;

		// level
		levelFillColor = Color.white;
		levelStrokeColor = Color.green;

		// handle
		showHandle = true;
		handleColor = Color.red; // nil disables stroke
		handleWidth = 2;
		// handleFillColor = Color.yellow;
		handleWidth = 3;

		// ticks
		showTicks = false;
		majTicks = [];
		minTicks = [];
		majTickVals = [];
		minTickVals = [];
		majorTickRatio = 0.25;
		minorTickRatio = 0.15;
		tickAnchor = \outside;
		majorTickWidth = 2;
		minorTickWidth = 1;
		tickStrokeColor = nil; // default to rangeStrokeColor

		view = View(this, this.bounds.extent.asRect).resize_(5);

		rView = UserView(view, bounds: this.bounds.extent.asRect)
		.resize_(5);

		this.direction = direction; // this initializes prStarAngle and prSweepLength

		this.defineDrawFunc;
		this.defineInteraction;

	}

	// DRAW
	defineDrawFunc {
		var bnds, cen, radius;
		var wedgeWidth, innerRad;
		var drRange, drLevel, drTicks, drRangeStroke, drHandle, drValueTxt, drLocalTicks;

		rView.drawFunc_({ |v|
			bnds = v.bounds;
			cen  = bnds.center;
			rViewCen = cen;
			radius = cen.x;

			innerRad = radius*innerRadiusRatio;
			wedgeWidth = radius - innerRad;

			drRange.();
			drLevel.();
			if (showTicks) {drTicks.()};
			if (showHandle) {drHandle.()};
			drRangeStroke.();
			drValueTxt.();

		});

		drRange = {
			Pen.fillColor_(rangeFillColor);
			Pen.addAnnularWedge(cen, innerRad, radius, prStartAngle, prSweepLength);
			Pen.fill;
		};

		drLevel = {
			Pen.fillColor_(levelFillColor);
			Pen.addAnnularWedge(cen, innerRad, radius, prStartAngle, prSweepLength*normValue);
			Pen.fill;
		};

		drTicks = {
			Pen.push;
			Pen.translate(cen.x, cen.y);
			Pen.rotate(prStartAngle);
			drLocalTicks.(majTicks, majorTickRatio, majorTickWidth);
			drLocalTicks.(minTicks, minorTickRatio, minorTickWidth);
			Pen.pop;
		};

		drLocalTicks = { |ticks, tickRatio, strokeWidth|
			var penSt, penEnd;
			penSt = switch (tickAnchor,
				\inside, {innerRad},
				\outside, {radius},
				{innerRad + (wedgeWidth - (wedgeWidth * tickRatio) * 0.5)} // \center
			);

			penEnd = if (tickAnchor == \outside) {
				penSt - (wedgeWidth * tickRatio)
			} { // \inside or \center
				penSt + (wedgeWidth * tickRatio)
			};

			ticks.do{ |tickRad|
				Pen.width_(strokeWidth);
				Pen.moveTo(penSt@0);
				Pen.push;
				Pen.lineTo(penEnd@0);
				Pen.rotate(tickRad * dirFlag);
				Pen.stroke;
				Pen.pop;
			};
		};

		drHandle = {
			Pen.push;
			Pen.translate(cen.x, cen.y);
			Pen.width_(handleWidth);
			Pen.strokeColor_(handleColor);
			Pen.moveTo(innerRad@0);
			Pen.lineTo(radius@0);
			Pen.rotate(prStartAngle+(prSweepLength*normValue));
			Pen.stroke;
			Pen.pop;
		};

		drRangeStroke = {
			var rad, inset;
			Pen.strokeColor_(rangeStrokeColor);
			Pen.width_(rangeStrokeWidth);
			inset = rangeStrokeWidth*0.5;
			switch (rangeStroke,
				\around, {
					Pen.addAnnularWedge(cen, innerRad, radius-inset, prStartAngle, prSweepLength);
				},
				\inside, {
					Pen.addArc(cen, innerRad+inset, prStartAngle, prSweepLength);
				},
				\outside, {
					Pen.addArc(cen, radius-inset, prStartAngle, prSweepLength);
				},
				\insideOutside, {
					Pen.addArc(cen, innerRad+inset, prStartAngle, prSweepLength);
					Pen.addArc(cen, radius-inset, prStartAngle, prSweepLength);
				},
			);
			Pen.stroke;
		};


		drValueTxt = {};
	}

	// INTERACT
	defineInteraction {

		rView.mouseDownAction_({|view, x, y|
			mDownPnt = x@y; // set for moveAction
			stValue = value;
		});

		rView.mouseMoveAction_({|view, x, y|
			switch (orientation,
				\vertical, {this.respondToLinearMove(mDownPnt.y-y)},
				\horizontal, {this.respondToLinearMove(x-mDownPnt.x)},
				\circular, {this.respondToCircularMove(x@y)}
			);
		});
	}

	respondToLinearMove { |dPx|
		postf("move % pixels\n", dPx);
		if (dPx !=0) {
			this.value = stValue + (dPx * valuePerPixel);
		};

		this.refresh;
	}

	// radial change, relative to center
	respondToCircularMove { |mMovePnt|
		var stPos, endPos, stRad, endRad, dRad;

		stPos = (mDownPnt - rViewCen);
		stRad = atan2(stPos.y,stPos.x);
		// postf("downAngle: %\n", stRad);

		endPos = (mMovePnt - rViewCen);
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
			this.value = stValue + (dRad * valuePerRadian); // causes refresh
		};

		// allow continuous updating of relative start point
		mDownPnt = mMovePnt;
		stValue = value;
	}

	refresh {
		rView.refresh;
	}


	value_ { |val|
		value = spec.constrain(val);
		normValue = spec.unmap(value);
		this.refresh;
	}

	label_ { |string, align=\top|
	}

	spec_ { |controlSpec|
	}

	direction_ { |dir=\cw|
		direction = dir;
		dirFlag = switch (direction, \cw, {1}, \ccw, {-1});
		this.startAngle_(startAngle);
		this.sweepLength_(sweepLength); // updates prSweepLength
		this.refresh;
	}

	startAngle_ { |radians=0|
		startAngle = radians;
		prStartAngle = -0.5pi + startAngle; //*dirFlag); // start angle always relative to 0 is up, cw
		this.ticksAtValues_(majTickVals, minTickVals); // refresh the list of maj/minTicks positions
		// this.refresh;
	}

	sweepLength_ { |radians=2pi|
		sweepLength = radians;
		prSweepLength = sweepLength * dirFlag;
		valuePerRadian = spec.range / sweepLength;
		this.ticksAtValues_(majTickVals, minTickVals); // refresh the list of maj/minTicks positions
		// this.refresh;
	}

	majorTickRatio_ { |ratio = 0.25|
		majorTickRatio = ratio;
		this.refresh;
	}
	minorTickRatio_ { |ratio = 0.15|
		minorTickRatio = ratio;
		this.refresh;
	}

	orientation_ { |vertHorizOrCirc = \vertical|
		orientation = vertHorizOrCirc;
	}

	showTicks_ { |bool|
		showTicks = bool;
		this.refresh;
	}

	// \inside, \outside, \center
	tickAnchor_ { |anchor|
		tickAnchor = anchor;
		this.refresh;
	}

	// arrays of radian positions, reference from startAngle
	ticksAt_ { |majorRadPositions, minorRadPositions|
		majTicks = majorRadPositions;
		minTicks = minorRadPositions;
		majTickVals = spec.map(majTicks / sweepLength);
		minTickVals = spec.map(minTicks / sweepLength);
		this.refresh;
	}

	// ticks at values unmapped by spec
	ticksAtValues_ { |majorVals, minorVals|
		majTicks = spec.unmap(majorVals)*sweepLength;
		minTicks = spec.unmap(minorVals)*sweepLength;
		majTickVals = majorVals;
		minTickVals = minorVals;
		this.refresh;
	}

	// ticks values by value hop, unmapped by spec
	ticksEveryVal_ { |valueHop, majorEvery=2|
		// majTicks = majorTicks;
		// minTicks = minorTicks;
		this.refresh;
	}


	ticksEvery_ { |radienHop, majorEvery=2|
		this.refresh;
	}

	// evenly distribute ticks
	numTicks_ { |num, majorEvery=2|
		var hop, ticks, numMaj, majList, minList;
		hop = sweepLength / (num-1);
		ticks = num.collect{|i| i * hop};
		numMaj = num/majorEvery;
		majList = List(numMaj);
		minList = List(num-numMaj);
		ticks.do{|val, i| if ((i%majorEvery) == 0) {majList.add(val)} {minList.add(val)} };
		this.ticksAt_(majList, minList);
	}

	rangeFillColor_ {|color|
		rangeFillColor = color;
		this.refresh;
	}

	// \inside, \outside, \around
	rangeStroke_ { |insideOutsideAround|
		rangeStroke = insideOutsideAround;
		this.refresh;
	}

	rangeStrokeColor_ {|color|
		rangeStrokeColor = color;
		this.refresh;
	}

	rangeStrokeWidth_ { |px|
		rangeStrokeWidth = px;
		this.refresh;
	}

	showHandle_ { |bool|
		showHandle = bool;
		this.refresh;
	}

	handleColor_ { |color|
		handleColor = color;
		this.refresh;
	}

	handleWidth_ { |px|
		handleWidth = px;
		this.refresh;
	}

}

/*
r = Rotary(bounds: Size(300, 300).asRect, innerRadiusRatio: 0.1).front
r.startAngle_(-0.75pi)
r.sweepLength = 1.5pi

r.startAngle_(0)
r.direction = \ccw

r.startAngle_(-0.1pi)
r.direction
r.orientation = \circular
r.orientation = \vertical
r.orientation = \horizontal

r.value=0
*/