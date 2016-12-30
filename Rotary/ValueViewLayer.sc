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
		if (selector.isSetter && p[asGetter].notNil) {
			p[asGetter] = value;
			this.changed(\layerProperty, asGetter, value);
		}
	}

	properties {^p}
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
		)
	}

	fill {
		var stAngle, col, inset;

		Pen.push;
		col = p.fillColor;
		if (view.bipolar) {
			stAngle = view.prCenterAngle;
			if (view.input<view.centerNorm) {
				col = Color.hsv(*col.asHSV * [1,1,view.colorValBelow, 1]);
			};
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
				col = Color.hsv(*col.asHSV * [1,1,p.colorValBelow, 1]);
			};
		} {
			stAngle = view.prStartAngle;
		};
		Pen.strokeColor_(col);
		Pen.width_(p.strokeWidth);
		inset = p.strokeWidth*0.5;
		switch (p.strokeType,
			\around, {
				Pen.addAnnularWedge(view.cen, view.innerRadius, view.radius-inset, stAngle, view.levelSweepLength);
			},
			\inside, {
				Pen.addArc(view.cen, view.innerRadius+inset, stAngle, view.levelSweepLength);
			},
			\outside, {
				Pen.addArc(view.cen, view.radius-inset, stAngle, view.levelSweepLength);
			},
			\insideOutside, {
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
			fontColor: Color.black,
			round: 0.1,
		)
	}

	fill {
		var v, bnds, rect, half;
		v = view.value.round(p.round).asString;
		bnds = view.bnds;
		Pen.push;
		Pen.fillColor_(p.fontColor);
		if (p.align.isKindOf(Point)) {
			rect = bnds.center_(bnds.extent*p.align);
			Pen.stringCenteredIn(v, rect, p.font, p.fontColor);
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
			Pen.stringCenteredIn(v, rect, p.font, p.fontColor)
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
			majorTickRatio: 0.25,
			minorTickRatio: 0.15,
			align: \outside,
			majorTickWidth: 1,
			minorTickWidth: 0.5,
			tickColor: Color.gray;
		)
	}

	fill {}

	stroke {
		Pen.push;
		Pen.translate(view.cen.x, view.cen.y);
		Pen.rotate(view.prStartAngle);
		this.drawTicks(view.majTicks, p.majorTickRatio, p.majorTickWidth);
		this.drawTicks(view.minTicks, p.minorTickRatio, p.minorTickWidth);
		Pen.pop;
	}

	drawTicks {|ticks, tickRatio, strokeWidth|
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
		Pen.strokeColor_(p.tickColor);
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
			type:	\line,
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
			\lineAndCircle, {Pen.push ; this.drawLine; Pen.pop; this.drawOval}
		);
		Pen.pop;
	}

	drawLine {
		Pen.width_(p.width);
		Pen.strokeColor_(p.color);
		Pen.moveTo(view.innerRadius@0);
		Pen.lineTo(view.radius@0);
		Pen.rotate(view.prStartAngle+(view.prSweepLength*view.input));
		Pen.stroke;
	}

	drawCircle {
		var d, rect;
		d = p.radius*2;
		rect = Size(d, d).asRect;
		Pen.fillColor_(p.color);
		switch (p.align,
			\inside, {rect = rect.center_(view.innerRadius@0)},
			\outside, {rect = rect.center_(view.radius@0)},
			\center, {rect = rect.center_((view.wedgeWidth*0.5+view.innerRadius)@0)},
		);
		Pen.rotate(view.prStartAngle+(view.prSweepLength*view.input));
		Pen.fillOval(rect);
	}
}