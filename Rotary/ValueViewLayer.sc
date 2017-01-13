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
				\miter, {0}, // pointed corners, will overshoot vertex at sharp angles
				\round, {1}, // round corners
				\bevel, {2}, // snubbed corners
				{0} // default
			)
		}
	}

	getCapIndex {|style|
		if (style.isKindOf(Number))
		{^style}
		{
			^switch(style,
				// square end that doesn't overshoot endpoint,
				// i.e. "butts" up angainst the endpoint
				\butt, {0},
				// same as butt, as Qt calls it
				\flat, {0},
				\round, {1},
				// square end that overshoots endpoint by half pen width,
				// i.e. center of the brush stops on the endpoint
				\square, {2},
				{0} // default
			)
		}
	}
}

RotaryRangeLayer : ValueViewLayer {
	// define default properties in an Event as a class method
	*properties {
		^(
			fill:		 true,
			fillColor:	 Color.gray,
			stroke:		 true,
			strokeType:	 \around,		// \inside, \outside, \around
			strokeColor: Color.black,
			strokeWidth: 1,
		)
	}

	// access properties with instance variable 'p'
	// access valueView variables with instance variable 'view'
	fill {
		Pen.fillColor_(p.fillColor);
		Pen.addAnnularWedge(view.cen, view.innerRadius, view.radius, view.prStartAngle, view.prSweepLength);
		Pen.fill;
	}

	stroke {
		var inset;
		Pen.push;
		Pen.strokeColor_(p.strokeColor);
		Pen.width_(p.strokeWidth);
		inset = p.strokeWidth*0.5;
		switch (p.strokeType,
			\around, {
				Pen.addAnnularWedge(view.cen, view.innerRadius, view.radius-inset, view.prStartAngle, view.prSweepLength);
			},
			\inside, {
				Pen.addArc(view.cen, view.innerRadius+inset, view.prStartAngle, view.prSweepLength);
			},
			\outside, {
				Pen.addArc(view.cen, view.radius-inset, view.prStartAngle, view.prSweepLength);
			},
			\insideOutside, {
				Pen.addArc(view.cen, view.innerRadius+inset, view.prStartAngle, view.prSweepLength);
				Pen.addArc(view.cen, view.radius-inset, view.prStartAngle, view.prSweepLength);
			},
		);
		Pen.stroke;
		Pen.pop;
	}
}

RotaryLevelLayer : ValueViewLayer {

	*properties {
		^(
			stroke: 	 true,
			strokeType:  \around,
			strokeColor: Color.green,
			strokeWidth: 2,
			fill: 		 true,
			fillColor: 	 Color.white,
			capStyle:	 0, // flat
			joinStyle:	 0,
		)
	}

	fill {
		var stAngle, col, inset;

		Pen.push;
		col = p.fillColor;
		if (view.bipolar) {
			stAngle = view.prCenterAngle;
		} {
			stAngle = view.prStartAngle;
		};
		Pen.fillColor_(col);
		Pen.addAnnularWedge(view.cen, view.innerRadius, view.radius, stAngle, view.levelSweepLength);
		Pen.fill;
		Pen.pop;
	}

	stroke {
		var stAngle, col, inset;

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
		Pen.strokeColor_(col);
		Pen.width_(p.strokeWidth);
		inset = p.strokeWidth*0.5;
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
			show: false,
			// majTicks: [],
			// minTicks: [],
			// majTickVals: [],
			// minTickVals: [],
			majorRatio: 0.25,
			minorRatio: 0.15,
			align: \outside,
			majorWidth: 1,
			minorWidth: 0.5,
			majorColor: Color.black,
			minorColor: Color.gray
		)
	}

	fill {}

	stroke {
		Pen.push;
		Pen.translate(view.cen.x, view.cen.y);
		Pen.rotate(view.prStartAngle);
		this.drawTicks(view.majTicks, p.majorRatio, p.majorWidth, p.majorColor);
		this.drawTicks(view.minTicks, p.minorRatio, p.minorWidth, p.minorColor);
		Pen.pop;
	}

	drawTicks {|ticks, tickRatio, strokeWidth, color|
		var penSt, penEnd;
		penSt = switch (p.align,
			\inside, {view.innerRadius},
			\outside, {view.radius},
			\center, {view.innerRadius + (view.wedgeWidth - (view.wedgeWidth * tickRatio) * 0.5)},
			{view.radius} // default to outside
		);

		penEnd = if (p.align == \outside) {
			penSt - (view.wedgeWidth * tickRatio)
		} { // \inside or \center
			penSt + (view.wedgeWidth * tickRatio)
		};

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
			show:	true,
			color:	Color.red,
			width:	2,
			radius:	3,
			align:	\outside,
			type:	\line, // line, circle, lineAndCircle, arrow
			arrowLengthRatio: 0.3,
			arrowWLRatio: 0.5,
			fillArrow: true,
			capStyle: \round,
			joinStyle: \round,
		)
	}

	fill {}

	stroke {
		var cen;
		cen = view.cen;
		Pen.push;
		Pen.translate(cen.x, cen.y);
		switch (p.type,
			\line, {this.drawLine},
			\circle, {this.drawCircle},
			\lineAndCircle, {Pen.push ; this.drawLine; Pen.pop; this.drawCircle},
			\arrow, {this.drawArrow}
		);
		Pen.pop;
	}

	drawLine {
		Pen.capStyle = this.getCapIndex(p.capStyle);
		Pen.width = p.width;
		Pen.strokeColor = p.color;
		Pen.moveTo(view.innerRadius@0);
		Pen.lineTo(view.radius@0);
		Pen.rotate(view.prStartAngle+(view.prSweepLength*view.input));
		Pen.stroke;
	}

	drawCircle {
		var d, rect;
		d = p.radius*2;
		rect = Size(d, d).asRect;
		Pen.fillColor = p.color;
		switch (p.align,
			\inside, {rect = rect.center_(view.innerRadius@0)},
			\outside, {rect = rect.center_(view.radius@0)},
			\center, {rect = rect.center_((view.wedgeWidth*0.5+view.innerRadius)@0)},
			// else assume align is a float 0>1
			{rect = rect.center_((view.wedgeWidth*p.align+view.innerRadius)@0)}
		);
		Pen.rotate(view.prStartAngle+(view.prSweepLength*view.input));
		Pen.fillOval(rect);
	}

	drawArrow {
		var rect, w, h;
		// Pen.push;
		h = view.wedgeWidth * p.arrowLengthRatio;
		w = h * p.arrowWLRatio;
		rect = Size(h, w).asRect.center_(0@0); // note h<>w, rect starts at 3 o'clock

		// define rect enclosing the arrow
		// align determines location of the arrow's tip
		rect = rect.right_(
			switch (p.align,
				\inside, {view.innerRadius},
				\outside, {view.radius},
				\center, {(view.wedgeWidth*0.5+view.innerRadius)},
				{view.wedgeWidth*p.align+view.innerRadius}
			)
		);


		Pen.rotate(view.prStartAngle+(view.prSweepLength*view.input));
		Pen.moveTo(rect.right@0);
		Pen.lineTo(rect.leftBottom);
		Pen.lineTo((rect.left+(h*0.1))@0);
		Pen.lineTo(rect.leftTop);
		Pen.lineTo(rect.right@0);

		if (p.fillArrow) {
			Pen.fillColor = p.color;
			Pen.fill;
		} {
			Pen.width = p.width;
			Pen.joinStyle = this.getJoinIndex(p.joinStyle);
			Pen.strokeColor = p.color;
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
		if (p.type !='line') {view.boarderPad=view.boarderPad};
		view.refresh;
	}

	align_ {|where|
		p.align = where;
		// update boarder pad
		if (p.type !='line') {view.boarderPad=view.boarderPad};
		view.refresh;
	}

	type_ {|type|
		p.type = type;
		// update boarder pad
		if (type !='line') {view.boarderPad=view.boarderPad};
		view.refresh;
	}
}