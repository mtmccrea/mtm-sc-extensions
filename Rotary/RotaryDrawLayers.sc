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
		col = p.levelFillColor;
		if (r.bipolar) {
			stAngle = r.prCenterAngle;
			if (r.input<r.centerNorm) {
				col = Color.hsv(*col.asHSV * [1,1,p.colorValBelow, 1]);
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
		col = p.levelStrokeColor;
		if (r.bipolar) {
			stAngle = r.prCenterAngle;
			if (r.input<r.centerNorm) {
				col = Color.hsv(*col.asHSV * [1,1,p.colorValBelow, 1]);
			};
		} {
			stAngle = r.prStartAngle;
		};
		Pen.strokeColor_(col);
		Pen.width_(p.levelStrokeWidth);
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
			showValue: true,
			valueAlign: \center, // \top, \bottom, \center, \left, \right, or Point()
			valueFontSize: 12,
			valueFont: Font("Helvetica", valueFontSize),
			valueFontColor: Color.black,
			round: 0.1,
		)
	}

	draw {
		|props|
	}
}

RotaryTickLayer {

	*properties {
		^(
			showTicks: false,
			majTicks: [],
			minTicks: [],
			majTickVals: [],
			minTickVals: [],
			majorTickRatio: 0.25,
			minorTickRatio: 0.15,
			tickAlign: \outside,
			majorTickWidth: 1,
			minorTickWidth: 0.5,
			tickColor: nil, // default to rangeStrokeColor
		)
	}

	*new { |rotary| ^super.newCopyArgs(rotary) }

	draw {
		|props|
	}
}

  RotaryHandleLayer {

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

	draw {
		|props|
	}
}