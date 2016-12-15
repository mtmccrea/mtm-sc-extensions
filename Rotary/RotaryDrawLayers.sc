RotaryLayer {
	var r, <>p; // properties

	*new { |rotary, initProperties| ^super.newCopyArgs(rotary, initProperties) }
}

RotaryRangeLayer : RotaryLayer {
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

	fill {
		Pen.fillColor_(p.fillColor);
		Pen.addAnnularWedge(r.cen, r.innerRadius, r.radius, r.prStartAngle, r.prSweepLength);
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
				Pen.addAnnularWedge(r.cen, r.innerRadius, r.radius-inset, r.prStartAngle, r.prSweepLength);
			},
			\inside, {
				Pen.addArc(r.cen, r.innerRadius+inset, r.prStartAngle, r.prSweepLength);
			},
			\outside, {
				Pen.addArc(r.cen, r.radius-inset, r.prStartAngle, r.prSweepLength);
			},
			\insideOutside, {
				Pen.addArc(r.cen, r.innerRadius+inset, r.prStartAngle, r.prSweepLength);
				Pen.addArc(r.cen, r.radius-inset, r.prStartAngle, r.prSweepLength);
			},
		);
		Pen.stroke;
		Pen.pop;
	}
}

RotaryLevelLayer : RotaryLayer {

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
		if (r.bipolar) {
			stAngle = r.prCenterAngle;
			if (r.input<r.centerNorm) {
				col = Color.hsv(*col.asHSV * [1,1,r.colorValBelow, 1]);
			};
		} {
			stAngle = r.prStartAngle;
		};
		Pen.fillColor_(col);
		Pen.addAnnularWedge(r.cen, r.innerRadius, r.radius, stAngle, r.levelSweepLength);
		Pen.fill;
		Pen.pop;
	}

	stroke {
		var stAngle, col, inset;

		Pen.push;
		col = p.strokeColor;
		if (r.bipolar) {
			stAngle = r.prCenterAngle;
			if (r.input<r.centerNorm) {
				col = Color.hsv(*col.asHSV * [1,1,p.colorValBelow, 1]);
			};
		} {
			stAngle = r.prStartAngle;
		};
		Pen.strokeColor_(col);
		Pen.width_(p.strokeWidth);
		inset = p.strokeWidth*0.5;
		switch (p.strokeType,
			\around, {
				Pen.addAnnularWedge(r.cen, r.innerRadius, r.radius-inset, stAngle, r.levelSweepLength);
			},
			\inside, {
				Pen.addArc(r.cen, r.innerRadius+inset, stAngle, r.levelSweepLength);
			},
			\outside, {
				Pen.addArc(r.cen, r.radius-inset, stAngle, r.levelSweepLength);
			},
			\insideOutside, {
				Pen.addArc(r.cen, r.innerRadius+inset, stAngle, r.levelSweepLength);
				Pen.addArc(r.cen, r.radius-inset, stAngle, r.levelSweepLength);
			},
		);
		Pen.stroke;
		Pen.pop;
	}
}

RotaryTextLayer : RotaryLayer {

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
		v = r.value.round(p.round).asString;
		bnds = r.bnds;
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

RotaryTickLayer : RotaryLayer {

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
		Pen.translate(r.cen.x, r.cen.y);
		Pen.rotate(r.prStartAngle);
		this.drawTicks(r.majTicks, p.majorTickRatio, p.majorTickWidth);
		this.drawTicks(r.minTicks, p.minorTickRatio, p.minorTickWidth);
		Pen.pop;
	}

	drawTicks {|ticks, tickRatio, strokeWidth|
		var penSt, penEnd;
		penSt = switch (p.align,
			\inside, {r.innerRadius},
			\outside, {r.radius},
			\center, {r.innerRadius + (r.wedgeWidth - (r.wedgeWidth * tickRatio) * 0.5)},
			{r.radius} // default to outside
		);

		penEnd = if (p.align == \outside) {
			penSt - (r.wedgeWidth * tickRatio)
		} { // \inside or \center
			penSt + (r.wedgeWidth * tickRatio)
		};

		Pen.push;
		Pen.strokeColor_(p.tickColor);
		ticks.do{|tickPos|
			Pen.width_(strokeWidth);
			Pen.moveTo(penSt@0);
			Pen.push;
			Pen.lineTo(penEnd@0);
			Pen.rotate(tickPos * r.dirFlag);
			Pen.stroke;
			Pen.pop;
		};
		Pen.pop;
	}
}

RotaryHandleLayer : RotaryLayer {

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
		cen = r.cen;
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
		Pen.moveTo(r.innerRadius@0);
		Pen.lineTo(r.radius@0);
		Pen.rotate(r.prStartAngle+(r.prSweepLength*r.input));
		Pen.stroke;
	}

	drawCircle {
		var d, rect;
		d = p.radius*2;
		rect = Size(d, d).asRect;
		Pen.fillColor_(p.color);
		switch (p.align,
			\inside, {rect = rect.center_(r.innerRadius@0)},
			\outside, {rect = rect.center_(r.radius@0)},
			\center, {rect = rect.center_((r.wedgeWidth*0.5+r.innerRadius)@0)},
		);
		Pen.rotate(r.prStartAngle+(r.prSweepLength*r.input));
		Pen.fillOval(rect);
	}
}