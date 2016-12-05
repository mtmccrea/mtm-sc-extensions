// strokeColor and fillColor variables: nil disables stroke/fill

// TODO
// user defines startAngle/End/Sweep as radian or deg?

Rotary : View {

	var spec;

	// movement
	var <direction, <startAngle, <sweepLength, <orientation;
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

	var rViewCen;

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
		})
	}

	// INTERACT
	defineInteraction {
		var mDownPnt;

		rView.mouseDownAction_({|view, x, y|
			mDownPnt = x@y; // set for moveAction
		});

		rView.mouseMoveAction_({|view, x, y|
			switch (orientation,
				\vertical, {this.respondToLinearMove(mDownPnt.y-y)},
				\horizontal, {this.respondToLinearMove(x-mDownPnt.x)},
				\circular, {this.respondToCircularMove(mDownPnt, x@y)}
			);

		});
	}

	respondToLinearMove { |dPx|
		postf("move % pixels\n", dPx);
		if (dPx !=0) {

		};
	}

	// radial change, relative to center
	respondToCircularMove { |mDownPnt, mMovePnt|
		var stPos, endPos, stRad, endRad, dRad;

		stPos = (mDownPnt - rViewCen);
		stRad = atan2(stPos.y,stPos.x);
		postf("downAngle: %\n", stRad);

		endPos = (mMovePnt - rViewCen);
		endRad = atan2(endPos.y, endPos.x);
		postf("moveAngle: %\n", endRad);

		dRad = endRad - stRad * dirFlag;

		postf("move % degrees\n", dRad.raddeg);

		if (dRad !=0) {

		};
	}

	refresh {
		rView.refresh;
	}


	value_ { |val|

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
r.startAngle_(0.1pi)
r.startAngle_(0)
r.direction = \ccw
r.sweepLength = 1.5pi
r.startAngle_(-0.1pi)
r.direction
r.orientation = \circular
r.orientation = \vertical
r.orientation = \horizontal
*/