// TODO:
// optionally also draw the output levels in a strip below the
// compression ratio and breakpoint level display (numerical)
GatedCompanderView {
	// copy args
	var <gc; // the GatedCompander model

	// ui vars
	var <view, /*<levelMeter, */<userView;
	var compLevel, gateLevel, compColor, boostColor, gateColor;

	// compression vars
	var >gateThresh = 0.3, >compThresh=0.6;
	var >gateSlope = 3, >compSlopeBelow=0.9, >compSlopeAbove=0.6;
	var rmsDB = -120, peakDB = -120;
	var rmsNorm = 0, peakNorm = 0;
	var dbSpec;

	*new { |aGatedCompander|
		^super.newCopyArgs(aGatedCompander).init;
	}

	init {

		view = View().background_(Color.clear);

		dbSpec = ControlSpec(-90, 0, 'lin');

		gateThresh = gc.synth.gateThresh;
		compThresh = gc.synth.boostThresh;
		gateSlope = gc.synth.gateRatio;
		compSlopeBelow = gc.synth.boostRatio;
		compSlopeAbove = gc.synth.compRatio;

		compLevel = dbSpec.unmap(gc.synth.boostThresh.ampdb);
		gateLevel = dbSpec.unmap(gc.synth.gateThresh.ampdb);

		compColor = Color.hsv(0,1,0.9,1);
		boostColor = Color.hsv(0.15,1,0.9,1);
		gateColor = Color.hsv(0,1,0.5,1);

		// levelMeter = LevelIndicator(view, view.bounds)
		// .resize_(5)
		// .drawsPeak_(true)
		// .meterColor_(Color.hsv(0,1,0.5,1))
		// .warningColor_(Color.hsv(0.15,1,0.9,1))
		// .warning_(dbSpec.unmap(gc.synth.gateThresh.ampdb))
		// .critical_(dbSpec.unmap(gc.synth.boostThresh.ampdb));

		userView = UserView(view, view.bounds)
		.resize_(5)
		// .animate_(true)
		// .frameRate_(5)
		.drawFunc_({
			|view|
			var bnds, compPnt, gatePnt, compPntNorm, gatePntNorm;
			var upperIntersectY, gateIntersect, lowerInterSect;
			var lowerPnt, upperPnt, whPoint, gateX;
			var ratio1Pnts, levelCol, peakCol;

			bnds = view.bounds;
			whPoint = bnds.size.asPoint;

			// show level in graph
			// rms
			Pen.fillColor_(this.getLevelCol(rmsNorm));
			Pen.fillRect(Size(rmsNorm * bnds.width, bnds.height).asRect);
			// peak
			Pen.fillColor_(this.getLevelCol(peakNorm));
			Pen.fillRect(Size(4, bnds.height).asRect.left_(peakNorm * bnds.width)-2);

			// 1:1 ratio line
			// left, gate, comp, right points
			ratio1Pnts = this.getPoints(gateThresh, compThresh, 1, 1, 1).collect(_ * whPoint);

			Pen.width_(0.5);
			Pen.strokeColor_(Color.gray);

			Pen.moveTo(ratio1Pnts[0]);
			ratio1Pnts[1..].do{|pnt| Pen.lineTo(pnt) };
			Pen.stroke;

			// compression line
			#lowerPnt, gatePnt, compPnt, upperPnt = this.getPoints(gateThresh, compThresh, gateSlope, compSlopeBelow, compSlopeAbove).collect(_ * whPoint);

			// draw mask over level rect
			Pen.fillColor_(Color.gray.alpha_(0.9));
			Pen.moveTo(0@0);
			Pen.lineTo(0@bnds.height);
			Pen.lineTo(lowerPnt);
			Pen.lineTo(gatePnt);
			Pen.lineTo(compPnt);
			Pen.lineTo(upperPnt);
			Pen.lineTo(bnds.width@0);
			Pen.lineTo(0@0);
			Pen.fill;

			// draw compression line
			Pen.width_(2);
			Pen.strokeColor_(Color.black.alpha_(0.3));

			Pen.moveTo(lowerPnt);
			[gatePnt, compPnt, upperPnt].do{|pnt|
				Pen.lineTo(pnt)
			};
			Pen.stroke;

			// encircle breakpoints
			Pen.strokeOval(Size(15,15).asRect.center_(compPnt));
			Pen.strokeOval(Size(15,15).asRect.center_(gatePnt));
		});

		// // maintain square aspect ratio on user view
		// // and make sure levelIndicator is always wider
		// // than it is tall so it stays horiz
		// view.onResize_({ |v|
		// 	var w, h, newBnds;
		// 	w = v.bounds.width;
		// 	h = v.bounds.height;
		// 	[v, w, h].postln;
		// 	if (h>w) {
		// 		w.postln;
		// 		Size(w, w-1).postln;
		// 		newBnds = Size(w, w-1).asRect; //.bottom_(h); // anchor to bottom
		// 		888.postln;
		// 		userView.bounds_(newBnds);
		// 		// levelMeter.bounds_(newBnds);
		// 	};
		// })
		view.onResize_({ |v| userView.bounds_(v.bounds.origin_(0@0)) });
	}

	// get color for the rms and peak rects
	// level is 0>1, scaled by dbSpec
	getLevelCol { |level|
		^if (level < gateLevel) {
			gateColor
		} {
			if (level >= compLevel) {compColor} {boostColor}
		};
	}

	// rms_ { |rmsAmp| }
	// peak_ { |peakAmp| }

	rmsPeak_{ |rmsAmp, peakAmp|
		var rmsDB, peakDB;
		#rmsDB, peakDB = [rmsAmp, peakAmp].ampdb;
		#rmsNorm, peakNorm = dbSpec.unmap([rmsDB, peakDB]);
		defer {userView.refresh};
		// defer {levelMeter.value_(rmsNorm).peakLevel_(peakNorm)};
	}

	meterMin_ {|minDB|
		dbSpec.minval = minDB;
		#rmsNorm, peakNorm = dbSpec.unmap([rmsDB, peakDB]);
		// levelMeter.value_(rmsNorm).peakLevel_(rmsNorm);
		userView.refresh;
	}

	meterMax_ {|maxDB|
		dbSpec.maxval = maxDB;
		#rmsNorm, peakNorm = dbSpec.unmap([rmsDB, peakDB]);
		// levelMeter.value_(rmsNorm).peakLevel_(rmsNorm);
		userView.refresh;
	}

	unmapAmpNoClip { |amp|
		^amp.ampdb.linlin(dbSpec.minval, dbSpec.maxval, 0, 1, nil)
	}

	getPoints {
		arg gateThresh = -40.dbamp, compThresh= -25.dbamp,
		gateSlope = 3, compSlopeBelow=0.9, compSlopeAbove=0.6;

		var lowerPnt, gatePnt, compPnt, upperPnt;
		var upperIntersectY, gateIntersect, lowerInterSect, gateX;
		var compThreshInRange, gateThreshInRange;
		/* compression lines */

		compThreshInRange = this.unmapAmpNoClip(compThresh);
		gateThreshInRange = this.unmapAmpNoClip(gateThresh);

		compPnt = Point(
			// ampToDB.(compThresh),
			// (1-ampToDB.(compThresh))
			compThreshInRange,
			1- compThreshInRange
		);

		// upper section
		upperIntersectY = compPnt.x + ((1-compPnt.x) * compSlopeAbove);
		upperPnt = Point(1,(1-upperIntersectY));

		// middle section
		// gateX = ampToDB.(gateThresh);
		gateX = gateThreshInRange;
		gateIntersect = compPnt.x -((compPnt.x - gateX) * compSlopeBelow);
		gatePnt = Point(gateX, 1-gateIntersect);

		// lower section
		lowerInterSect = gateIntersect - (gateX * gateSlope);
		lowerPnt = Point(0, 1-lowerInterSect);

		^[lowerPnt, gatePnt, compPnt, upperPnt];
	}


	free {
		"freeing UI".postln;
		gc.removeDependant(this);
	}

	update {
		|who, what ...args|

		if (who==gc) {
			// "heard".postln;
			switch (what,
				\inbus, {},
				\outbus, {},
				\boostThresh, {
					compThresh = args[0];
					// levelMeter.critical_(dbSpec.unmap(args[0].ampdb));
					compLevel = dbSpec.unmap(gc.synth.boostThresh.ampdb);
				},
				\boostRatio, {
					 compSlopeBelow = args[0];
				},
				\compRatio, {
					 compSlopeAbove = args[0];
				},
				\gateThresh, {
					gateThresh = args[0];
					gateLevel = dbSpec.unmap(gc.synth.gateThresh.ampdb);
					// levelMeter.warning_(dbSpec.unmap(args[0].ampdb));
				},
				\gateRatio, {
					gateSlope = args[0];
				},
				\rmsPeak, {
					this.rmsPeak_(*args[0..1])
				}
			);
			defer {userView.refresh};
		}
	}
}