// ValueView: a wrapper to make custom widgets
// that hold a mapped 'value' and normalized 'input',
// and draws custom layers in a UserView, with mouse/arrow interaction

// TODO
// add absolute mode where knob jumps to click point

ValueView : View {
	var <spec, <value, <input, <action, <userView;
	var <>wrap=false;
	var mouseDownPnt, mouseUpPnt, mouseMovePnt;
	var <>mouseDownAction, <>mouseUpAction, <>mouseMoveAction;
	var <>valuePerPixel;
	var <layers; // array of drawing layers which respond to .properties
	var <maxUpdateRate=25, updateWait, allowUpdate=true, updateHeld=false;
	var <step; 			// used for scrollWheel and arrow keys, initialized to spec.step
	var <>arrowKeyStepMul=1;// scale step when arrow keys are pressed
	var <>scrollStepMul=1;	// scale step when scroll wheel steps
	var <>xScrollDir= 1;	// change scroll direction, -1 or 1
	var <>yScrollDir= -1;	// change scroll direction, -1 or 1, -1 is "natural" scrolling on Mac
	var <>arrowKeyDir=1;	// change step direction of arrow keys (useful for some UI behavior)

	*new { |parent, bounds, spec, initVal |
		^super.new(parent, bounds).superInit(spec, initVal); //.init(*args)
	}

	superInit { |argSpec, initVal|
		spec = argSpec ?? \unipolar.asSpec;
		value = initVal ?? spec.default;
		input = spec.unmap(value);
		action = {};
		valuePerPixel = spec.range / 200; // for interaction: movement range in pixels to cover full spec range
		updateWait = maxUpdateRate.reciprocal;
		step = if (spec.step == 0) {0.01} {spec.step};

		userView = UserView(this, this.bounds.origin_(0@0)).resize_(5);
		userView.drawFunc_(this.drawFunc);
		// over/write mouse actions
		// TODO: make a complete list
		userView.mouseMoveAction_({
			|v,x,y,modifiers|
			mouseMovePnt = x@y;
			mouseMoveAction.(v,x,y,modifiers)
		});
		userView.mouseDownAction_({
			|v,x,y, modifiers, buttonNumber, clickCount|
			mouseDownPnt = x@y;
			mouseDownAction.(v,x,y, modifiers, buttonNumber, clickCount)
		});
		userView.mouseUpAction_({
			|v,x,y, modifiers|
			mouseUpPnt = x@y;
			mouseUpAction.(v,x,y,modifiers)
		});

		userView.mouseWheelAction_({
			|v, x, y, modifiers, xDelta, yDelta|
			this.stepByMouseWheel(v, x, y, modifiers, xDelta, yDelta);
		});

		// add mouse wheel action directly
		// NOTE: if overwriting this function, include a call to
		// this.stepByArrow(key) to retain key inc/decrement capability
		userView.keyDownAction_ ({
			|view, char, modifiers, unicode, keycode, key|
			this.stepByArrow(key);
		});

		this.onResize_({userView.bounds_(this.bounds.origin_(0@0))});
		this.onClose_({}); // set default onClose to removeDependants
	}

	stepByMouseWheel {
		|v, x, y, modifiers, xDelta, yDelta|
		var dx, dy, delta;
		dx = xDelta * xScrollDir;
		dy = yDelta * yScrollDir;
		delta = step * (dx+dy).sign * scrollStepMul;
		this.valueAction = value + delta;
	}

	stepByArrow { |key|
		var dir, delta;
		dir = switch( key,
			16777234, {-1}, // left
			16777235, {1},  // up
			16777236, {1},  // right
			16777237, {-1}, // down
		);

		dir !? {
			delta = step * dir * arrowKeyDir * arrowKeyStepMul;
			this.valueAction = value + delta;
		}
	}

	// overwrite default View method to retain freeing dependants
	onClose_ {|func|
		var newFunc = {
			|...args|
			layers.do(_.removeDependant(this));
			func.(*args)
		};
		// from View:onClose_
		this.manageFunctionConnection( onClose, newFunc, 'destroyed()', false );
		onClose = newFunc;
	}

	update { |changer, what ...args|
		// refresh when layer properties change
		if (what == \layerProperty) {
			// postf("heard % % change to %\n", what, args[0], args[1]);
			this.refresh;
		}
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
		this.broadcastState;
	}

	// set the value by unmapping a normalized value 0>1
	input_ {|normValue|
		input = normValue.clip(0,1);
		value = spec.map(input);
		this.broadcastState;
	}

	broadcastState {
		// update the value and input in layers' properties list
		layers.do({|l| l.p.val = value; l.p.input = input});
		// TODO: add a notify flag instead of automatically notifying?
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
		var rangeInPx = spec.range/valuePerPixel; // get old pixels per range
		spec = controlSpec;
		this.rangeInPixels_(rangeInPx); // restore mouse scaling so it feels the same
		updateValue.if{this.value_(value)}; // also updates input
	}

	// refresh { userView.refresh }
	refresh {
		if (allowUpdate) {
			userView.refresh;
			allowUpdate = false;
			AppClock.sched(updateWait, {

				if (updateHeld) {  // perform deferred refresh
					userView.refresh;
					updateHeld = false;
				};
				allowUpdate = true;
			});
		} {
			updateHeld = true;
		};
	}

	rangeInPixels_ { |px|
		valuePerPixel = spec.range/px;
	}

	maxUpdateRate_ { |hz|
		maxUpdateRate = hz;
		updateWait = maxUpdateRate.reciprocal;
	}

	step_ {|stepVal|
		step = stepVal;
		spec.step_(step);
	}
}

RotaryView : ValueView {

	// variables to be use by this class which don't need getters
	var innerRadiusRatio, boarderPx, <boarderPad;
	var stValue, stInput, >clickMode;

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

		// REQUIRED: in subclass init, initialize drawing layers

		// initialize layer classes and save them to vars
		#range, level, text, ticks, handle = [
			RotaryRangeLayer, RotaryLevelLayer, RotaryTextLayer,
			RotaryTickLayer, RotaryHandleLayer
		].collect({
			|class|
			class.new(this, class.properties)
		});

		layers = [range, level, text, ticks, handle];

		innerRadiusRatio = argInnerRadiusRatio ?? {0};
		startAngle = argStartAngle; // reference 0 is UP
		sweepLength = argSweepLength;
		direction = \cw;
		dirFlag = 1;
		orientation = \vertical;
		wrap = false;
		clickMode = \relative; // or \absolute, in which case value snaps to where mouse clicks
		boarderPad = 1;
		boarderPx = boarderPad;

		bipolar = false;
		centerValue = spec.minval+spec.range.half;
		centerNorm = spec.unmap(centerValue);
		colorValBelow = 0.65; // shift level color by this value below center

		// valuePerPixel = spec.range / 200; // for interaction: movement range in pixels to cover full spec range
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
			// "global" instance vars
			bnds = v.bounds;
			cen  = bnds.center;
			radius = min(cen.x, cen.y) - boarderPx;
			innerRadius = radius*innerRadiusRatio;
			wedgeWidth = radius - innerRadius;
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

		// assign action variables: down/move
		mouseDownAction = {
			|v, x, y|
			// mouseDownPnt = x@y; // set for moveAction
			stValue = value;
			stInput = input;
			if (clickMode=='absolute') {this.respondToAbsoluteClick};
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
		if (dRad !=0) {
			this.input_(stInput + (dRad/sweepLength));			// triggers refresh
			this.doAction;
		};
		// allow continuous updating of relative start point
		mouseDownPnt = mMovePnt;
		stValue = value;
		stInput = input;
	}

	respondToAbsoluteClick {
		var pos, rad, radRel;
		pos = (mouseDownPnt - cen);
		rad = atan2(pos.y,pos.x);	// radian position, relative 0 at 3 o'clock
		radRel = rad + 0.5pi * dirFlag;			// radian position, relative 0 at 12 o'clock, clockwise
		radRel = (radRel - (startAngle*dirFlag)).wrap(0, 2pi);			// radian position, relative to start position
		if (radRel.inRange(0, sweepLength)) {
			this.input_(radRel/sweepLength); // triggers refresh
			this.doAction;
			stValue = value;
			stInput = input;
		};
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
		prStartAngle = -0.5pi + startAngle;						// start angle always relative to 0 is up, cw
		this.setPrCenter;
		this.ticksAtValues_(majTickVals, minTickVals, false);	// refresh the list of maj/minTicks positions
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

	boarderPad_ { |px|
		var align, overhang, alignRadius;
		align = handle.align;
		boarderPad = px;
		boarderPx = if (handle.type==\line)
		{boarderPad}
		{
			if (align==\outside)
			{boarderPx + handle.radius}
			{
				if (align.isKindOf(Number))
				{
					overhang = max(0, ((wedgeWidth * align) + (handle.radius)) - wedgeWidth);
					overhang + boarderPad
				}
				{boarderPad}
			}
		};
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
}