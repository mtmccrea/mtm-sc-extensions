MBCompressorView {
	// copyArgs
	var model, server, windowLabel;
	var win, masterView, compView,
	bv1, bv2, bv3, bv4, bv5,
	mv1, mv2, mv3, mv4, mv5,
	tv1, tv2, tv3, tv4, tv5,
	cv1, cv2, cv3, cv4, cv5,
	gv1, gv2, gv3, gv4, gv5,
	s1, s2, s3, s4, s5,
	gainSpec, threshSpec, compSpec, ampSpec,
	threshSliders, compSliders, gainSliders,
	bandElemSpacing, meterWidth,
	meters, peaklabels, rmslabels, <compresp,
	isSoloed, isSounding,
	xView, xSpec, xSpacing, xSliders,
	bDict, psetPopUp, tf, psetView, ampSlider,

	agWin, gainView, v1,v2,v3,v4,v5,
	l1, l2, l3, l4,l5, t1, t2, t3, t4,t5,
	floor, ceil, targ, gainrolloff,
	cnt, <agresp,
	agParamView, gainsclSl, lookaheadSl, lagbehindSl, pkthreshSl, pkcompSl,
	gainsclSpec, lookaheadSpec, lagbehindSpec, pkthreshSpec, pkcompSpec,
	iotext;

	*new { | model, server, windowLabel |
		^super.newCopyArgs( model, server, windowLabel ).init;
	}

	init {
		server = server ?? Server.default;

		gainSpec = ControlSpec(-30, 12, \lin, 0.1, 0); // min, max, mapping, step, default
		threshSpec = ControlSpec(-50, 0, \lin, 0.1, 0);
		compSpec = ControlSpec(1, 10, \lin, 0.01, 1);
		xSpec = ControlSpec(20, 20000, 4, 1); // min, max, mapping, step
		ampSpec = ControlSpec(-60, 12, -4, 0.1, 0); // min, max, mapping, step, default

		#floor, ceil, targ, gainrolloff = [model.floor, model.ceil, model.targ, model.gainrolloff];

		this.makeView();

		model.addDependant( this );
	}

	remove {
		model.removeDependant( this );
	}

	update { | who, what ...args |
		var id, val;
		(who == model).if({
			id = args[0];
			val = args[1];

			switch ( what )
				{ \thresh } { threshSliders[id].value_( threshSpec.unmap( val ) ).string_( val ) }
				{ \comp } { compSliders[id].value_( compSpec.unmap( val ) ).string_( val ) }
				{ \gain } { gainSliders[id].value_( gainSpec.unmap( val ) ).string_( val ) }
				{ \freq } { xSliders[id].value_( xSpec.unmap( val ) ).string_( val ) }
				{ \gainscale } { gainsclSl.value_( gainsclSpec.unmap( val ) ).string_( val ) }
				{ \lookahead } { lookaheadSl.value_( lookaheadSpec.unmap( val ).string_( val ) )}
				{ \lagbehind } { lagbehindSl.value_( lagbehindSpec.unmap( val ) ).string_( val ) }
				{ \pkthresh } { pkthreshSl.value_( pkthreshSpec.unmap( val ) ).string_( val ) }
				{ \pkcomp } { pkcompSl.value_( pkcompSpec.unmap( val ) ).string_( val ) }
				{ \iobus } { iotext.string_( " Reading in bus:  " ++ model.compbus.bus ++ " Sending out bus:  " ++ model.outbus ) }
				;
		})
	}

	close { win.close }

	free { win.close }

	makeView {

		win = Window.new( windowLabel ?? {'multi-band compression'}, Rect(800, 300, 820, 600) ).front;
		masterView = VLayoutView(win, Rect(0,0, win.bounds.width, win.bounds.height));
		compView = HLayoutView(masterView, Rect(0,0, masterView.bounds.width, 275));
		compView.decorator = FlowLayout( compView.bounds, 5@5, 5@5 );

		//// band views
		bandElemSpacing = 1;
		#bv1, bv2, bv3, bv4, bv5 = 5.collect({ |i| HLayoutView(compView, Rect(0,0,compView.bounds.width/	5-5,compView.bounds.height-10)).setProperty(\spacing,bandElemSpacing)
			.background_(Color.green(1-(0.1 * i) - 0.3))
		});

		//// meter views
		meterWidth = 55;
		#mv1, mv2, mv3, mv4, mv5 = [bv1, bv2, bv3, bv4, bv5].collect({|view|
			VLayoutView(view, Rect(0,0,meterWidth,view.bounds.height))
		});

		//// threshold views
		#tv1, tv2, tv3, tv4, tv5 = [bv1, bv2, bv3, bv4, bv5].collect({|view|
			VLayoutView(view, Rect(0,0, (view.bounds.width - meterWidth)/3, view.bounds.height))
			});
		//// compression views
		#cv1, cv2, cv3, cv4, cv5 = [bv1, bv2, bv3, bv4, bv5].collect({|view|
			VLayoutView(view, Rect(0,0, (view.bounds.width - meterWidth)/3, view.bounds.height))
			});
		//// gain views
		#gv1, gv2, gv3, gv4, gv5 = [bv1, bv2, bv3, bv4, bv5].collect({|view|
			VLayoutView(view, Rect(0,0, (view.bounds.width - meterWidth)/3, view.bounds.height))
			});

		//// labels
		[tv1, tv2, tv3, tv4, tv5].do({|view| StaticText(view, Rect(0,0,view.bounds.width,20)).background_(Color.green(0.9)).font_( Font("Helvetica", 9) ).string_("Thresh").align_(\center) });

		[cv1, cv2, cv3, cv4, cv5].do({|view| StaticText(view, Rect(0,0,view.bounds.width,20)).background_(Color.green(0.8)).font_( Font("Helvetica", 9) ).string_("Comp").align_(\center) });

		[gv1, gv2, gv3, gv4, gv5].do({|view| StaticText(view, Rect(0,0,view.bounds.width,20)).background_(Color.green(0.7)).font_( Font("Helvetica", 9) ).string_("Gain").align_(\center) });


		//// sliders
		// threshold
		threshSliders = [tv1, tv2, tv3, tv4, tv5].collect({ |view, i|
			SmoothSlider( view,  Rect( 0,0,view.bounds.width,view.bounds.height-5-20) )
				.action_({ |sl|
					var mapval;
					mapval = threshSpec.map( sl.value ); //.linlin(0, 1.0,-50, 0); // threshold in dB
					model.threshcntls[i].value( mapval );
					sl.string = mapval.asString;
					})
				.stringColor_(Color.white)
				.hilightColor_(Color.blue.alpha_(0.25))
				.thumbSize_(4)
				.stringAlignToKnob_(true)
				.value_( threshSpec.unmap( threshSpec.default ));
		});
		threshSliders.do(_.doAction);

		// compression
		compSliders = [cv1, cv2, cv3, cv4, cv5].collect({ |view, i|
			SmoothSlider( view,  Rect( 0,0,view.bounds.width,view.bounds.height-5-20) )
				.action_({ |sl|
					var mapval;
					mapval = compSpec.map( sl.value ); //sl.value.linlin(0, 1.0, 1, 8); // compresstion ratio
					model.compcntls[i].value( mapval );
					sl.string = mapval.round( 0.1 ).asString;
					})
				.stringColor_(Color.white)
				.hilightColor_(Color.blue.alpha_(0.35))
				.thumbSize_(4)
				.stringAlignToKnob_(true)
				.value_( compSpec.unmap( compSpec.default ));
		});
		compSliders.do(_.doAction);

		// gain
		gainSliders = [gv1, gv2, gv3, gv4, gv5].collect({ |view, i|
			SmoothSlider( view,  Rect( 0,0,view.bounds.width,view.bounds.height-5-20) )
				.action_({ |sl|
					var mapval;
					mapval = gainSpec.map( sl.value );  //sl.value.linlin(0, 1.0,-30, 12); // gain in dB
					model.gaincntls[i].value( mapval );
					sl.string = mapval.round( 0.1 ).asString;
					})
				.stringColor_(Color.white)
				.thumbSize_(4)
				.stringAlignToKnob_(true)
				.value_( gainSpec.unmap( gainSpec.default ) );
		});
		gainSliders.do(_.doAction);

		//// meters

		// band labels
		[mv1, mv2, mv3, mv4, mv5].do({|view, i| StaticText(view, Rect(0,0,view.bounds.width,20)).background_(Color.green(0.2)).font_( Font("Helvetica", 14) ).stringColor_(Color.green(0.9)).string_("Band " ++ (i+1)).align_(\center) });

		peaklabels = [mv1, mv2, mv3, mv4, mv5].collect({|view, i| StaticText(view, Rect(0,0,view.bounds.width, 15)).background_(Color.grey(0.75)).font_( Font("Helvetica", 14) ).string_("pk " ++ i).align_(\center) });

		// meters
		meters = [mv1, mv2, mv3, mv4, mv5].collect({ |view|
				var cView;
				cView = CompositeView(view,
						Rect(0, 0, view.bounds.width/2, view.bounds.height-85));
				LevelIndicator( cView, Rect(15, 0, cView.bounds.width/2, cView.bounds.height) )
					.drawsPeak_(true)
					.critical_(0.dbamp);
			});

		rmslabels = [mv1, mv2, mv3, mv4, mv5].collect({|view, i| StaticText(view, Rect(0,0,view.bounds.width, 15)).background_(Color.grey(0.75)).font_( Font("Helvetica", 14) ).string_("rms " ++ i).align_(\center) });

		isSoloed = 5.collect({ false });
		isSounding = 5.collect({ true });

		// solos
		#s1, s2, s3, s4, s5 = [mv1, mv2, mv3, mv4, mv5].collect({ |view, i|
				Button(view, 20@20)
					.states_([
						["s", Color.black, Color.grey],
						["s", Color.black, Color.yellow]
					])
					.action_({|button|
						var othersSoloed;
						(button.value==1).if({
								// wake up if muted
								isSounding[i].not.if({
									model.gaincntls[i].value( model.tempgains[i]);
									isSounding[i] = true;
								});
								isSoloed[i] = true;
								// mute those sounding and not solo'd
								model.gaincntls.do({ |cntl, j|
									( isSounding[j] && isSoloed[j].not ).if({
										model.tempgains[j] = model.getgains[j].value;
										cntl.value( -200 );
										isSounding[j] = false;
										isSoloed[j] = false;
									});
								});
								("soloing band " ++ (i+1).asString).postln;
						},{
								othersSoloed = false;
								block { |break|
									5.do({|j| ((j!=i) && isSoloed[j]).if({
										othersSoloed = true; break.value})
									})
								};
								othersSoloed.if({ // mute me
									model.tempgains[i] = model.getgains[i].value(  );
									model.gaincntls[i].value( -200 );
									isSounding[i] = false;
									isSoloed[i] = false;
								},{	// unsolo all
									model.gaincntls.do({ |cntl, j|
										(j != i).if({ cntl.value( model.tempgains[j] ) });
										isSounding[j] = true;
										isSoloed[j] = false;
									})
								});

						})
					})
		});


		compresp = OSCresponder(server.addr, '/blevels', {arg time, resp, msg;
			{	var dex, pk, rms;
				dex = msg[2];
				pk =  msg[4].ampdb.round(0.5);
				pk = ( (pk-(pk.asInteger)) == 0).if({pk.asString ++ ".0"}, {pk.asString});
				pk = ((pk.size < 5) && pk.asFloat.isNegative).if({pk.insert(1, " ")},{pk});
				rms =   msg[3].ampdb.round(0.5);
				rms = ( (rms-(rms.asInteger)) == 0).if({rms.asString ++ ".0"}, {rms.asString});
				rms = ((rms.size < 5) && rms.asFloat.isNegative).if({rms.insert(1, " ")},{rms});

				peaklabels[dex].string_( pk );
				rmslabels[dex].string_( rms );
				meters[dex].value_( msg[3].ampdb.linlin(-60, 0, 0, 1) )
					.peakLevel_( msg[4].ampdb.linlin(-60, 0, 0, 1) );

			}.defer;
		});

		xSpacing = 2;

		xView = CompositeView(masterView, Rect(0,0,masterView.bounds.width, 130)).background_( Color(0.35181558132172, 0.95105316638947, 0.80707138538361) );
		xView.decorator_(FlowLayout(xView.bounds, 0@0, 3@xSpacing));

		StaticText(xView, Rect( 0,0,xView.bounds.width,20)).align_(\center)
			.string_("Crossover Frequncies").font_(Font("Helvetica", 14))
			.background_( Color.green.alpha_(0.3) );

		xSliders = 6.collect({ |i|
			var sldr, nbx;

			nbx = NumberBox(xView,
				Rect(0,0, 40,
					(xView.bounds.height/6) - ((20+(6*xSpacing))/6) ))
				.value_(model.defXOverFreqs[i])
				.action_( {|num|
					sldr.valueAction_(xSpec.unmap(num.value))
					});

			sldr = SmoothSlider( xView,
				Rect( 0, 0,
					xView.bounds.width - 50,
					(xView.bounds.height/6) - ((20+(6*xSpacing))/6) ))
				.action_({ |sl|
					var mapval;
					mapval = xSpec.map(sl.value); // threshold in dB
					model.freqcntls[i].value( mapval );
					sl.string = mapval.asString;
					nbx.value_( mapval );
					})
				.hilightColor_(Color.gray(0.5, 0.5))
				.background_(Color.gray(0.5, 0.5))
				.stringColor_(Color.white)
				.thumbSize_(34)
				.stringAlignToKnob_(true)
				.value_(xSpec.unmap(model.defXOverFreqs[i]));
			sldr;
		});
		xSliders.do(_.doAction);


	psetView = CompositeView( masterView, Rect(0,0,masterView.bounds.width, 200) )
				.background_( Color(0.35181558132172, 0.95105316638947, 0.80707138538361) );
	psetView.decorator = FlowLayout.new(psetView.bounds, 2@2, 2@2);

	3.do{psetView.decorator.nextLine}; // spacing

	Button(psetView, Rect(0,0, psetView.bounds.width/4, 20) )
		.states_([["bypass", Color.yellow, Color.black],["bypassed", Color.red, Color.black]])
		.action_({ |butt|
			switch (butt.value,
				1, {"bypassing compressor".postln; model.synth.bypass_(1) },
				0, { "resuming compressor".postln; model.synth.bypass_(0) }
			)
		});

	StaticText( psetView, Rect(0,0, psetView.bounds.width/4, 20) )
		.string_( "Global Amplitude    " )
		.font_( Font("Helvetica", 14) ).align_(\right);

	ampSlider =
		SmoothSlider( psetView,  Rect( 0,0,psetView.decorator.indentedRemaining.bounds.width,20) )
			.action_({ |sl|
				var mapval;
				mapval = ampSpec.map( sl.value );  //sl.value.linlin(0, 1.0,-30, 12); // gain in dB
				model.amp_( mapval );
				sl.string = mapval.round( 0.1 ).asString;
				})
			.stringColor_(Color.white).thumbSize_(30).stringAlignToKnob_(true)
			.value_( ampSpec.unmap( gainSpec.default ) )
			.doAction;


	6.do {psetView.decorator.nextLine};

	StaticText( psetView, Rect(0,0, psetView.bounds.width, 20) )
		.string_( "  P   R   E   S   E   T   S  " )
		.font_( Font("Helvetica", 14) ).align_(\left);
	psetView.decorator.nextLine;

	tf = TextField(psetView, Rect(0,0, 180, 20))
		.action_({arg field; field.value.postln; });

	Button(psetView, Rect(0,0, 150, 20))
		.states_([["save", Color(0.35181558132172, 0.95105316638947, 0.80707138538361), Color.black]])
		.action_({ |butt|
			var name;
			name = tf.value.asSymbol;
			model.storePreset( name );
			psetPopUp.items_(
				psetPopUp.items.includes(name).not.if({
					psetPopUp.items.add( name )
				},{
					psetPopUp.items
				})
			)
		});

	3.do{psetView.decorator.nextLine};

	psetPopUp = PopUpMenu(psetView,Rect(0,0,180,20))
		.action_({ arg menu;
			//[menu.value, menu.item].postln;
		})
		.items_( ["-"] ++ Archive.global[\mbcPresets].keys.asArray);

	Button(psetView, Rect(0,0, 150, 20))
		.states_([["recall", Color(0.35181558132172, 0.95105316638947, 0.80707138538361), Color.black]])
		.action_({ |butt|
			model.recallPreset( psetPopUp.item.asSymbol );
		});

	Button(psetView, Rect(0,0, 150, 20))
		.states_([["remove", Color(0.35181558132172, 0.95105316638947, 0.80707138538361), Color.black]])
		.action_({ |butt|
			Archive.global[\mbcPresets].removeAt( psetPopUp.item.asSymbol );
			psetPopUp.items_( ["-"] ++ Archive.global[\mbcPresets].keys.asArray );
			psetPopUp.value_(0);
		});

	Button(psetView, Rect(0,0, 150, 20))
		.states_([["post values", Color(0.35181558132172, 0.95105316638947, 0.80707138538361), Color.black]])
		.action_({ |butt|
			var test;
			test = Archive.global[\mbcPresets][ psetPopUp.item.asSymbol ];
			test.isNil.if({
					"Save settings as a preset first!".postln;
				},{
					test.keysValuesDo({ |key, value| key.postln; value.postln})
				})
		});

	psetView.decorator.nextLine;

	iotext =
		StaticText( psetView, Rect(0,0, psetView.bounds.width, 20) )
			.string_( " Reading in bus:  " ++ model.compbus.bus ++ " Sending out bus:  " ++ model.outbus )
			.font_( Font("Helvetica", 14) ).align_(\left);
	psetView.decorator.nextLine;

	StaticText( psetView, Rect(0,0, psetView.bounds.width, 20) )
			.string_( "For use, route to x.compbus, and pull from x.outbus")
			.font_( Font("Helvetica", 14) ).align_(\left);

	win.onClose = { compresp.remove; this.remove };

	compresp.add;

	//this.makeAGView;
	}
}


/*
//auto-gain gui
(
var gainView, v1,v2,v3,v4,v5,l1, l2, l3, l4,l5, t1, t2, t3, t4,t5,
floor, ceil, targ, gainrolloff, cnt,
agParamView, onsetdelSl, rmsSl, smoothSl,
onsetdelSpec, rmsSpec, smoothSpec;

w = Window.new('auto-gain', Rect(800, 300, 600, 650)).front;
gainView = CompositeView.new(w, Rect(0,0, w.bounds.width, w.bounds.height) ).background_(Color.rand);

#v1,v2 = 2.collect({ |i| VLayoutView(gainView, Rect(40 * i, 10, 40, 380)) });

// input level meter
l1 = LevelIndicator(v1, v1.bounds.width@(v1.bounds.height-60))
	.warning_(-6.dbamp).critical_(0.dbamp).drawsPeak_(true);

// post-gain level meter
l2 = LevelIndicator(v2, v2.bounds.width@(v2.bounds.height-60))
	.warning_(-6.dbamp).critical_(0.dbamp).drawsPeak_(true);

// applied gain
v3 = VLayoutView(gainView, Rect(40 * 3, 30, 40, 345));
l3 = LevelIndicator(v3, v3.bounds.width/2@(v3.bounds.height-30)).style_(1);


t1 = StaticText(v1, v1.bounds.width@20).align_(\center);
t2 = StaticText(v2, v2.bounds.width@20).align_(\center);
t3 = StaticText(v3, v3.bounds.width@20).align_(\center);

StaticText(v1, v1.bounds.width@15).align_(\center).string_("Input");
StaticText(v2, v2.bounds.width@15).align_(\center).string_("Post-");
	StaticText(v2, v2.bounds.width@15).align_(\center).string_("Gain");
StaticText(v3, v3.bounds.width@15).align_(\center).string_("Gain");

// envelope lookup view
v4 = VLayoutView(gainView, Rect(40 * 4, 10, 350, 350));

floor = -40; // lowest input signal rms in db
ceil = -10; // highest input signal rms in db
targ = -10;	// target rms to normalize signal
gainrolloff = 0; // gain rolloff scales down the amp boost at the lowest input threshold

Env(
	[-160, targ-floor-gainrolloff, targ-ceil].dbamp,
	[floor.dbamp, ceil.dbamp - floor.dbamp],
	[5, 0]
	).plot(parent: v4
);

// input level lookup meeter
l4 = LevelIndicator(gainView, Rect(v4.bounds.left, v4.bounds.bottom, v4.bounds.width, 20)).style_(1);
t4 = StaticText(gainView, Rect(v4.bounds.left, v4.bounds.bottom+25, v4.bounds.width, 20)).align_(\center);

// final output meter
v5 = VLayoutView(gainView, Rect(v4.bounds.right+20, 10, 40, 380)).background_(Color.rand);
l5 = LevelIndicator(v5, Rect(0, 0, v5.bounds.width, v5.bounds.height-60));
l5.warning_(-6.dbamp).critical_(0.dbamp).drawsPeak_(true);
t5 = StaticText(v5, v5.bounds.width@20).align_(\center);

StaticText(v5, v5.bounds.width@15).align_(\center).string_("Post-");
StaticText(v5, v5.bounds.width@15).align_(\center).string_("Comp");


[l1,l2,l3,l4,l5].do({|mtr| mtr.numTicks_(13).numMajorTicks_(5) });

cnt = 0;

o = OSCresponder(s.addr, '/autogainlevels', {arg time, resp, msg;
	{
		l1.value = msg[3].ampdb.linlin(-40, 0, 0, 1);
		l1.peakLevel = msg[4].ampdb.linlin(-40, 0, 0, 1);

		l2.value = msg[5].ampdb.linlin(-40, 0, 0, 1);
		l2.peakLevel = msg[6].ampdb.linlin(-40, 0, 0, 1);

		//applied gain
		l3.value = msg[7].ampdb.linlin(0, 25, 0, 1);

		// rms lookup in gain transfer function
		l4.value = msg[8].linlin(floor.dbamp, ceil.dbamp - floor.dbamp, 0, 1);

		l5.value = msg[9].ampdb.linlin(-40, 0, 0, 1);
		l5.peakLevel = msg[10].ampdb.linlin(-40, 0, 0, 1);

	cnt = cnt + 1;
	((cnt % 3) == 0).if({
			cnt = 0;
			t1.string_(msg[4].ampdb.round(0.1).asString);
			t2.string_(msg[6].ampdb.round(0.1).asString);
			t3.string_(msg[7].ampdb.round(0.1).asString);
			t4.string_(msg[8].ampdb.round(0.1).asString);
			t5.string_(msg[10].ampdb.round(0.1).asString);
		});

	}.defer;
}).add;

w.onClose = {o.remove;};

agParamView = CompositeView.new(gainView, Rect(0,400,gainView.bounds.width, 100)).background_(Color.rand);
agParamView.decorator = FlowLayout.new(
	Rect(0,0,agParamView.bounds.width, agParamView.bounds.height),
	2@2, 2@2
	);

onsetdelSpec = ControlSpec.new(0,10,\lin,0.5,2); // min, max, mapping, step, default
rmsSpec = ControlSpec.new(0.01,1,\lin,0.005,0.25);
smoothSpec = ControlSpec.new(0.05,10,\lin,0.05,3);

// onset delay
StaticText(agParamView, 100@20).align_(\right).string_("Onset Delay");
onsetdelSl = SmoothSlider.new(agParamView, Rect(0,0, 300, 20))
			.action_({ |sl|
				var mapval;
				mapval = onsetdelSpec.map(sl.value);
				sl.string = mapval.round( 0.1 ).asString;

				})
			.stringColor_(Color.white)
			.thumbSize_(4)
			.stringAlignToKnob_(true)
			.value_( onsetdelSpec.unmap( onsetdelSpec.default ) )
			.doAction;
agParamView.decorator.nextLine;

// rms time
StaticText(agParamView, 100@20).align_(\right).string_("RMS time");
rmsSl = SmoothSlider.new(agParamView, Rect(0,0, 300, 20))
			.action_({ |sl|
				var mapval;
				mapval = rmsSpec.map( sl.value );
				sl.string = mapval.round( 0.1 ).asString;
				})
			.stringColor_(Color.white)
			.thumbSize_(4)
			.stringAlignToKnob_(true)
			.value_( rmsSpec.unmap( rmsSpec.default ) )
			.doAction;
agParamView.decorator.nextLine;

// smooth
StaticText(agParamView, 100@20).align_(\right).string_("Smooth");
smoothSl = SmoothSlider.new(agParamView, Rect(0,0, 300, 20))
			.action_({ |sl|
				var mapval;
				mapval = smoothSpec.map( sl.value );
				sl.string = mapval.round( 0.1 ).asString;
				})
			.stringColor_(Color.white)
			.thumbSize_(4)
			.stringAlignToKnob_(true)
			.value_( smoothSpec.unmap( smoothSpec.default ) )
			.doAction;

)
*/

/*
Server.default = s = Server.local.waitForBoot({c = MBCompressor.new});
c.play
c.gui


c.scope
c.freqScope

c.outbus

x = {Out.ar(c.compbus.bus, WhiteNoise.ar*SinOsc.kr(10.reciprocal))}.play
x.free
c.bypassAGain(1)



c.agsynth.xferenv.asEnv

c.free
*/