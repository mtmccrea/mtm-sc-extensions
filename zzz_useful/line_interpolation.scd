/*
Various types of interpolation for drawing curves
with numerous control points

"cubic with controls", (bottom example has both x and y interpolation)
"bspline",
"spline",
"hermite",
"linear",
"sine",
"step"

* wslib required *
*/

/* TODO
-- plot the resampled points on the bezier curve so you can see the actual
output of the sampled points
-- handled the padded boarder with a contained user view as opposed to manual padding
*/

(
var yPxMin, yPxMax, xPxMin, xPxMax, pad;
var yNormFunc, xNormFunc, calcSamples, samples, numSamplePoints, resampLinearX, scaleY;
w = Window( "2D interpolation: cubic with controls" ).alwaysOnTop_(true).front;
pad = 30;
yPxMin = pad;
yPxMax = w.bounds.height-pad;
xPxMin = pad;
xPxMax = w.bounds.width-pad;
numSamplePoints = 50;
scaleY = 5;

u = UserView( w, w.view.bounds ).resize_(5);
w.decorate;

// points of enndpoint anchors [0,3] and control points [1,2]
p = [ xPxMin@yPxMax, (1*(xPxMax-xPxMin)/3)@yPxMax, xPxMax@(yPxMax-yPxMin/2), xPxMax@yPxMin ];
j = nil;
r = 7;

yNormFunc = {|yPxVal| (yPxMax - (yPxVal - yPxMin)) / (yPxMax-yPxMin) };
xNormFunc = {|xPxVal| (xPxVal-xPxMin) / (xPxMax-xPxMin) };


calcSamples = {
	var sample, result = [];

	p[[0,3]].collect( _.asArray ) 	// get endpoints, as arrays [[x,y],[x,y]]
	.flop  													// group x's and y's of endpoints
	.collect({ |item,i|							// collect samples of interpolated x's and y's
		item.splineIntFunction(
			(0,(1/numSamplePoints)..1), // normalized sample points 0-1
			p[1].asArray[i],
			p[2].asArray[i]
		)
	}).flop // re-interleave x,y pairs, there are numSamplePoints of them
	.do({ |xyPair|
		sample = [xNormFunc.(xyPair[0]), yNormFunc.(xyPair[1])];
		result = result.add(sample);
	});
	result * scaleY;
};

resampLinearX = {
	var newPnts, xNorm, yNorm, resampEnv, result;
	newPnts = p[[0,3]].collect( _.asArray ).flop.collect({ |item,i|
				item.splineIntFunction( (0,1/numSamplePoints..1), p[1].asArray[i], p[2].asArray[i] )
			}).flop;
	// resample the curve with linear x distribution (as the transducers are linearly arrayed)
	xNorm = (newPnts.flop[0]-pad).collect({|xVal| xNormFunc.(xVal) }); // x's
	yNorm = (newPnts.flop[1]+pad).collect({|yVal| yNormFunc.(yVal) }); // y's
	// ("differentiated: " ++ xNorm.differentiate.drop(1).normalizeSum.round(0.001) ).postln;
	resampEnv = Env( yNorm, xNorm.differentiate.drop(1).normalizeSum, \lin); //.plot(name: 'resampled ENV');
	result = numSamplePoints.collect({|i|
		resampEnv.at(i/(numSamplePoints-1))
	});
	result * scaleY;
};

samples = calcSamples.();

u.drawFunc_({ |uvw|
	var newPnts;
	Color.black.alpha_(0.5).set;
	p[[0,3]].do({ |point, i| Pen.addArc( point, r, 0, 2pi ).stroke; });

	// control handles
	Color.red.set;
	Pen.line( p[0], p[1] ).stroke;
	Pen.line( p[2], p[3] ).stroke;
	p[[1,2]].do({ |point, i| Pen.addArc( point, r, 0, 2pi ).stroke; });

	Color.blue.set;
	Pen.moveTo( p[0] );

	newPnts = p[[0,3]].collect( _.asArray )
	.flop // group x endpoints and y endpoints for interpolation separately
	.collect({ |item,i|
		// i = 0 for x's, i = 1 for y's
		item.splineIntFunction( (0,1/numSamplePoints..1), p[1].asArray[i], p[2].asArray[i] )
	})
	.flop; // re-interleave points back into [[x,y],[x,y],...]

	newPnts.do({ |item| Pen.lineTo( item.asPoint ) });
	Pen.stroke;

	// redraw the xy points as grid
	Color.gray.set;
	newPnts.do({ |item|
		var pnt;
		pnt = item.asPoint;
		//vertical
		Pen.moveTo( pnt.x@yPxMax );
		Pen.lineTo( pnt.x@pad );
		//horizontal
		Pen.moveTo( pad@pnt.y);
		Pen.lineTo( xPxMax@pnt.y );
	});
	Pen.stroke;

});

u.mouseDownAction_({ |uvw, x, y|
	var distances, endpnts, sample;
	distances = p.collect({ |pt| pt.dist( x@y ) });
	j = distances.detectIndex( _ < (r+1) );

	// click anywhere but breakpoints...plot the result
	j ?? {
		// normalized endpoints - Point format
		endpnts = p[[0,3]].collect({|pnt| xNormFunc.(pnt.x)@yNormFunc.(pnt.y) });
		// "Endpoints: ".post; endpnts.postln;
		samples = calcSamples.();
		// samples.do{|xyPair| xyPair.round(0.001).postln};
		// samples.flop[1].plot("sampled values", minval: 0, maxval: 1); // plot y's
		resampLinearX.value().plot("REsampled values"); //, minval: 0, maxval: 1);
	};
});

u.mouseMoveAction_({ |uvw, x,y|
	if( j.notNil ) {
		p[j] = x.clip(xPxMin, xPxMax)@y.clip(yPxMin, yPxMax);
		// p[j] = x@y; // uncomment to not limit the control points to gridded region
		w.refresh;
	};
});
)



/* from Wouter Snoei Reply | Threaded | More
Oct 24, 2011; 2:46am Re: bezier transfer curves */

// interpolation between points [0,1] for values (0,0.01..1), controls: (c1 and c2) 0.7 and 0.3
// you can tweak the control points to match your desired result


// language side:
[0,1].splineIntFunction( (0,0.01..1), 0.7, 0.3).plot;


// server side (ar in this case):
(
{
        [0,1].splineIntFunction( Line.ar(0,1,0.01), 0.7, 0.3 );
}.plot;
)

// more optimized server side:
(
{
        var spli;
        spli = [-1,2].splineIntPart1( 0.7, 0.3 ); // calculate this only once
        spli.splineIntPart2( Line.ar(0,1,0.01) );  // calculate this for every sample
}.plot;
)



[0,1,5,2].intAt( 2.5 );
[0,1,5,2].intAt( 2.5, \hermite );
[0,1,5,2].intAt( 2.5, \bspline );

[0,1,5,2].interpolate( 10, \hermite ).size;
[0,1,5,2].interpolate( 10, \hermite ).plot;

[0,1].splineIntFunction( (0,0.01..1), 0.125, 1.75 ).plot;


(
var track = false;
// hands on interpolation
// move the points around..
w = Window( "y-axis interpolation" ).front;
u = UserView( w, w.view.bounds ).resize_(5);
w.decorate;
w.view.background_( Color.white );
m = PopUpMenu( w, 160@20 ).items_([
		"cubic with controls",
		"bspline",
		"spline",
		"hermite",
		"linear",
		"sine",
		"step" ])
	.action_({ |pu|
		if( (pu.value == 2) or: (pu.value == 1) )
			{ c.visible = true }
			{ c.visible = false };
		w.refresh; });

c = Slider( w, 200@20 )
	.value_( (0.75 / (1.9**0.5)) )
	.action_({ w.refresh })
	.visible_( false );

p = [ 12@200, 138@50, 262@50, 388@200 ];
j = nil;
r = 7;

u.drawFunc_({ |uvw|
	Color.black.alpha_(0.5).set;

	Pen.line( 0@200, 400@200 ).stroke;

	p[[0,3]].do({ |point, i| Pen.addArc( point, r, 0, 2pi ).stroke; });
	if( m.value == 0 )
		{ Color.red.set;
			Pen.line( p[0], p[1] ).stroke;
			Pen.line( p[2], p[3] ).stroke;
		};

	p[[1,2]].do({ |point, i| Pen.addArc( point, r, 0, 2pi ).stroke; });

	Color.blue.set;
	Pen.moveTo( p[0] );
	switch( m.value,
		0, { p[[0,3]].collect( _.y )
				.splineIntFunction( (0,1/50..1), p[1].y, p[2].y )
				.do({ |item, i|
					 Pen.lineTo(
					i.linlin( 0, 50, p[0].x, p[3].x, \none )@item )
				});
			},
		1, { p.collect(_.y).interpolate(15, 'bspline', false, c.value.linlin( 0,1,2,8 ).asInt )
				.do({ |item, i| Pen.lineTo( i.linlin( 0, 59, p[0].x, p[3].x, \none )@item ) });
			},
		2, { p.collect(_.y).interpolate(15, 'spline', false, c.value )
				.do({ |item, i| Pen.lineTo( i.linlin( 0, 59, p[0].x, p[3].x, \none )@item ) });
			},

		3, { p.collect(_.y).interpolate(15, 'hermite', false)
				.do({ |item, i| Pen.lineTo( i.linlin( 0, 59, p[0].x, p[3].x, \none )@item ) });
			},
		4, { p.collect(_.y).interpolate(10, 'linear', false)
				.do({ |item, i| Pen.lineTo( i.linlin( 0, 39, p[0].x, p[3].x, \none )@item ) });
		 	},
		5, { p.collect(_.y).interpolate(15, 'sine', false)
				.do({ |item, i| Pen.lineTo( i.linlin( 0, 59, p[0].x, p[3].x, \none )@item ) });
		 	},
		6, { p.collect(_.y).interpolate(15, 'step', false)
				.do({ |item, i| Pen.lineTo( i.linlin( 0, 59, p[0].x, p[3].x, \none )@item ) });
		 	}

 		);

	Pen.stroke;

	});

u.mouseDownAction_({ |uvw, x,y|
	var distances;
	distances = p.collect({ |pt| pt.dist( x@y ) });
	j = distances.detectIndex( _ < (r+1) );
	// j.postln;
});


// u.mouseMoveAction_({ |uvw, x,y| if( j.notNil ) { p[j] = (p[j].x)@(y.max(0)); w.refresh; }; });
u.mouseMoveAction_({ |uvw, x,y| if( j.notNil ) { p[j] = x@(y.max(0)); w.refresh; }; });

)


// as above, but modified to enable numerous control points
// also added 2D (x and y) interpolation to the "cubic with controls mode"
// Note this messes up the "cubic with controls" option

(
var
resamps = 7,			// number of downsampled points
nCtls = 5, 				// number of control points
track = false,
nInterps = 15,		// number of output curve values will be nInterps*nCtls
pxStep,
p_orig,
prevInterp = 0;

// hands on interpolation
// move the points around..
w = Window( "y-axis interpolation" ).front;
u = UserView( w, w.view.bounds ).resize_(5);
w.decorate;
w.view.background_( Color.white );

// p = [ 12@200, 138@50, 262@50, 388@200 ];
pxStep = 400/(nCtls-1);
p = nCtls.collect{|i| (i*pxStep)@200 };
p_orig = p.copy;
// p = [ 12@200, 50@50, 138@50, 262@50, 388@200 ];
j = nil;
r = 7;

m = PopUpMenu( w, 160@20 ).items_([
		"cubic w/ ctls - 2D interp",
		"bspline",
		"spline",
		"hermite",
		"linear",
		"sine",
		"step" ])
.action_({ |pu|
	if ( (pu.value == 2) or: (pu.value == 1) )
	{ c.visible = true }
	{ c.visible = false };
	if ((pu.value > 0) and: (prevInterp == 0)) {
		"true".postln;
		(p.size-2).do{|i| p[i+1] = p_orig[i+1].x@p[i+1].y}; // restore original control points
		postf("prevInterp: %\n", prevInterp);
	};
	prevInterp = pu.value;
	w.refresh;
});

c = Slider( w, 200@20 )
	.value_( (0.75 / (1.9**0.5)) )
	.action_({ w.refresh })
	.visible_( false );


u.drawFunc_({ |uvw|
	var interpd, ienv, rsStep;

	Color.black.alpha_(0.5).set;

	Pen.line( 0@200, 400@200 ).stroke;

	// draw endpoints
	[p.first, p.last].do({ |point, i| Pen.addArc( point, r, 0, 2pi ).stroke; });
	if( m.value == 0 )
	{
		// cubic control handles
		Color.red.set;
		Pen.line( p[0], p[1] ).stroke;
		Pen.line( p[p.size-2], p.last ).stroke;
		p[[1,p.size-2]].do({ |point, i| Pen.addArc( point, r, 0, 2pi ).stroke; });
	} {
			p[1..p.size-2].do({ |point, i| Pen.addArc( point, r, 0, 2pi ).stroke; });
	};

	Color.blue.set;
	Pen.moveTo( p[0] );
	interpd = switch( m.value,
		0, {
			// [p.first, p.last].collect( _.y ).splineIntFunction( (0,1/(p.size*nInterps-1)..1), p[1].y, p[p.size-2].y )
			[p.first, p.last].collect( _.asArray ).flop.collect({ |item,i|
				item.splineIntFunction( (0,1/(p.size*nInterps-1)..1), p[1].asArray[i], p[p.size-2].asArray[i] )
			}).flop;
		},
		1, { p.collect(_.y).interpolate(nInterps, 'bspline', false, c.value.linlin( 0,1,2,8 ).asInt ) },
		2, { p.collect(_.y).interpolate(nInterps, 'spline', false, c.value ) },
		3, { p.collect(_.y).interpolate(nInterps, 'hermite', false) },
		4, { p.collect(_.y).interpolate(nInterps, 'linear', false) },
		5, { p.collect(_.y).interpolate(nInterps, 'sine', false) },
		6, { p.collect(_.y).interpolate(nInterps, 'step', false) }
	);

	case
	{m.value==0} { 	// draw curve (for cubic with controls)
		interpd.do({ |xy, i| Pen.lineTo( xy.asPoint ) });
		ienv = Env(
			interpd.flop[1], // y's
			interpd.flop[0].differentiate.drop(1).normalizeSum // x's differentiated and normalized sum
		);
	}
	{ // otherwise draw curve (for all but cubic with controls)
		interpd.do({ |item, i|
			Pen.lineTo( i.linlin( 0, p.size*nInterps-1, p.first.x, p.last.x, \none )@item );
		});
		ienv = Env(interpd, (interpd.size-1).reciprocal);
	};

	Pen.stroke;

	Pen.moveTo( p[0] );
	Color.red.set;
	// draw "resample"
	rsStep = (resamps-1).reciprocal;
	resamps.do{|i|
		var y;
		y = ienv.at(rsStep*i);
		Pen.lineTo( i.linlin( 0, (resamps-1), p.first.x, p.last.x, \none )@y )
	};
	Pen.stroke;
});


u.mouseDownAction_({ |uvw, x,y|
	var distances;
	distances = p.collect({ |pt| pt.dist( x@y ) });
	j = distances.detectIndex( _ < (r+1) );
});


u.mouseMoveAction_({ |uvw, x,y|
	if( j.notNil ) {
		case
		{m.value==0} { 	// for cubic with free controls
			p[j] = x@(y.max(0)); w.refresh;
		}
		{ // others have restricted x movement
			p[j] = p[j].x@(y.max(0)); w.refresh;
		};
	};
});

)