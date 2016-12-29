// ValueView: a wrapper for UserView to make custom widgets
// that hold a mapped 'value' and normalized 'input', and draws
// custom layers in a UserView

// TODO: provide methods for common interaction calculations:
// -distFromCenter, -

ValueView : View {
	var <spec, <value, <input, <action, <userView;
	var <>wrap=false;
	var mouseDownPnt, mouseUpPnt, mouseMovePnt;
	var <>mouseDownAction, <>mouseUpAction, <>mouseMoveAction;
	var <>valuePerPixel;

	*new { |parent, bounds, spec, initVal |
		^super.new(parent, bounds).superInit(spec, initVal); //.init(*args)
	}

	superInit { |argSpec, initVal|
		spec = argSpec ?? \unipolar.asSpec;
		value = initVal ?? spec.default;
		input = spec.unmap(value);
		action = {};
		valuePerPixel = spec.range / 200; // for interaction: movement range in pixels to cover full spec range

		userView = UserView(this, this.bounds.origin_(0@0)).resize_(5);
		userView.drawFunc_(this.drawFunc);
		// over/write mouse actions
		// TODO: make a complete list
		userView.mouseMoveAction_({
			|v,x,y|
			mouseMovePnt = x@y;
			mouseMoveAction.(v,x,y)
		});
		userView.mouseDownAction_({
			|v,x,y|
			mouseDownPnt = x@y;
			mouseDownAction.(v,x,y)
		});
		userView.mouseUpAction_({
			|v,x,y|
			mouseUpPnt = x@y;
			mouseUpAction.(v,x,y)
		});

		this.onResize_({userView.bounds_(this.bounds.origin_(0@0))});
	}

	// init { this.subclassResponsibility(thisMethod) }

	drawFunc { this.subclassResponsibility(thisMethod) }

	value_ {|val|

		// TODO: should wrap option be default behavior?
		// or should subclass add this via method overwrite
		// as desired?
		value = if (wrap) {
			val.wrap(spec.minval, spec.maxval);
		} {
			spec.constrain(val);
		};
		input = spec.unmap(value);

		// TODO: add a notify flag instead of automatically notifying?
		// notify dependants
		this.changed(\value, value);
		this.changed(\input, input);
		this.refresh;
	}

	// set the value by unmapping a normalized value 0>1
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

	spec_ {|controlSpec, updateValue=true|
		spec = controlSpec;
		updateValue.if{this.value_(value)};
	}

	refresh { userView.refresh }

	rangeInPixels_ { |px|
		valuePerPixel = spec.range/px;
	}
}

RotaryView : ValueView {

	// variables to be use by this class which don't need getters
	var innerRadiusRatio, boarderPx, boarderPad;
	var stValue, stInput; //, clickMode;

	// create variables with getters which you want
	// the drawing layers to access
	var <direction, <orientation, <bipolar, <startAngle, <sweepLength;
	var <prCenterAngle, <centerNorm, <centerValue, <colorValBelow;
	var <bnds, <cen, <radius, <innerRadius, <wedgeWidth;  // set in drawFunc
	var <dirFlag; 			// cw=1, ccw=-1
	var <prStartAngle;		// start angle used internally, reference 0 to the RIGHT, as used in addAnnularWedge
	var <prSweepLength; 	// sweep length used internally, = sweepLength * dirFlag
	var <levelSweepLength;
	var <majTicks, <minTicks, majTickVals, minTickVals;

	// drawing layers. Add getters to get/set individual properties by '.p'
	var <range, <level, <text, <ticks, <handle;



	*new {
		|parent, bounds, spec, initVal, innerRadiusRatio, startAngle=0, sweepLength=2pi|
		^super.new(parent, bounds, spec, initVal).init(innerRadiusRatio, startAngle, sweepLength);
	}


	init {
		|argInnerRadiusRatio, argStartAngle, argSweepLength|

		// initialize layer classes and save them to vars
		#range, level, text, ticks, handle = [
			RotaryRangeLayer, RotaryLevelLayer, RotaryTextLayer,
			RotaryTickLayer, RotaryHandleLayer
		].collect({
			|class|
			class.new(this, class.properties)
		});

		innerRadiusRatio = argInnerRadiusRatio ?? {0};
		startAngle = argStartAngle; // reference 0 is UP
		sweepLength = argSweepLength;
		direction = \cw;
		dirFlag = 1;
		orientation = \vertical;
		wrap = false;
		// clickMode = \relative; // or \absolute, in which case value snaps to where mouse clicks
		boarderPx = 1;
		boarderPad = boarderPx;

		bipolar = false;
		centerValue = spec.minval+spec.range.half;
		centerNorm = spec.unmap(centerValue);
		colorValBelow = 0.65; // shift level color by this value below center

		valuePerPixel = spec.range / 200; // for interaction: movement range in pixels to cover full spec range
		// valuePerRadian = spec.range / sweepLength;

		majTicks = [];
		minTicks = [];
		majTickVals = [];
		minTickVals = [];

		this.defineMouseActions;
		this.direction_(direction);  // this initializes prStarAngle and prSweepLength
	}

	drawFunc {
		^{|v|
			// var in;
			// "global" instance vars
			bnds = v.bounds;
			cen  = bnds.center;
			radius = min(cen.x, cen.y) - boarderPad;
			innerRadius = radius*innerRadiusRatio;

			// in = if (levelFollowsValue) {input} {levelInput};
			// levelSweepLength = if (bipolar,{in - centerNorm},{in}) * prSweepLength;
			levelSweepLength = if (bipolar,{input - centerNorm},{input}) * prSweepLength;

			this.drawInThisOrder;
		}
	}

	drawInThisOrder {
		// define for proper layering
		if (range.p.fill) {range.fill};
		if (level.p.fill) {level.fill};
		if (ticks.p.show) {ticks.fill; ticks.stroke};
		if (range.p.stroke) {range.stroke};
		if (level.p.stroke) {level.stroke};
		if (handle.p.show) {handle.fill; handle.stroke};
		if (text.p.show) {text.fill; text.stroke};
	}

	defineMouseActions {

		mouseDownAction = {
			|v, x, y|
			// mouseDownPnt = x@y; // set for moveAction
			stValue = value;
			stInput = input;
		};

		mouseMoveAction  = {
			|v, x, y|
			switch (orientation,
				\vertical, {this.respondToLinearMove(mouseDownPnt.y-y)},
				\horizontal, {this.respondToLinearMove(x-mouseDownPnt.x)},
				\circular, {this.respondToCircularMove(x@y)}
			);
		};
	}

	respondToLinearMove {|dPx|
		if (dPx != 0) {this.valueAction_(stValue + (dPx * valuePerPixel))};
		this.refresh;
	}

	// radial change, relative to center
	respondToCircularMove {|mMovePnt|
		var stPos, endPos, stRad, endRad, dRad;

		stPos = (mouseDownPnt - cen);
		stRad = atan2(stPos.y,stPos.x);

		endPos = (mMovePnt - cen);
		endRad = atan2(endPos.y, endPos.x);

		dRad = (endRad - stRad).fold(0, pi) * dirFlag * (endRad - stRad).sign;
		// postf("move % degrees\n", dRad.raddeg);

		if (dRad !=0) {
			this.input_(stInput + (dRad/sweepLength)); // causes refresh
			this.doAction;
		};

		// allow continuous updating of relative start point
		mouseDownPnt = mMovePnt;
		stValue = value;
		stInput = input;
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
		this.setPrCenter;
		this.ticksAtValues_(majTickVals, minTickVals, false); // refresh the list of maj/minTicks positions
		// this.refresh;
	}

	setPrCenter {
		prCenterAngle = -0.5pi + startAngle + (centerNorm*sweepLength);
		this.refresh;
	}

	centerValue_ {|value|
		centerValue = spec.constrain(value);
		centerNorm = spec.unmap(centerValue);
		this.setPrCenter;
	}

	sweepLength_ {|radians=2pi|
		sweepLength = radians;
		prSweepLength = sweepLength * dirFlag;
		// valuePerRadian = spec.range / sweepLength;
		this.setPrCenter;
		this.ticksAtValues_(majTickVals, minTickVals, false); // refresh the list of maj/minTicks positions
		// this.refresh;
	}

	orientation_ {|vertHorizOrCirc = \vertical|
		orientation = vertHorizOrCirc;
	}

	innerRadiusRatio_ {|ratio|
		innerRadiusRatio = ratio;
		this.refresh
	}

	bipolar_ {|bool|
		bipolar = bool;
		this.refresh;
	}

	/* Ticks */

	// showTicks_ {|bool|
	// 	showTicks = bool;
	// 	this.refresh;
	// }
	//
	// majorTickRatio_ {|ratio = 0.25|
	// 	majorTickRatio = ratio;
	// 	this.refresh;
	// }
	//
	// minorTickRatio_ {|ratio = 0.15|
	// 	minorTickRatio = ratio;
	// 	this.refresh;
	// }
	//
	// // \inside, \outside, \center
	// tickAlign_ {|insideOutSideCenter|
	// 	case
	// 	{
	// 		(insideOutSideCenter == \inside) or:
	// 		(insideOutSideCenter == \outside) or:
	// 		(insideOutSideCenter == \center)
	// 	} {
	// 		tickAlign = insideOutSideCenter;
	// 		this.refresh;
	// 	}
	// 	{ "Rotary:tickAlign_ : Invalid align argument. Must be 'inside', 'outside' or 'center'".warn }
	// }

	// arrays of radian positions, reference from startAngle
	ticksAt_ {|majorRadPositions, minorRadPositions, show=true|
		majTicks = majorRadPositions;
		minTicks = minorRadPositions;
		majTickVals = spec.map(majTicks / sweepLength);
		minTickVals = spec.map(minTicks / sweepLength);
		// show.if{showTicks = true};
		show.if{ticks.show = true};
		this.refresh;
	}

	// ticks at values unmapped by spec
	ticksAtValues_ {|majorVals, minorVals, show=true|
		majTicks = spec.unmap(majorVals)*sweepLength;
		minTicks = spec.unmap(minorVals)*sweepLength;
		majTickVals = majorVals;
		minTickVals = minorVals;
		// show.if{showTicks = true};
		show.if{ticks.show = true};
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

	// tickColor_ {|color|
	// 	tickColor = color;
	// 	this.refresh;
	// }
}