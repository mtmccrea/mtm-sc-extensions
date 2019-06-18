/* LegendView: a configurable legend to insert into a plot window.
The view offers some high-level parameters for adding labels,
configuring alignment, line and font sizes, etc.
The view scales automatically with resing of the parent window.
Once drawn, the legend is draggable by mouse.

Michael McCrea, 2019.

This is a quick port of the PolarLegendLayer, part of PolarPlot.
*/

LegendView {
	// capyArgs
	var parent, data, dataColors, align, <strokeTypes;

	var <p; // properties
	var <uv; // UserView
	var parWdth, parHght, parCen, parWHPnt; // parent dimension vars
	var nElem, txtRects, labels, font, legBnds;
	var updated = false;

	*new { |parent, data, dataColors, align, strokeTypes|
		^super.newCopyArgs(parent, data, dataColors, align, strokeTypes).init;
	}

	init {
		var prevMousePnt, dragging = false;
		// var uvtemp; // debug

		p = this.class.properties;
		this.updateParentDims;

		strokeTypes = strokeTypes ?? { (\lines).dup(data.size) };
		p.dataColors = dataColors ?? { Color.hsvSeries(data.size) };
		p.align = align;

		// the UserView overlays on the entire parent view, legend drawn in
		// a subspace of the Userview
		uv = UserView(parent, Rect(0,0,parWdth,parHght)).front;
		uv.drawFunc_{
			this.updateParentDims;
			this.fill;
			this.stroke;
		};
		uv.resize_(5);

		uv.mouseDownAction_{ |v, x, y|
			var downPnt = Point(x, y);
			var inside, testBnds;

			if (p.align.isKindOf(Point).not) {
				// if aligned by keyword, drawing origin has
				// been translated to center, so bounds are offset
				testBnds = legBnds.copy.origin_(legBnds.origin + parCen);
				inside = testBnds.contains(downPnt);
				if (inside) {
					p.align = legBnds.center + parCen / parWHPnt;
				};
			} {
				testBnds = legBnds.copy.center_(p.align * parWHPnt);
				inside = testBnds.contains(downPnt);
			};

			// debug: highlight legend click area on mouse down
			// uvtemp = UserView(parent.view, testBnds).background_(Color.red.alpha_(0.3)).front;

			if (inside) {
				prevMousePnt = downPnt;
				dragging = true
			}
		};

		uv.mouseUpAction_{ |v, x, y|
			dragging = false;
			// uvtemp.destroy; // debug
		};

		uv.mouseMoveAction_{ |v, x, y|
			var mousie, dxdy;
			if (dragging) {
				mousie = Point(x, y);
				dxdy = mousie - prevMousePnt / (parWdth@parHght);
				this.set(\align, p.align + dxdy);
				prevMousePnt = mousie;
			}
		};

		nElem = data.size;
	}

	updateParentDims {
		var b = parent.bounds;
		parWdth = b.width;
		parHght = b.height;
		parCen = [parWdth, parHght].asPoint * 0.5;
		parWHPnt = parWdth@parHght;
	}

	set { |property, value|
		if (p[property].notNil) {
			p[property] = value;
			uv.refresh;
		} {
			"property not found".warn;
		}
	}

	*properties {
		^(
			show:        false,
			fillColor:   Color.white,
			txtColor:    Color.gray,
			align:       \bottomRight, // right, left, top, bottom, topRight, topLeft, bottomRight, bottomLeft
			inset:       10,           // inset between legend and view's edge
			margin:      10,           // inset margin between entries and border of the legend
			spacing:     8,            // spacing between legend entries
			layout:      \vertical,    // horizontal, vertical
			lineLength:  15,           // length of sample plot line
			lineSpacing: 6,            // spacing between sample line and text
			fontName:    "Helvetica",
			fontSize:    0.028,
			labels:      [],
			showBorder:  true,
			borderColor: Color.gray,
			borderWidth: 1,
			strokeWidth: 2,
			pointRad:    2,
			fillPoints:  false,
			dataColors:  [],
		)
	}

	stroke {
		var lineCols, cursor, h_2, stx, stcursor;
		var pntRad, cRect;
		var cStep, numcSteps, cOff; // for strokeTupe == \points

		block { |break|
			legBnds ?? { break.() };  // bail if background hasn't been calculated

			// No need to push/translate because that happened in fill {}

			if (p.showBorder) {
				Pen.width_(p.borderWidth);
				Pen.strokeColor_(p.borderColor);
				Pen.addRect(legBnds);
				Pen.stroke;
			};

			cursor = legBnds.leftTop + (p.margin@p.margin);
			// translate to top left of legend background rect
			Pen.translate(cursor.x, cursor.y);
			cursor = 0@0; // reset cursor

			Pen.width = p.strokeWidth;
			lineCols = p.dataColors.asArray;
			pntRad = p.pointRad;
			cRect = [0,0,pntRad*2,pntRad*2].asRect;

			if (strokeTypes.any(_ == \points)) {
				cStep = cRect.width * 3;  // separation between pnts = pnt diam * 5
				numcSteps = (p.lineLength / cStep).asInt; // how many pnts on the line
				cOff = if (numcSteps > 0) {
					p.lineLength - (cStep * numcSteps) / 2;
				} {
					p.lineLength / 2
				}
			};

			Pen.push;
			nElem.do{ |i|
				var sColor;
				Pen.push;

				h_2 = txtRects[0].height/2;
				sColor = p.dataColors[i];

				if (strokeTypes.wrapAt(i).isKindOf(FloatArray)) {
					Pen.lineDash_(strokeTypes.wrapAt(i))
				};

				switch(p.layout,
					\horizontal, {
						stcursor = cursor.copy;
						cursor = stcursor;

						// w = margin-lineLength-lineSpacing-text length-spacing-lineLength-lineSpacing-text length-margin, etc
						if (strokeTypes.wrapAt(i) == \points) {
							cursor = cursor + (cOff@h_2);
							Pen.addOval(cRect.center_(cursor));
							numcSteps.do {
								cursor = cursor + (cStep@0);
								Pen.addOval(cRect.center_(cursor));
							};
							cursor = cursor + ((cOff+p.lineSpacing)@h_2.neg);
							if (p.fillPoints) {
								Pen.fillColor_(sColor);
								Pen.fill;
							} {
								Pen.strokeColor_(sColor);
								Pen.stroke;
							}
						} { // lines or dashes
							cursor = cursor + (0@h_2);
							Pen.moveTo(cursor);
							cursor = cursor + (p.lineLength@0);
							Pen.lineTo(cursor);
							cursor = cursor + (p.lineSpacing@h_2.neg);
							Pen.strokeColor_(sColor);
							Pen.stroke;
						};

						Pen.stringLeftJustIn(
							labels[i], txtRects[i].left_(cursor.x).top_(cursor.y),
							font, p.txtColor
						);
						cursor.x = cursor.x + txtRects[i].width + p.spacing;
					},
					\vertical, {
						stcursor = cursor.copy;
						cursor = stcursor;

						// h = margin-txtHeight-spacing-txtHeight-margin
						if (strokeTypes.wrapAt(i) == \points) {
							cursor = cursor + (cOff@h_2);
							Pen.addOval(cRect.center_(cursor));
							numcSteps.do {
								cursor = cursor + (cStep@0); // step
								Pen.addOval(cRect.center_(cursor));
							};
							cursor = cursor + ((cOff+p.lineSpacing)@h_2.neg);
							if (p.fillPoints) {
								Pen.fillColor_(sColor);
								Pen.fill;
							} {
								Pen.strokeColor_(sColor);
								Pen.stroke;
							}
						} { // lines or dashes
							cursor = cursor + (0@h_2);
							Pen.moveTo(cursor);
							cursor = cursor + (p.lineLength@0);
							Pen.lineTo(cursor);
							cursor = cursor + (p.lineSpacing@h_2.neg);
							Pen.strokeColor_(sColor);
							Pen.stroke;
						};

						Pen.stringLeftJustIn(
							labels[i], txtRects[i].left_(cursor.x).top_(cursor.y),
							font, p.txtColor
						);
						cursor.x = stcursor.x; // jump back to starting x
						cursor.y = cursor.y + txtRects[0].height + p.spacing;
					}
				);
				Pen.pop;
			};
			Pen.pop;
			Pen.pop; // last pop clears first push in fill {}
		};
	}

	fill {
		var sumW, sumH, spacing, margin, align;
		var minDim;
		minDim = min(parWdth, parHght);

		Pen.push;

		if (p.labels.isKindOf(String)) {
			p.labels = [p.labels]
		};

		labels = if (p.labels.size == 0) {
			nElem.collect{|i| format("Plot %", i+1) }
		} { // make sure there are labels for all plots
			p.labels.asArray.extend(nElem, " - ");
		};
		font = Font(
			p.fontName,
			if (p.fontSize < 1) { p.fontSize * minDim } { p.fontSize }
		);
		txtRects = labels.collect(_.bounds(font));
		spacing = p.spacing;
		margin = p.margin;
		align = p.align;

		switch(p.layout,
			\horizontal, {
				// w = margin-lineLength-lineSpacing-textlength-spacing-lineLength-lineSpacing-text length-spacing, etc, - margin
				sumW = margin + (p.lineLength + p.lineSpacing + spacing * nElem) + txtRects.collect(_.width).sum - spacing + margin;
				sumH = (margin * 2) + txtRects[0].height;
			},
			\vertical, {
				// h = spacing-txtHeight-spacing-txtHeight-spacing
				sumW = margin + p.lineLength + p.lineSpacing + txtRects.collect(_.width).maxItem + margin;
				sumH = margin + (txtRects[0].height + spacing * nElem) - spacing + margin;
			}
		);

		if (align.isKindOf(Point)) {
			Pen.translate(
				parWdth * align.x,
				parHght * align.y
			);
			legBnds = [0,0, sumW, sumH].asRect.center_(0@0);
		} {
			Pen.translate(parCen.x, parCen.y);
			legBnds = [0,0, sumW, sumH].asRect.center_(0@0);

			switch (align,
				\right,  { legBnds.right  = parWdth/2 - p.inset },
				\left,   { legBnds.left   = parWdth/2.neg + p.inset },
				\top,    { legBnds.top    = parHght/2.neg + p.inset },
				\bottom, { legBnds.bottom = parHght/2 - p.inset },
				\topRight, {
					legBnds.top   = parHght/2.neg + p.inset;
					legBnds.right = parWdth/2 - p.inset;
				},
				\topLeft, {
					legBnds.top  = parHght/2.neg + p.inset;
					legBnds.left = parWdth/2.neg + p.inset;
				},
				\bottomRight, {
					legBnds.bottom = parHght/2 - p.inset;
					legBnds.right  = parWdth/2 - p.inset;
				},
				\bottomLeft, {
					legBnds.bottom = parHght/2 - p.inset;
					legBnds.left   = parWdth/2.neg + p.inset;
				},
			);
		};

		Pen.fillColor_(p.fillColor);
		Pen.fillRect(legBnds);

		// don't pop yet, stroke will immediately follow and pop at the end
	}
}

/*
// Usage //

(
c = [Color.red, Color.cyan, Color.green, Color.blue];
d = 4.collect({ |i| 100.collect({|j| sinPi(1+i.squared*j * 50.reciprocal) })});
p = d.plot;
p.plotColors_(c).plotModes_([\steps, \linear, \plines, \points]).superpose_(true);
{ p.refresh }.defer(0.4);
)

l = LegendView(p.parent, d, c, \bottomRight)

l.set(\align, \topRight)
l.set(\inset, 45)
l.set(\fontSize, 0.05)
l.set(\labels, ["one", "two", "three", "four"])
l.set(\layout, \horizontal)

// other settable properties:
l.p // current properties
LegendView.properties // property template
*/