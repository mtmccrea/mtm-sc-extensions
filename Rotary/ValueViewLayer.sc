ValueViewLayer {
	var view, <>p; // properties

	*new { |valueView, initProperties|
		^super.newCopyArgs(valueView, initProperties).register
	}

	register {
		this.addDependant(view);
	}

	// catch setters and forward to setting properties
	doesNotUnderstand {|selector, value|
		var asGetter = selector.asGetter;

		if (selector.isSetter and: p[asGetter].notNil) {
			p[asGetter] = value;
			this.changed(\layerProperty, asGetter, value);
		} {
			^p[selector];
		};
	}

	properties {^p}

	getJoinIndex {|style|
		if (style.isKindOf(Number))
		{^style}
		{
			^switch(style,
				\miter, {0}, 			// pointed corners, will overshoot vertex at sharp angles
				\round, {1}, 			// round corners
				\bevel, {2}, 			// snubbed corners
				{0} 							// default
			)
		}
	}

	getCapIndex {|style|
		if (style.isKindOf(Number))
		{^style}
		{
			^switch(style,
				\butt,	{0},			// square end that doesn't overshoot endpoint, i.e. "butts" up angainst the endpoint
				\flat,	{0},			// same as butt, as Qt calls it
				\round, {1},			// round end that overshoots endpoint by half pen width, i.e. center of the brush stops on the endpoint
				\square,{2},			// square end that overshoots endpoint by half pen width, i.e. center of the brush stops on the endpoint
				{0} 							// default
			)
		}
	}
}

/*
 * For subclasses of ValueViewLayer:
 * access properties with instance variable 'p'
 * access valueView variables with instance variable 'view'
*/

// Superclass for RotaryRangeLayer and RotaryLevelLayer
RotaryArcWedgeLayer : ValueViewLayer {

	fillFromLength { |sweepFrom, sweepLength|
		var arcStrokeWidth, arcStrokeRadius;
		Pen.push;
		switch (p.style,
			\wedge, {
				Pen.fillColor_(p.fillColor);
				Pen.addAnnularWedge(
					view.cen, view.outerRadius - (view.wedgeWidth*p.width),
					view.outerRadius*p.radius,
					sweepFrom, sweepLength
				);
				Pen.fill;
			},
			\arc, {
				arcStrokeWidth = view.wedgeWidth*p.width;
				arcStrokeRadius = (view.wedgeWidth*p.radius) + view.innerRadius - (arcStrokeWidth*0.5);
				Pen.capStyle_(this.getCapIndex(p.capStyle));
				Pen.width_(arcStrokeWidth);
				Pen.addArc(view.cen, arcStrokeRadius, sweepFrom, sweepLength);
				Pen.stroke;
			},
			{"style property doesn't match either \wedge or \arc".warn}
		);
		Pen.pop;
	}

	strokeFromLength { |sweepFrom, sweepLength|
		var inset, strokeWidth;
		Pen.push;
		Pen.strokeColor_(p.strokeColor);
		strokeWidth = if (p.strokeWidth<1){p.strokeWidth*view.maxRadius}{p.strokeWidth};
		Pen.width_(strokeWidth);
		// inset accounts for pen width overshooting the outer radius
		inset = strokeWidth*0.5;
		Pen.capStyle_(this.getCapIndex(p.capStyle));
		switch (p.strokeType,
			\around, {
				Pen.joinStyle = this.getJoinIndex(p.joinStyle);
				Pen.addAnnularWedge(view.cen, view.innerRadius, view.outerRadius-inset, sweepFrom, sweepLength);
			},
			\inside, {
				Pen.capStyle = this.getCapIndex(p.capStyle);
				Pen.addArc(view.cen, view.innerRadius+inset, sweepFrom, sweepLength);
			},
			\outside, {
				Pen.capStyle = this.getCapIndex(p.capStyle);
				Pen.addArc(view.cen, view.outerRadius-inset, sweepFrom, sweepLength);
			},
			\insideOutside, {
				Pen.capStyle = this.getCapIndex(p.capStyle);
				Pen.addArc(view.cen, view.innerRadius+inset, sweepFrom, sweepLength);
				Pen.addArc(view.cen, view.outerRadius-inset, sweepFrom, sweepLength);
			},
			{"strokeType property doesn't match \around, \inside, \outside, or \insideOutside".warn}
		);
		Pen.stroke;
		Pen.pop;
	}

}

RotaryRangeLayer : RotaryArcWedgeLayer {
	// define default properties in an Event as a class method
	*properties {
		^(
			show:					true,					// show this layer or not
			style:				\wedge,				// \wedge or \arc: annularWedge or arc
			width:				1,						// width of either annularWedge or arc; relative to wedgeWidth
			radius:				1,						// outer edge of the wedge or arc; relative to maxRadius
																	// TODO: rename this?
			fill:		 			true,					// if annularWedge
			fillColor:		Color.gray.alpha_(0.3),
			stroke:				true,
			strokeColor:	Color.gray,
			strokeType:		\around, 			// if style: \wedge; \inside, \outside, or \around
			strokeWidth:	1, 						// if style: \wedge, if < 1, assumed to be a normalized value and changes with view size, else treated as a pixel value
			capStyle:			\round				// if style: \arc
			joinStyle:	 	0,						// if style: \wedge; 0=flat
		)
	}

	fill {
		this.fillFromLength(view.prStartAngle, view.prSweepLength)
	}

	stroke {
		this.strokeFromLength(view.prStartAngle, view.prSweepLength)
	}
/*
	fill {
		var arcStrokeWidth, arcStrokeRadius;
		Pen.fillColor_(p.fillColor);
		switch (p.style,
			\wedge, {
			Pen.addAnnularWedge(
				view.cen, view.outerRadius - (view.wedgeWidth*p.width),
				view.outerRadius*p.radius,
				view.prStartAngle, view.prSweepLength
			);
			Pen.fill;
		},
		\arc, {
			arcStrokeWidth = view.wedgeWidth*p.width;
			arcStrokeRadius = (view.wedgeWidth*p.radius) + view.innerRadius - (arcStrokeWidth*0.5);
			Pen.capStyle_(this.getCapIndex(p.capStyle));
			Pen.width_(arcStrokeWidth);
			Pen.addArc(view.cen, arcStrokeRadius, view.prStartAngle, view.prSweepLength)
			Pen.stroke;
		},
		{"style property doesn't match either \wedge or \arc".warn}
	);
	}

	stroke {
		var inset, strokeWidth;
		Pen.push;
		Pen.strokeColor_(p.strokeColor);
		strokeWidth = if (p.strokeWidth<1){p.strokeWidth*view.maxRadius}{p.strokeWidth};
		Pen.width_(strokeWidth);
		// inset accounts for pen width overshooting the outer radius
		inset = strokeWidth*0.5;
		Pen.capStyle_(this.getCapIndex(p.capStyle));
		switch (p.strokeType,
			\around, {
				Pen.joinStyle = this.getJoinIndex(p.joinStyle);
				Pen.addAnnularWedge(view.cen, view.innerRadius, view.outerRadius-inset, view.prStartAngle, view.prSweepLength);
			},
			\inside, {
				Pen.capStyle = this.getCapIndex(p.capStyle);
				Pen.addArc(view.cen, view.innerRadius+inset, view.prStartAngle, view.prSweepLength);
			},
			\outside, {
				Pen.capStyle = this.getCapIndex(p.capStyle);
				Pen.addArc(view.cen, view.outerRadius-inset, view.prStartAngle, view.prSweepLength);
			},
			\insideOutside, {
				Pen.capStyle = this.getCapIndex(p.capStyle);
				Pen.addArc(view.cen, view.innerRadius+inset, view.prStartAngle, view.prSweepLength);
				Pen.addArc(view.cen, view.outerRadius-inset, view.prStartAngle, view.prSweepLength);
			},
			{"strokeType property doesn't match \around, \inside, \outside, or \insideOutside".warn}
		);
		Pen.stroke;
		Pen.pop;
	}
	*/
}

RotaryLevelLayer : RotaryArcWedgeLayer {

	*properties {
		^(
			show:					true,
			style:				\wedge,				// \wedge or \arc: annularWedge or arc
			width:				1,						// width of either annularWedge or arc; relative to wedgeWidth
			radius:				1,						// outer edge of the wedge or arc; relative to maxRadius (1)
																	// TODO: rename this?
			fill: 		 		true,					// if style: \wedge
			fillColor: 	 	Color.white,
			stroke: 	 		true,
			strokeColor: 	Color.green,
			strokeType:  	\around,			// if style: \wedge; \inside, \outside, or \around
			strokeWidth: 	2,						// if style: \wedge, if < 1, assumed to be a normalized value and changes with view size, else treated as a pixel value
			capStyle:	 		0, 						// if style: \arc or \wedge with strokeType != \around
			joinStyle:	 	0,						// if style: \wedge; 0=flat
		)
	}

	getStartAngle {
		^if (view.bipolar) {view.prCenterAngle} {view.prStartAngle}
	}

	fill {
		this.fillFromLength(this.getStartAngle, view.levelSweepLength)
	}

	stroke {
		this.strokeFromLength(this.getStartAngle, view.levelSweepLength)
	}
/*
	fill {
		var arcStrokeWidth, arcStrokeRadius;
		var stAngle, col;

		Pen.push;
		col = p.fillColor;
		if (view.bipolar) {
			stAngle = view.prCenterAngle;
		} {
			stAngle = view.prStartAngle;
		};
		switch (p.style,
				\wedge, {
				Pen.addAnnularWedge(
					view.cen, view.outerRadius - (view.wedgeWidth*p.width),
					view.outerRadius*p.radius,
					view.prStartAngle, view.levelSweepLength
				);
				Pen.fill;
			},
			\arc, {
				arcStrokeWidth = view.wedgeWidth*p.width;
				arcStrokeRadius = (view.wedgeWidth*p.radius) + view.innerRadius - (arcStrokeWidth*0.5);
				Pen.capStyle_(this.getCapIndex(p.capStyle));
				Pen.width_(arcStrokeWidth);
				Pen.addArc(view.cen, arcStrokeRadius, view.prStartAngle, view.levelSweepLength)
				Pen.stroke;
			},
			{"style property doesn't match either \wedge or \arc".warn}
		);
		Pen.pop;
	}

	stroke {
		var stAngle, col, inset, strokeWidth;

		Pen.push;
		col = p.strokeColor;
		if (view.bipolar) {
			stAngle = view.prCenterAngle;
			if (view.input<view.centerNorm) {
				col = Color.hsv(*col.asHSV * [1,1,view.colorValBelow, 1]);
			}
		} {
			stAngle = view.prStartAngle;
		};
		strokeWidth = if (p.strokeWidth<1){p.strokeWidth*view.maxRadius}{p.strokeWidth};
		Pen.width_(strokeWidth);
		inset = strokeWidth*0.5;
		Pen.strokeColor_(col);
		switch (p.strokeType,
			\around, {
				Pen.joinStyle = this.getJoinIndex(p.joinStyle);
				Pen.addAnnularWedge(view.cen, view.innerRadius, view.radius-inset, stAngle, view.levelSweepLength);
			},
			\inside, {
				Pen.capStyle = this.getCapIndex(p.capStyle);
				Pen.addArc(view.cen, view.innerRadius+inset, stAngle, view.levelSweepLength);
			},
			\outside, {
				Pen.capStyle = this.getCapIndex(p.capStyle);
				Pen.addArc(view.cen, view.radius-inset, stAngle, view.levelSweepLength);
			},
			\insideOutside, {
				Pen.capStyle = this.getCapIndex(p.capStyle);
				Pen.addArc(view.cen, view.innerRadius+inset, stAngle, view.levelSweepLength);
				Pen.addArc(view.cen, view.radius-inset, stAngle, view.levelSweepLength);
			},
		);
		Pen.stroke;
		Pen.pop;
	}
*/
}

RotaryTextLayer : ValueViewLayer {

	*properties {
		^(
			show: true,
			align: \center, // \top, \bottom, \center, \left, \right, or Point()
			fontSize: 12,
			font: {|me| Font("Helvetica", me.fontSize)},
			color: Color.black,
			round: 0.1,
		)
	}

	fill {
		var v, bnds, rect, half;
		v = view.value.round(p.round).asString;
		bnds = view.bnds;
		Pen.push;
		Pen.fillColor_(p.color);
		if (p.align.isKindOf(Point)) {
			rect = bnds.center_(bnds.extent*p.align);
			Pen.stringCenteredIn(v, rect, p.font, p.color);
		} {
			rect = switch (p.align,
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
			Pen.stringCenteredIn(v, rect, p.font, p.color)
		};
		Pen.fill;
		Pen.pop;
	}

	stroke {}
}

RotaryTickLayer : ValueViewLayer {

	*properties {
		^(
			show: 				false,					// show ticks or not
			anchor:				1,							// position/radius where the ticks are "anchored", relative to wedgeWidth
			align: 				\outside,				// specifies the direction the tick is drawn from anchor; \inside, \outside, or \center
			majorLength:	0.25,						// length of major ticks, realtive to maxRadius (1)
			minorLength:	0.6,						// length of minor ticks, realtive to majorLength

			majorWidth: 	1,							// width of major tick, in pixels, TODO: this could be relative to windowSize if < 1
			minorWidth: 	0.5,						// width of minor tick, realtive to majorWidth
			majorColor: 	Color.black,
			minorColor: 	Color.gray
		)
	}

	fill {}

	stroke {
		var majLen;
		Pen.push;
		Pen.translate(view.cen.x, view.cen.y);
		Pen.rotate(view.prStartAngle);
		majLen = p.majorLength * view.wedgeWidth;
		this.drawTicks(	// major ticks
			view.majTicks, majLen, p.majorWidth, p.majorColor);
		this.drawTicks(	// minor ticks
			view.minTicks, p.minorLength * majLen, p.minorWidth * p.majorWidth, p.minorColor);
		Pen.pop;
	}

	drawTicks { |ticks, tickLength, strokeWidth, color|
		var penSt_temp, penSt, penEnd;
		// start the pen on the inside end of the line and draw outward
		penSt_temp = view.innerRadius + (p.anchor*view.wedgeWidth);
		penSt = switch (p.align,
			\inside, {penSt_temp},
			\outside, {penSt_temp - tickLength},
			\center, {penSt_temp - (tickLength * 0.5)},
			{"Tick 'align' property isn't \inside, \outside, or \center".warn; 0}
		);
		penEnd = penSt + tickLength;

		Pen.push;
		Pen.strokeColor_(color);
		ticks.do{|tickPos|
			Pen.width_(strokeWidth);
			Pen.moveTo(penSt@0);
			Pen.push;
			Pen.lineTo(penEnd@0);
			Pen.rotate(tickPos * view.dirFlag);
			Pen.stroke;
			Pen.pop;
		};
		Pen.pop;
	}
}

RotaryHandleLayer : ValueViewLayer {

	*properties {
		^(
			show:		true,							// show handle or not
			style:	\line,	 					// \line, \circle, or \arrow
			fill:		true,							// if style = \circle or \arrow
			fillColor: Color.red,
			stroke: true,							// if style = \circle or \arrow
			strokeColor: Color.black,
			strokeWidth:	2,					// if style = \line or \circle
			radius:	3,								// if style = \circle
			length: 0.3,							// if style = \line or \arrow
			width: 0.6,								// if style = \arrow, relative to length (width of 1 = length)
			anchor: 0.9,							// where the outer end of the handle is anchored, 0>1, relative to wedgeWidth
			capStyle:		\round,				// if style = \line
			joinStyle:	\round,				// if style = \arrow
		)
	}

	fill {}

	stroke {
		var cen;
		cen = view.cen;
		Pen.push;
		Pen.translate(cen.x, cen.y);
		switch (p.style,
			\line, {this.drawLine},
			\circle, {this.drawCircle},
			\lineAndCircle, {Pen.push ; this.drawLine; Pen.pop; this.drawCircle},
			\arrow, {this.drawArrow}
		);
		Pen.pop;
	}

	drawLine {
		var penSt;
		penSt = view.innerRadius+(view.wedgeWidth*p.anchor);
		Pen.capStyle = this.getCapIndex(p.capStyle);
		Pen.width = p.strokeWidth;
		Pen.strokeColor = p.strokeColor;
		Pen.moveTo(penSt@0);
		Pen.lineTo((penSt-(view.wedgeWidth*p.length))@0);
		Pen.rotate(view.prStartAngle+(view.prSweepLength*view.input));
		Pen.stroke;
	}

	drawCircle {
		var d, rect;
		d = p.radius*2;
		rect = Size(d, d).asRect;
		rect = rect.center_((view.innerRadius+(view.wedgeWidth*p.anchor))@0);
		Pen.rotate(view.prStartAngle+(view.prSweepLength*view.input));
		if (p.fill) {Pen.fillColor = p.fillColor; Pen.fillOval(rect)};
		if (p.stroke) {Pen.strokeColor = p.strokeColor; Pen.strokeOval(rect)};
	}

	drawArrow {
		var rect, w, h;
		h = view.wedgeWidth * p.length;
		w = h * p.width;
		rect = Size(h, w).asRect.center_(0@0); // note h<>w, rect starts at 3 o'clock

		// define rect enclosing the arrow
		// align determines location of the arrow's tip
		rect = rect.right_(view.innerRadius + (view.wedgeWidth*p.anchor));
		Pen.rotate(view.prStartAngle+(view.prSweepLength*view.input));
		Pen.moveTo(rect.right@0);
		Pen.lineTo(rect.leftBottom);
		Pen.lineTo((rect.left+(h*0.1))@0);
		Pen.lineTo(rect.leftTop);
		Pen.lineTo(rect.right@0);

		if (p.fill) {
			Pen.fillColor = p.fillColor;
			Pen.fill;
		};
		if (p.stroke) {
			Pen.strokeColor = p.strokeColor;
			Pen.width = p.strokeWidth;
			Pen.joinStyle = this.getJoinIndex(p.joinStyle);
			Pen.stroke;
		};
	}


	/*
		Set properties which require update
		to view's boarder padding as necessary
	*/

	radius_ {|px|
		p.radius = px;
		// update boarder pad
		if (p.style !='line') {view.boarderPad=view.boarderPad};
		view.refresh;
	}

	align_ {|where|
		p.align = where;
		// update boarder pad
		if (p.style !='line') {view.boarderPad=view.boarderPad};
		view.refresh;
	}

	style_ {|style|
		p.style = style;
		// update boarder pad
		if (style !='line') {view.boarderPad=view.boarderPad};
		view.refresh;
	}
}
