// strokeColor and fillColor variables: nil disables stroke/fill

// TODO
// user defines startAngle/End/Sweep as radian or deg?

Rotary : View {

	var spec;

	// movement
	var <direction, <startAngle, <sweepLength, <orientation;
	var valuePerPixel, valuePerRadian;
	//range
	var rangeFillColor, rangeStrokeColor, rangeStrokeType, rangeStrokeWidth;
	// level
	var levelFillColor, levelStrokeColor;
	// handle
	var showHandle, handleStrokeColor, handleStrokeWidth, handleFillColor, handleWidth;



	// ticks
	var majorTickLen, minorTickLen;

	var <rView; // the rotary view
	var <value;
	var dirFlag; // changes with direction: cw=1, ccw=-1
	var prStartAngle; // start angle used internally, reference 0 to the RIGHT, as used in addAnnularWedge
	var prSweepLength; // sweep length used internally, = sweepLength * dirFlag
	var moveRelative = true;  // false means value jumps to click, TODO: disabled for infinite movement

	// private
	var rViewCen;
	var stValue;
	var mDownPnt;

	*new { arg parent, bounds, label, labelAlign, rangeLabelAlign, levelLabelAlign;
		^super.new(parent, bounds).init(label, labelAlign, rangeLabelAlign, levelLabelAlign)
	}

	init { |label, labelAlign, rangeLabelAlign, levelLabelAlign|

		value = 0; // TODO: default to spec.default
		spec = \unipolar.asSpec;

		direction = \cw;
		startAngle = 0; // reference 0 is UP
		sweepLength = 2pi;
		orientation = \vertical;

		valuePerPixel = spec.range / 300;
		valuePerRadian = spec.range / sweepLength;

		// range
		rangeFillColor = Color.gray;
		rangeStrokeColor = Color.black;
		rangeStrokeType = \around;		// \inside, \outside, \around
		rangeStrokeWidth = 2;

		// level
		levelFillColor = Color.white;
		levelStrokeColor = Color.green;

		// handle
		showHandle = true;
		handleStrokeColor = Color.blue; // nil disables stroke
		handleStrokeWidth = 2;
		handleFillColor = Color.yellow;
		handleWidth = 3;

		// ticks
		majorTickLen = 0.5;
		minorTickLen = 0.25;



		rView = UserView(this, this.bounds)
		.resize_(5);

		this.direction = direction; // this initializes prStarAngle and prSweepLength

		this.defineDrawFunc;
		this.defineInteraction;

	}

	// DRAW
	defineDrawFunc {
		rView.drawFunc_({ |v|
			var bnds, cen;
			bnds = v.bounds;
			cen  = bnds.center;
			rViewCen = cen;

			Pen.fillColor_(rangeFillColor);
			Pen.addAnnularWedge(cen, 5, cen.x, prStartAngle, prSweepLength);
			Pen.fill;

			Pen.fillColor_(levelFillColor);
			Pen.addAnnularWedge(cen, 5, cen.x, prStartAngle, prSweepLength*value);
			Pen.fill;

		})
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
		postf("downAngle: %\n", stRad);

		endPos = (mMovePnt - rViewCen);
		endRad = atan2(endPos.y, endPos.x);
		postf("moveAngle: %\n", endRad);

		// dRad = endRad - stRad * dirFlag ;

		// stRad.isNegative.if({
		// 	dRad = endRad.abs.neg - stRad * dirFlag ;
		// 	},{
		// 		dRad = endRad.abs - stRad * dirFlag ;
		// });


		dRad = (endRad - stRad).fold(0, pi) * dirFlag * (endRad - stRad).sign;

		postf("move % degrees\n", dRad.raddeg);

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
		prStartAngle = -0.5pi + (radians*dirFlag);
		this.refresh;
	}

	sweepLength_ { |radians=2pi|
		sweepLength = radians;
		prSweepLength = sweepLength * dirFlag;
		valuePerRadian = spec.range / sweepLength;
		this.refresh;
	}

	majorTickLength_ { |ratio = 0.5|
	}
	minorTickLength_ { |ratio = 0.25|
	}

	orientation_ { |vertHorizOrCirc = \vertical|
		orientation = vertHorizOrCirc;
	}

}

/*
r = Rotary(bounds: Size(300,300).asRect).front
r.startAngle_(-0.75pi)
r.startAngle_(0)
r.direction = \cw
r.sweepLength = 1.5pi
r.startAngle_(-0.1pi)
r.direction
r.orientation = \circular
r.orientation = \vertical
r.orientation = \horizontal

r.value=0
*/