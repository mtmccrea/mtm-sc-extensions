ControlMixer {
	// copyArga
	var <broadcastTag, <broadcastAddr, <broadcastRate, <server, loadCond, colorShift, <broadcasting;

	var <busnum, <oscTag, <ctlFades, <ctlViews, <>oscEnabled = true, outVal;
	var <ratePeriodSpec, <sclSpec, <offsSpec;
	var <mixView, msgTxt, <broadcastChk, <plotChk, updateBx, outValTxt;
	var <nBoxWidth = 42, <nBoxHeight = 15, <validLFOs, <plotter, <ctlLayout, plotterAdded = false;
	var broadcastBus, broadcastWaittime, broadcastTag, pollTask;//, <broadcasting=false;
	var baseColor, idColor, <mixColor, colorStep;

	*new { | broadcastTag="/myMessage", broadcastNetAddr, broadcastRate=30, server, loadCond, colorShift=0.03, broadcasting=true |
		^super.newCopyArgs( broadcastTag, broadcastNetAddr, broadcastRate, server, loadCond, colorShift, broadcasting ).init;
	}

	init {

		broadcastWaittime = broadcastRate.reciprocal;
		broadcastAddr = broadcastAddr ?? {NetAddr("localhost", 57120)};
		(broadcastTag.asString[0].asSymbol != '/').if{ broadcastTag = "/" ++ broadcastTag };

		ctlFades = [];
		ctlViews = [];
		server = server ?? Server.default;
		this.prDefineColors;

		server.waitForBoot({
			busnum = server.controlBusAllocator.alloc(1);
			postf("Creating ControlMixer to output to %\n", busnum);

			// create a ctk bus to read from for broadcasting, with the same busnum that the ControlFades write to
			broadcastBus = CtkControl(1, 0, 0, busnum);

			validLFOs = [
				'static', SinOsc, LFPar, LFSaw, LFTri, LFCub, LFDNoise0, LFDNoise1, LFDNoise3
			].collect(_.asSymbol);

			ratePeriodSpec = ControlSpec(45.reciprocal, 15, 2.5, default: 3);
			sclSpec = ControlSpec(0, 2, 'lin', default: 1);
			offsSpec = ControlSpec(-1, 1, 'lin', default: 0);

			pollTask = Task({
				inf.do{
					broadcastBus.get({|busnum, val|
						outVal = val;
						defer{ outValTxt.string_(val.round(0.001)) };
					});
					(oscEnabled and: broadcasting).if{ broadcastAddr.sendMsg(broadcastTag, outVal) };
					broadcastWaittime.wait
				}
			});

			pollTask.play;

			this.makeView;
			this.addPlotter;
			this.addCtl;
			{0.4.wait; this.updatePlotterBounds;}.fork(AppClock);

			loadCond !? {loadCond.test_(true).signal};
		});
	}

	// pause/run all the controlFaders
	pause { ctlFades.do(_.pause) }
	run { ctlFades.do(_.run) }

	addCtl { |finishCond|
		var ctl, completeFunc, faderView, loadCond = Condition();
		completeFunc = { loadCond.test_(true).signal };

		fork({
			ctl = ControlFade(fadeTime: 0.0, initVal: 0, busnum: busnum, server: server, onComplete: completeFunc);
			loadCond.wait; loadCond.test_(false);

			ctlFades = ctlFades.add(ctl);

			// create the view for this controlFade
			faderView = ControlMixFaderView(this, ctl, finishCond: loadCond);
			loadCond.wait;

			ctlLayout.add( faderView.view );
			ctlViews = ctlViews.add( faderView.view );

			finishCond !? {finishCond.test_(true).signal};
		}, AppClock);
	}

	removeCtlFader { |ctl|
		// var vHeight = view.bounds.height;

		ctl.release(0.3, freeBus: false); // leave the bus running if others are writing to it

		block{ |break| ctlFades.do{ |cFade, i|
			if(cFade === ctl){
				ctlFades.removeAt(i); "removing a ctl".postln;
				ctlViews.removeAt(i); "removed ctl view".postln;
				break.()
			}
		}};
	}

	makeView {
		mixView = View().layout_(
			VLayout(
				ctlLayout = VLayout(
					[ View().background_(idColor).layout_(
						VLayout(
							HLayout(
								[ msgTxt = TextField().string_(broadcastTag.asString).minWidth_(80), a: \left],
								[ outValTxt = StaticText().string_("broadcast").background_(Color.gray.alpha_(0.25))
									.align_(\left).fixedWidth_(55), a: \right],
							),
							HLayout(
								[ StaticText().string_("OSC").align_(\right), a: \right],
								[ broadcastChk = CheckBox().fixedWidth_(15).value_(broadcasting), a: \right],

								[ StaticText().string_("Hz").align_(\right), a: \right],
								[ updateBx = NumberBox().minDecimals_(3).fixedWidth_(nBoxWidth).fixedHeight_(nBoxHeight).scroll_(false),
									a: \right],

								[ StaticText().string_("Plot").align_(\right), a: \right],
								[ plotChk = CheckBox().fixedWidth_(15), a: \right],
							)
						).margins_(2)
					).maxHeight_(66),

					a: \top ]
				).margins_(2),
				[ Button().states_([["+"]]).action_({this.addCtl()}), a: \top ],
				nil
			).margins_(4).spacing_(3)
		).maxWidth_(290);

		msgTxt.action_({ |txt|
			broadcastTag = (txt.value.asSymbol);
		});

		broadcastChk.action_({|chk| broadcasting = chk.value.asBoolean });
		plotChk.action_({|chk| chk.value.asBoolean.if({plotter.start},{plotter.stop}) }).value_(1);

		updateBx.action_({ |bx|
			broadcastRate = bx.value;
			broadcastWaittime = broadcastRate.reciprocal;
		}).value_(broadcastRate);

	}

	broadcastRate_ { |rateHz|
		broadcastRate = rateHz;
		broadcastWaittime = broadcastRate.reciprocal;
		updateBx.value_(broadcastRate);
	}

	broadcasting_ {|bool|
		broadcasting = bool;
		broadcastChk.value_(bool);
	}

	addPlotter { |plotLength=75, refeshRate=24|
		var view;
		plotter = ControlPlotter( busnum, 1, plotLength, refeshRate).start;
		view = plotter.mon.plotter.parent.view;
		view.fixedHeight_(225);
		// mixView.layout.add( view.minHeight_(view.bounds.height) );
		mixView.layout.insert( view.minHeight_(view.bounds.height), 0 );
		//mixView.layout.setAlignment(0, \top);
		// {0.4.wait; this.updatePlotterBounds;}.fork(AppClock);
		plotterAdded = true;
	}

	updatePlotterBounds {
		var minbound, maxbound, range;
		minbound = ctlFades.collect({ |ctl| ctl.low }).minItem;
		maxbound = ctlFades.collect({ |ctl| ctl.high }).maxItem;
		range = maxbound - minbound;
		plotter.bounds_( minbound - (range * 0.25), maxbound + (range * 0.25) );
	}

	free {
		ctlFades.do(_.free);
		broadcastBus.free;
		pollTask !? { pollTask.stop.clock.clear };
	}

	prDefineColors {
		baseColor = Color.hsv(
			// Color.newHex("BA690B").asHSV;
			// Color.newHex("2C4770").asHSV;
			0.60049019607843, 0.60714285714286, 0.43921568627451, 1 );

		idColor = Color.hsv(
			*baseColor.asHSV.put( 0, (baseColor.asHSV[0] + colorShift).wrap(0,1) )
		);

		mixColor = Color.hsv(
			*idColor.asHSV
			.put(3, 0.8)
			.put(2, idColor.asHSV[2] * 1.35)
			//.put(2, (baseColor.asHSV[2] * 1.4).clip(0,1))
		);
	}

}

ControlMixFaderView {
	// copyArgs
	var mixer, ctl, <min, <max, finishCond;
	var <view, completeFunc, nBoxWidth, nBoxHeight;
	// gui
	var minBx, maxBx, rateBx, rateSl, rateTxt, periodChk, mixBx;
	var valBx, mixKnb, sigPUp, rmvBut, sclBx, sclKnb, offsBx, offsKnb;

	*new{ | mixer, controlFade, min= 0, max=1, finishCond|
		^super.newCopyArgs(mixer, controlFade, min, max, finishCond).init;
	}

	init {
		ctl.addDependant(this);
		nBoxWidth = mixer.nBoxWidth;
		nBoxHeight = mixer.nBoxHeight;

		view = View().background_(mixer.mixColor).maxHeight_(205)
		.layout_(
			VLayout(
				HLayout(
					[ VLayout(
						sigPUp = PopUpMenu().maxWidth_(125).maxHeight_(17)
					).spacing_(0), a: \left ],
					nil,
					[ rmvBut = Button().states_([["X", Color.black, Color.red]])
						.fixedWidth_(nBoxWidth/2).fixedHeight_(nBoxWidth/2), a: \topRight]
				),
				HLayout(
					[ VLayout(
						StaticText().string_("min"),
						minBx = NumberBox()
						.fixedWidth_(nBoxWidth).fixedHeight_(nBoxHeight)
						.minDecimals_(3).scroll_(false)
					).spacing_(0), a: \left ],
					[ VLayout(
						StaticText().string_("max"),
						maxBx = NumberBox()
						.fixedWidth_(nBoxWidth).fixedHeight_(nBoxHeight)
						.minDecimals_(3).scroll_(false)
					).spacing_(0), a: \left ],
					nil,
					[ VLayout(
						StaticText().string_("StaticVal").align_(\left),
						valBx = NumberBox()
						.fixedWidth_(nBoxWidth*1.2).fixedHeight_(nBoxHeight)
						.minDecimals_(3).scroll_(false)
					).spacing_(5), a: \right ],
				),
				HLayout(
					[ VLayout(
						rateTxt = StaticText().string_("Rate(sec)"),
						rateBx = NumberBox().fixedWidth_(nBoxWidth*1.5).fixedHeight_(nBoxHeight)
						.minDecimals_(3).scroll_(false),
					).spacing_(0), a: \left ],
					[ VLayout(
						StaticText().string_("period").align_(\center),
						periodChk = CheckBox().fixedWidth_(15),
					).spacing_(0), a: \left ],
					nil
				),
				rateSl = Slider().orientation_(\horizontal).maxHeight_(25).minWidth_(120),
				HLayout(
					VLayout(
						StaticText().string_("scale").align_(\center),
						sclBx = NumberBox()
						.fixedWidth_(nBoxWidth).fixedHeight_(nBoxHeight)
						.minDecimals_(3).scroll_(false),
					).spacing_(0),
					sclKnb = Knob().step_(0.001).mode_(\vert).centered_(true),
					VLayout(
						StaticText().string_("offset").align_(\center),
						offsBx = NumberBox()
						.fixedWidth_(nBoxWidth).fixedHeight_(nBoxHeight)
						.minDecimals_(3).scroll_(false),
					).spacing_(0),
					offsKnb = Knob().step_(0.001).mode_(\vert).centered_(true),
				),
				HLayout(
					nil,
					[VLayout(
						StaticText().string_("mix").align_(\right)
						.fixedWidth_(nBoxWidth).fixedHeight_(nBoxHeight),
						mixBx = NumberBox()
						.fixedWidth_(nBoxWidth).fixedHeight_(nBoxHeight)
						.minDecimals_(3).scroll_(false),
					).spacing_(0), a: \center],
					[ mixKnb = Knob().step_(0.001).mode_(\vert), a: \center],
					nil
				)
			).margins_(4).spacing_(2)
		);

		// define the actions

		minBx.action_({ |bx|
			ctl.low_(bx.value);
			max = bx.value;
			mixer.updatePlotterBounds;
			// updateOffset.();
		}).value_(min);

		maxBx.action_({ |bx|
			ctl.high_(bx.value);
			max = bx.value;
			mixer.updatePlotterBounds;
			// updateOffset.();
		}).value_(max);

		sigPUp.items_(mixer.validLFOs).action_({|sl|
			if( sl.item.asSymbol != 'static' )
			{
				var rateHz;
				rateHz = mixer.ratePeriodSpec.map(rateSl.value).reciprocal;
				ctl.lfo_( sl.item.asSymbol, rateHz, minBx.value, maxBx.value)

			}
			{ ctl.value_( valBx.value ) };
		});

		valBx.action_({ |bx| ctl.value_(bx.value); sigPUp.value_(0)});

		rateSl.action_({ |sl|
			var rateSec, rateHz;

			rateSec = mixer.ratePeriodSpec.map(sl.value);
			rateHz = mixer.ratePeriodSpec.map(sl.value).reciprocal;

			ctl.freq_( rateHz );
			rateBx.value_( periodChk.value.asBoolean.if({rateSec},{rateHz}) );
		}).value_(mixer.ratePeriodSpec.unmap(mixer.ratePeriodSpec.default));

		rateBx.action_({ |bx|
			var rateSec, rateHz;

			if( periodChk.value.asBoolean,
				{	rateHz = bx.value.reciprocal;
					rateSec = bx.value;
				},
				{	rateHz = bx.value;
					rateSec = bx.value.reciprocal;
				}
			);
			ctl.freq_( rateHz );

			rateSl.value_( mixer.ratePeriodSpec.unmap(rateSec) );
		}).value_( mixer.ratePeriodSpec.default ).clipLo_(0.0);

		periodChk.action_({ |chk|
			var bool, curRateBx;
			bool = chk.value.asBoolean;
			curRateBx = rateBx.value;
			rateBx.value_(curRateBx.reciprocal);
			bool.if({ rateTxt.string_("Rate(sec)") },{ rateTxt.string_("Rate(Hz)") });
		}).value_(true);

		offsBx.action_({|bx|
			ctl.offset_(bx.value);
			offsKnb.value_(mixer.offsSpec.unmap(bx.value));
		}).value_(mixer.offsSpec.default);

		offsKnb.action_({|knb|
			var val = mixer.offsSpec.map(knb.value);
			ctl.offset_(val);
			offsBx.value_(val);
		}).value_(mixer.offsSpec.unmap(mixer.offsSpec.default));

		sclBx.action_({|bx|
			ctl.scale_(bx.value);
			sclKnb.value_(mixer.sclSpec.unmap(bx.value));

		}).value_(mixer.sclSpec.default);

		sclKnb.action_({|knb| var val = mixer.sclSpec.map(knb.value);
			ctl.scale_(val);
			sclBx.value_(val);
		}).value_(mixer.sclSpec.unmap(mixer.sclSpec.default));


		rmvBut.action_({
			ctl.removeDependant(this);
			mixer.removeCtlFader(ctl);
			fork({
				view.remove;
				0.1.wait;
				// win.setInnerExtent(win.view.bounds.width, win.view.bounds.height - vHeight );
			}, AppClock);
		});

		mixBx.action_({|bx| ctl.amp_(bx.value); }).value_(1);
		mixKnb.action_({|knb|
			var val = knb.value.sqrt;  // power scaling
			ctl.amp_(val);
		}).value_(1);

		finishCond.test_(true).signal;
	}

	updateOffset {
		var range;
		range = ctl.high - ctl.low;
		mixer.offsSpec.minval_(range.half.neg);
		mixer.offsSpec.maxval_(range.half);
	}

	// var minBx, maxBx, rateBx, rateSl, rateTxt, periodChk, mixBx;
	// var valBx, mixKnb, sigPUp, rmvBut, sclBx, sclKnb, offsBx, offsKnb;
	update { | who, what ... args |
		// we're only paying attention to one thing, but just in case we check to see what it is
		if( who == ctl, {
			defer { // defer for AppClock to handle all the gui updates
				switch ( what,

					\staticVal, {valBx.value_(args[0]) },
					\lfo, {
						sigPUp.value_( mixer.validLFOs.indexOf(args[0].asSymbol) );
					},
					\freq, {
						var val;
						val = periodChk.value.asBoolean.if({ args[0].reciprocal },{ args[0] });
						rateBx.value_(val);
						rateSl.value_( mixer.ratePeriodSpec.unmap( args[0].reciprocal ) )
					},
					\low, { minBx.value_(args[0]) },
					\high, { maxBx.value_(args[0]) },
					\scale, { sclBx.value_(args[0]); sclKnb.value_(mixer.sclSpec.unmap(args[0])) },
					\offset, { offsBx.value_(args[0]); offsKnb.value_(mixer.offsSpec.unmap(args[0])) },
					\amp, { mixBx.value_(args[0]); mixKnb.value_(args[0].squared) }
				)
			}
		});
	}
}

ControlMixMaster {
	// copyArgs
	var broadcastTags, <broadcastNetAddr, broadcastRate, server;
	var <win, <mixers, <lastUpdated, <presetWin, <canvas, <globalFadeTime = 0.0, <presetsPerColumn=12.0;

	*new { |broadcastTags="/myControlVal", broadcastNetAddr, broadcastRate=30, server|
		^super.newCopyArgs(broadcastTags, broadcastNetAddr, broadcastRate, server).init
	}

	init {
		broadcastNetAddr ?? {broadcastNetAddr = NetAddr("localhost", NetAddr.langPort)};
		server = server ?? Server.default;

		mixers = [];

		server.waitForBoot({
			var cshift;

			cshift = rrand(-0.1, 0.1); // -0.03888, 0.093191576004028
			postf("shifting color %\n", cshift);

			this.makeWin;

			broadcastTags.asArray.do({ |tag, i|
				this.addMixer(tag, broadcastNetAddr , broadcastRate, server, (cshift*i));
			});
		});
	}

	addMixer { |sendToNetAddr, oscTag="/myControlVal", sendRate=15, server, colorShift = -0.03888|
		var mixer;
		var loadCond = Condition();
		sendToNetAddr ?? {sendToNetAddr = NetAddr("localhost", NetAddr.langPort)};
		server = server ?? Server.default;

		{
			// win.setInnerExtent( win.view.bounds.width + mixWidth );
			mixer = ControlMixer(sendToNetAddr, oscTag, sendRate, server, loadCond, colorShift);
			mixers = mixers.add(mixer);
			loadCond.wait;
			// win.layout.add( mixer.mixView );
			canvas.layout.add( mixer.mixView ); // for ScrollView
		}.fork(AppClock);
	}

	makeWin {
		var scroller;

		win = Window("Broadcast Controls", Rect(Window.screenBounds.width / 4, Window.screenBounds.height, 900, 600));
		scroller = ScrollView(win, Rect(0,0, 900, 600)).hasBorder_(true).autohidesScrollers_(false);
		canvas = View().layout_(
			HLayout().margins_(2).spacing_(2)
		);
		scroller.canvas_(canvas);

		win.layout_(VLayout(

			View(bounds: Rect(0,0, 900,600)).layout_(VLayout(scroller).margins_(0)),

			HLayout(
				[ Button()
					.states_([["Send OSC", Color.white, Color.blue],["Stop OSC", Color.white, Color.gray]])
					.action_({ |but|
						switch( but.value,
							0, { mixers.do(_.oscEnabled_(false)) },
							1, { mixers.do(_.oscEnabled_(true)) }
						)
				}).maxWidth_(70).fixedHeight_(25).value_(1), a: \left],
				VLayout(
					[ StaticText().string_("Rate"), a: \left],
					[ NumberBox().action_({ |bx|
						mixers.do(_.broadcastRate_(bx.value))}).value_(30).maxWidth_(35).scroll_(false),
					a: \left]
				).margins_(2),
				VLayout(
					[ StaticText().string_("Plot").maxWidth_(25), a: \left],
					[ CheckBox().action_({ |bx| mixers.do({ |mxr|
						bx.value.asBoolean.if({
							mxr.plotChk.value.asBoolean.if{mxr.plotter.start}
						},{ mxr.plotter.stop }
						);
					});
					}).value_(true).maxWidth_(25), a: \left]
				).margins_(2),
				[ Button().states_([["Pause Signals", Color.white, Color.gray],["Resume Controls", Color.black, Color.yellow]]).action_({ |but|
					switch( but.value,
						1, { mixers.do(_.pause) },
						0, { mixers.do(_.run) }
					)
				}).maxWidth_(110).fixedHeight_(25), a: \left
				],
				nil,
				StaticText().string_("Preset Fade Time").align_(\right),
				NumberBox().action_({|bx| this.globalFadeTime_(bx.value) }),
				nil,
				Button().states_([["Presets >>"]]).action_({
					this.presetGUI((this.presets.size / presetsPerColumn).ceil.asInt) // presets per column
				})
			)
		)
		);

		win.onClose_({ this.free }).front;
	}

	// pause/run all the mixers' controlFaders
	pause { mixers.do(_.pause) }
	run { mixers.do(_.run) }

	broadcastRate_{ |rateHz| mixers.do(_.broadcastRate_(rateHz)) }

	globalFadeTime_{ |fadeTime|
		globalFadeTime = fadeTime;
		mixers.do{|mxr| mxr.ctlFades.do(_.fadeTime_(fadeTime))};
	}

	free {
		mixers.do(_.free);
	}

	/* Preset/Archive Support */

	prInitArchive {
		^Archive.global.put(\roverPresets, IdentityDictionary(know: true));
	}

	archive { ^Archive.global[\roverPresets] }
	presets { ^Archive.global[\roverPresets] }
	listPresets { ^this.presets.keys.asArray.sort.do(_.postln) }

	*archive { ^Archive.global[\roverPresets] }
	*presets { ^Archive.global[\roverPresets] }
	*listPresets { ^this.class.presets.keys.asArray.sort.do(_.postln) }

	backupPreset { this.class.backupPreset }

	*backupPreset {
		Archive.write(format("~/Desktop/archive_ControlMixerBAK_%.sctxar",Date.getDate.stamp).standardizePath)
	}


	// keyValPairs: any other data to store in the dictionary associated with this key
	// e.g. [\scenefile, "darktrees.xml"]
	storePreset { |key, overwrite =false, keyValPairs|
		var arch, synth, mixerDict, ctlInfoDict;

		arch = Archive.global[\roverPresets] ?? { this.prInitArchive };

		(arch[key].notNil and: overwrite.not).if {
			format("PRESET NOT SAVED! Preset with that name already exists. Choose another name or first perform .removePreset(%), or explicityly set overwrite=true in this method call.", key).warn
		};


		mixerDict = IdentityDictionary(know: true);

		mixers.do{ |mixer, i|
			var ctlFadeArr = [];
			postf("mixer %\n", i);

			// each mixer can have multiple ctlFades
			mixer.ctlFades.do{ |ctlfade, j|
				postf("\tctlfade %\n", j);
				ctlFadeArr = ctlFadeArr.add(
					IdentityDictionary( know: true ).putPairs([
						\min, ctlfade.low,
						\max, ctlfade.high,
						\signal, ctlfade.lfo,
						\freq, ctlfade.freq,
						\val, ctlfade.value,
						\scale, ctlfade.scale,
						\offset, ctlfade.offset,
						\mix, ctlfade.amp,
					])
				)
			};

			mixerDict.put( mixer.broadcastTag.asSymbol, ctlFadeArr);
		};

		arch.put( key.asSymbol ?? {Date.getDate.stamp.asSymbol},
			IdentityDictionary( know: true ).put( \mixers, mixerDict )
		);

		keyValPairs !? {
			keyValPairs.clump(2).do{ |kvArr| ctlInfoDict.put(kvArr[0].asSymbol, kvArr[1]) };
		};

		postf("Preset Stored\n%\n", key);
		arch[key].keysValuesDo{|k,v| [k,v].postln;}
	}

	prRecallCtlFaderState { | mixer, faderStates, thisFadeTime |
		var kind, ctlFade;

		faderStates.do{ |fDict, fDex|

			//"\tUpdating Control".postln;
			//fDict.keysValuesDo({|k,v| [k,v].postln;});

			ctlFade = mixer.ctlFades[fDex];

			// static or lfo?
			if( fDict[\signal] == 'static' )
			{	// just recall the static val
				ctlFade.value_(fDict[\val], thisFadeTime)
			}
			{	// recall the lfo with bounds, etc...
				"recalling LFO".postln;
				ctlFade.lfo_(fDict[\signal], fDict[\freq], fDict[\min], fDict[\max], thisFadeTime)
			};

			// recall mix, scale offset
			fDict[\scale] !? {ctlFade.scale_(fDict[\scale], thisFadeTime)};
			fDict[\offset] !? {ctlFade.offset_(fDict[\offset], thisFadeTime)};
			fDict[\mix] !? {ctlFade.amp_(fDict[\mix], thisFadeTime)};
		}
	}

	loadSnapshot { |filePath|
		var f, paramDict;
		var zoom, xcount, ycount, xstart, ystart, xscroll, yscroll, focus, scene;
		var minIn, maxIn, minOut, maxOut, gamma, desaturation;

		File.exists(filePath).not.if{warn("could not find the file... check the path")};

		f = FileReader.read(filePath);//.reverse;

		// f.do(_.postln);


		scene = f[0][0]; // scene xml file is first line

		// refocus params are next five lines
		#focus, xscroll, yscroll, xstart, ystart, xcount, ycount, zoom = 5.collect({|i|
			f[i+1][0].split($,).asFloat}).flat;

		// image processing params
		(f.size > 6).if {
			#minIn, maxIn, minOut, maxOut, gamma, desaturation = 4.collect({|i|
			f[i+6][0].split($,).asFloat}).flat;
		};

		postf("Found these parameters:\n\tzoom, %\n\txcount, %\n\tycount, %\n\txstart, %\n\tystart, %\n\txscroll, %\n\tyscroll, %\n\tfocus, %\n\tscene %\n",
			zoom, xcount, ycount, xstart, ystart, xscroll, yscroll, focus, scene);

		(f.size > 6).if {
			postf("Image parameters:\n\tminIn, %\n\tmaxIn, %\n\tminOut, %\n\tmaxOut, %\n\tgamma, %\n\tdesaturation\n",
				minIn, maxIn, minOut, maxOut, gamma, desaturation);
		};

		// initialize preset entry for this snapshot
		this.presets.put(\snapshot, IdentityDictionary().put(\mixers, IdentityDictionary()) );

		paramDict = IdentityDictionary().putPairs([
			'/xcount', xcount,
			'/ycount', ycount,
			'/ystart', ystart,
			'/xstart', xstart,
			'/xscroll', xscroll,
			'/yscroll', yscroll,
			'/focus', focus,
			'/zoom', zoom
		]);

		paramDict.keysValuesDo{ |key, val|
			this.presets[\snapshot][\mixers].put( key,
				[ IdentityDictionary().putPairs(['signal', 'static', 'val', val]) ] );
		};

		// load the new texture
		// broadcastNetAddr.sendMsg('/loadTextures', *textures);
		broadcastNetAddr.sendMsg('/loadScene', scene.asString);

		this.recallPreset(\snapshot);
		// clear the preset so it doesn't show up on the presets GUI
		this.removePreset(\snapshot);
	}

	loadScene{ |sceneName|
		broadcastNetAddr.sendMsg('/loadScene', sceneName);
	}

	recallPreset { |key, thisFadeTime|
		var p;
		block { |break|

			p = this.archive[key] ?? {"Preset not found".error; break.()};
			("recalling " ++ key).postln;

			p[\mixers].keysValuesDo({ |ptag, faderStates|
				var recalled=false;
				// QUIET
				// postf("recalling mixer %\n", ptag.asString);

				fork({ var cond = Condition();
					mixers.do{ |mixer, i|

						if( mixer.broadcastTag.asSymbol == ptag ){
							// check that the current mixer has the same number of controlfaders as the preset
							var numFadersDiff = faderStates.size - mixer.ctlFades.size;

							case
							{numFadersDiff > 0}{
								// recall presets, adding numFadersDiff controls to update
								numFadersDiff.do{
									mixer.addCtl(finishCond: cond);
									cond.wait; 0.1.wait; // wait a little extra time for fade synth to start
								};
							}
							{numFadersDiff < 0}{
								numFadersDiff.abs.do{
									"removing a control".postln;
									mixer.ctlFades.last.release(freeBus: false);
									mixer.ctlFades.removeAt(mixer.ctlFades.size-1);
									mixer.ctlViews.last.remove;
								};
							};

							this.prRecallCtlFaderState( mixer, faderStates, thisFadeTime );

							recalled =true;
							lastUpdated = key;
						};
					};

					recalled.not.if{
						warn( format(
							"No mixer found in the current layout to set the preset tag % not found in current setup",
							ptag ) );
						// TODO add a mixer that was not found present, remove present mixers that aren't in the preset
					};
				}, AppClock );
			});
		}
	}


	updatePreset {
		lastUpdated.notNil.if(
			{ this.storePreset( lastUpdated, true ) },
			{ "last updated key is not known".warn }
		);
	}


	removePreset { |key|
		Archive.global[\roverPresets][key] ?? { format("preset % not found!", key).error };
		Archive.global[\roverPresets].removeAt(key)
	}

	presetGUI { |numCol=2|
		var presetsClumped, ftBox, varBox, msg_Txt, presetLayouts, maxRows;
		maxRows = (this.presets.size / numCol).ceil.asInt;

		presetsClumped = this.presets.keys.asArray.sort.clump(maxRows);
		presetsClumped.do(_.postln);

		presetLayouts = presetsClumped.collect({ |presetGroup|
			VLayout(
				*presetGroup.extend(maxRows,nil).collect({ |name, i|
					var lay;
					name.notNil.if({
						lay = HLayout(
							[ Button().states_([[name]])
								.action_({

									this.recallPreset(name.asSymbol);
									msg_Txt.string_(format("% recalled.", name)).stringColor_(Color.black);
							}), a: \top]
						)
					},{
						nil
					})
				})
			)
		});

		presetWin = Window("Presets",
			Rect( Window.screenBounds.width.half,150, 300, 100)).view.layout_(
			VLayout(

				HLayout(
					nil,
					StaticText().string_("Variance").align_(\right).fixedHeight_(25),
					varBox = NumberBox().value_(0.0).maxWidth_(35).fixedHeight_(25).scroll_(false),
					StaticText().string_("Fade Time").align_(\right).fixedHeight_(25),
					ftBox = NumberBox().value_(1.0).maxWidth_(35).fixedHeight_(25).scroll_(false)
				),
				HLayout(
					msg_Txt = StaticText().string_("Select a preset to update.").fixedHeight_(35),
					Button().states_([["Update Preset", Color.black, Color.gray]]).action_({this.updatePreset}).fixedWidth_(95)
				),
				HLayout( *presetLayouts )
			)
		).front;
	}
}