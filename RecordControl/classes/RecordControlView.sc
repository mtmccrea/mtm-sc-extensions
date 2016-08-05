RecordControlView {
	// copyArgs
	var recorder;

	var winWidth = 400;
	var winHeight = 100;
	var nbw = 25;

	var fnTxt, dirTxt, dirBut, curDirBut, openDirBut, owrChk, owrTxt;
	var posBusTxt, busTxt, numChNb, recBut, statusTxt;
	var overlayChk, plotBut, <plotter, prevPlotBounds, plotting=false;
	var bndLoNb, bndHiNb, autoChk;
	var statusUpdateRoutine, color, localDir;
	var <win;

	*new { |anControlRecorder|
		^super.newCopyArgs(anControlRecorder).init
	}

	init {
		localDir = "".resolveRelative;
		recorder.addDependant(this);
		color = Color.newHex("#61AAA8");
		this.makeWin;
		win.onClose_({ this.free })
	}

	makeWin {

		fnTxt = TextField().string_(recorder.fileName).stringColor_(Color.gray);
		dirTxt = StaticText().string_(recorder.directory).stringColor_(Color.gray);
		dirBut = Button().states_([["Select directory"]]);
		curDirBut = Button().states_([["Rec to current directory"]]);
		openDirBut = Button().states_([["Open directory"]]);
		owrChk = CheckBox();
		owrTxt = StaticText().string_("Overwrite");
		busTxt = TextField().string_(recorder.busnum);
		numChNb = NumberBox().value_(recorder.numChannels);
		plotBut = Button().states_([["Plot Signal"]]);
		overlayChk = CheckBox();
		bndLoNb = NumberBox().value_(0).stringColor_(Color.gray);
		bndHiNb = NumberBox().value_(1).stringColor_(Color.gray);
		autoChk = CheckBox().value_(1);
		recBut = Button().states_([["Record", Color.black, Color.red.alpha_(0.5)],["Stop", Color.black, Color.yellow.alpha_(0.5) ]]);
		statusTxt = StaticText().string_("Select a recording directory.").align_(\left);

		win = Window("Record control signals", Rect(0,0, winWidth, winHeight));

		this.defineActions;
		this.layOutControls;

		win.front;
	}

	updateStatus { |str, clearIn|
		{ statusTxt.string_(str) }.defer;

		clearIn.notNil.if {
			statusUpdateRoutine !? { statusUpdateRoutine.stop };

			statusUpdateRoutine = fork( {
				clearIn.wait;
				statusTxt.string = if (recorder.recording) {"Recording..."} {""};
			}, AppClock );
		}
	}

	defineActions {
		[
			fnTxt, { |txt|
				if (txt.string != "fileName") {
					recorder.fileName_(txt.string);
				} {
					recorder.fileName_(Date.getDate.stamp);
				};
				txt.stringColor_(Color.black);
			},

			dirBut, { |but|
				// submit filename in case it wasn't already
				fnTxt.doAction;

				recorder.selectDirectory;
				but.states_([["Select directory"]]); // change back to normal color
			},

			curDirBut, { |but|
				// submit filename in case it wasn't already
				fnTxt.doAction;

				try {
					recorder.directory_(localDir);
				} { |err|
					err.errorString.warn;
					defer {statusTxt.string_(err.errorString)};
				};
				but.states_([["Rec to current directory"]]); // change back to normal color
			},

			openDirBut, {
				recorder.openDirectory;
			},

			owrChk, { |chk|
				recorder.overwrite = chk.value.asBoolean;
				chk.value.asBoolean.if{
					this.updateStatus(format("Warning: you'll be overwriting %", recorder.fileName), 6);
				}
			},

			busTxt, { |txt|
				block{ |break|
					var val = txt.string.interpret;
					val ?? {
						this.updateStatus("Error: could not interpret your value!", 6);
						txt.string = recorder.busnum;
						break.()
					};
					try {recorder.busnum_(val)} {|er|
						this.updateStatus(er.errorString, 6); er.throw;
					};
				}
			},

			numChNb, { |nb|
				recorder.numChannels_(nb.value)
			},

			recBut, { |but|
				if (but.value.asBoolean) {
					recorder.record;

					fork({  // un-click the button in case recording fails
						0.7.wait;
						if (recorder.recording.not) {but.value = 0};
					}, AppClock);
				} {
					recorder.stop;
				}
			},

			plotBut, { |but|
				recorder.plot
			},

			overlayChk, { |chk|
				recorder.overlayPlot_(chk.value.asBoolean)
			},

			autoChk, { |chk|
				if (chk.value.asBoolean) {
					recorder.setPlotterBounds(\auto);
					[bndLoNb, bndHiNb].do{|nb| nb.stringColor_(Color.gray)};
				} {
					recorder.setPlotterBounds(bndLoNb.value, bndHiNb.value);
					[bndLoNb, bndHiNb].do{|nb| nb.stringColor_(Color.black)};
				}
			},

			bndLoNb, { |nb|
				recorder.setPlotterBounds(nb.value, bndHiNb.value);
				autoChk.value_(0);
				[nb, bndHiNb].do{|nb| nb.stringColor_(Color.black)};
			},
			bndHiNb, { |nb|
				recorder.setPlotterBounds(bndLoNb.value, nb.value);
				autoChk.value_(0);
				[bndLoNb, nb].do{|nb| nb.stringColor_(Color.black)};
			},

		].clump(2).do {
			|objActionPair|
			var obj, action;
			#obj, action = objActionPair;
			obj.action_(action);
		};

		fnTxt.mouseDownAction_{
			fnTxt.stringColor_(Color.gray)
		};
	}

	layOutControls {
		win.view.layout_(
			HLayout(

				// Left half
				VLayout(
					View().background_(color).layout_(
						// directoty/filename controls
						VLayout(
							StaticText().string_("File name").align_(\center),
							[fnTxt.fixedWidth_(150).align_(\center), a: \center],
							HLayout(
								[dirBut.fixedWidth_(110), a: \left],
								nil,
								[curDirBut, a: \right],
							),
							[dirTxt.minWidth_(300).align_(\center).minHeight_(50).background_(Color.white), a: \center],
						)
					),
					// bus controls
					HLayout(
						View().background_(color).layout_(
							VLayout(
								StaticText().string_("Control Bus Recorder").align_(\center),
								HLayout(
									VLayout(
										HLayout(
											plotBut.fixedWidth_(85),
											// nil,
											// StaticText().string_("overlay").align_(\right),
											// overlayChk,
										),
										10,
										StaticText().string_("Plot Bounds").align_(\center),
										HLayout(
											autoChk,
											StaticText().string_("auto").align_(\left),
											5,
											StaticText().string_("lo").align_(\left),
											bndLoNb.minWidth_(40).align_(\center),
											StaticText().string_("hi").align_(\left),
											bndHiNb.minWidth_(40).align_(\center),
										),
									),
									nil,
									20,
									VLayout(
										StaticText().string_("bus").align_(\center),
										StaticText().string_("num\nChannels").align_(\center),
									),
									VLayout(
										[busTxt.maxWidth_(65).align_(\center), a: \center],
										[numChNb.align_(\center).minWidth_(45), a: \center],
									)
								)
							)
						)
					),
					[statusTxt.minWidth_(320).minHeight_(50).background_(color.alpha_(0.3)).align_(\center), a: \center]
				),

				30,
				// Right half
				VLayout(
					[recBut, a: \top],
					HLayout(
						nil,
						owrChk,
						owrTxt.align_(\left).minWidth_(owrTxt.string.bounds.width+3),
						nil
					),
					nil,
					[openDirBut, a: \bottomRight],
				)
			)
		);

		// view, kind, param, val, recursive
		this.mapParam(win.view, NumberBox, \fixedWidth_, nbw);
		this.mapParam(win.view, NumberBox, \align_, \center);
	}

	mapParam { |view, kind, param, val, recursive = true|
		view.children.do{
			|child|
			if (kind.isNil) {
				postf("trying to: % a %\n", param, child);
				child.tryPerform( param, val )
			} {
				if (child.isKindOf(kind)) {
					child.tryPerform( param, val )
				}
			};
			if (child.isKindOf(View) and: recursive) {this.mapParam(child, kind, param, val, recursive)};
		}
	}

	free {
		recorder.removeDependant(this);

		statusUpdateRoutine !? {
			statusUpdateRoutine.stop;
			statusUpdateRoutine=nil
		};
		win.isClosed.not.if {win.close};
	}


	update {
		|who, what ... args|

		case
		{who==recorder} {
			switch( what,
				\busnum, {
					this.updateStatus(format("Bus updated: %", args[0]), 4);
					plotting.if{ plotBut.doAction }
				},
				\numchannels, {
					this.updateStatus(format("Number of bus channels updated: %", args[0]), 4);
					plotting.if{ plotBut.doAction }
				},
				\filename, {
					{
						fnTxt.string = args[0];
						fnTxt.stringColor_(Color.black);
					}.defer;
					this.updateStatus(format("Filename updated: %", args[0]), 4);
				},
				\dirname, {
					{
						dirTxt.string = args[0];
						dirTxt.stringColor_(Color.black);
					}.defer;
					this.updateStatus(format("Recording directory updated: %", args[0]), 4);
				},
				\recording, {
					if (args[0]) {
						this.updateStatus("Recording...");
					} {
						this.updateStatus("Recording stopped.");
					};
				},
				\error, {
					this.updateStatus(args[0]);
				}
			)
		}
	}
}