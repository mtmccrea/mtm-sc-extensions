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
		var swLen, stAngle, col, inset;

		swLen = if (r.bipolar) {
			r.prSweepLength * (r.levelFollowsValue.if({r.input},{r.levelInput}) - r.centerNorm);
		} {
			r.prSweepLength * r.levelFollowsValue.if({r.input},{r.levelInput});
		};

		stAngle = if (r.bipolar, {r.prCenterAngle}, {r.prStartAngle});

		Pen.push;
		switch (fillOrStroke,
			\fill, {
				col = p.levelFillColor;
				if (r.bipolar and: (r.input<r.centerNorm)) {
					col = Color.hsv(*col.asHSV * [1,1,p.colorValBelow, 1]);
				};
				Pen.fillColor_(col);
				Pen.addAnnularWedge(r.cen, r.innerRadius, r.radius, stAngle, swLen);
				Pen.fill;
			},
			\stroke, {
				col = p.levelStrokeColor;
				if (r.bipolar and: (r.input<r.centerNorm)) {
					col = Color.hsv(*col.asHSV * [1,1,p.colorValBelow, 1]);
				};
				Pen.strokeColor_(col);
				Pen.width_(p.levelStrokeWidth);
				inset = p.strokeWidth*0.5;
				switch (p.strokeType,
					\around, {
						Pen.addAnnularWedge(r.cen, r.innerRadius, r.radius-inset, stAngle, swLen);
					},
					\inside, {
						Pen.addArc(r.cen, r.innerRadius+inset, stAngle, swLen);
					},
					\outside, {
						Pen.addArc(r.cen, r.radius-inset, stAngle, swLen);
					},
					\insideOutside, {
						Pen.addArc(r.cen, r.innerRadius+inset, stAngle, swLen);
						Pen.addArc(r.cen, r.radius-inset, stAngle, swLen);
					},
				);
				Pen.stroke;
			}
		);
		Pen.pop;
	}

	stroke {
		var swLen, col;

			swLen = if (bipolar) {
				prSweepLength * (levelFollowsValue.if({input},{levelInput}) - centerNorm);
			} {
				prSweepLength * levelFollowsValue.if({input},{levelInput});
			};
			Pen.push;
			switch (fillOrStroke,
				\fill, {
					col = levelFillColor;
					if (bipolar and: (input<centerNorm)) {
						col = Color.hsv(*col.asHSV * [1,1,colorValBelow, 1]);
					};
					Pen.fillColor_(col);
					Pen.addAnnularWedge(cen, innerRadius, radius, if (bipolar, {prCenterAngle}, {prStartAngle}), swLen);
					Pen.fill;
				},
				\stroke, {
					col = levelStrokeColor;
					if (bipolar and: (input<centerNorm)) {
						col = Color.hsv(*col.asHSV * [1,1,colorValBelow, 1]);
					};
					Pen.strokeColor_(col);
					Pen.width_(levelStrokeWidth);
					drWedgeStroke.(levelStroke, levelStrokeWidth, if (bipolar, {prCenterAngle}, {prStartAngle}), swLen);
					Pen.stroke;
				}
			);
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
			showHandle: true,
			handleColor: Color.red,
			handleWidth: 2,
			handleRadius: 3,
			handleAlign: \outside,
			handleType: \line,
		)
	}

	draw {
		|props|
	}
}