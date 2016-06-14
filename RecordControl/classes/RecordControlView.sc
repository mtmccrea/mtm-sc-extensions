RecordControlView {
	// copyArgs
	var recorder;

	var winWidth = 400;
	var winHeight = 100;
	var nbw = 25;

	var fnTxt, dirTxt, dirBut, openDirBut, owrChk, owrTxt;
	var posBusTxt, busTxt, numChNb, recBut, statusTxt;
	var statusUpdateRoutine, color;
	var <win;

	*new { |anControlRecorder|
		^super.newCopyArgs(anControlRecorder).init
	}

	init {
		recorder.addDependant(this);
		color = Color.newHex("#61AAA8");
		this.makeWin;
		win.onClose({ this.free })
	}

	makeWin {

		fnTxt = TextField().string_(recorder.fileName).stringColor_(Color.gray);
		dirTxt = StaticText().string_(recorder.directory).stringColor_(Color.gray);
		dirBut = Button().states_([["Select directory"]]);
		openDirBut = Button().states_([["Open directory"]]);
		owrChk = CheckBox();
		owrTxt = StaticText().string_("Overwrite");
		busTxt = TextField().string_(recorder.busnum);
		numChNb = NumberBox().value_(recorder.numChannels);
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

			openDirBut, {
				recorder.openDirectory;
			},

			owrChk, { |chk|
				recorder.overwrite = chk.value.asBoolean;
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
					// if (fnTxt.string != "fileName") {
					// fnTxt.doAction; // in case it wasn't submitted
						// recorder.fileName_(fnTxt.string); // in case it wasn't submitted
				// };
					recorder.record;
					// un-click the button in case recordin fails
					fork({
						1.wait;
						if (recorder.recording.not) {but.value = 0};
					}, AppClock);
				} {
					recorder.stop;
				}
			},

		].clump(2).do {
			|objActionPair|
			var obj, action;
			#obj, action = objActionPair;
			obj.action_(action);
		};

		fnTxt.mouseDownAction_{fnTxt.stringColor_(Color.gray)};
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
							[dirBut.fixedWidth_(110), a: \center],
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
										StaticText().string_("bus").align_(\center),
										[busTxt.maxWidth_(65).align_(\center), a: \center],
									),
									VLayout(
										StaticText().string_("numChannels").align_(\center),
										[numChNb.align_(\center).minWidth_(45), a: \center],
									)
								)
							)
						)
					),
					[statusTxt.minWidth_(300).minHeight_(50).background_(color.alpha_(0.3)).align_(\center), a: \center]
				),

				20,
				// Right half
				VLayout(
					[recBut, a: \top],
					HLayout(
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

	// TODO
	update {
		|who, what ... args|

		case
		{who==recorder} {
			switch( what,
				\busnum, {
					this.updateStatus(format("Bus updated: %", args[0]), 4);
				},
				\numchannels, {
					this.updateStatus(format("Number of bus channels updated: %", args[0]), 4);
				},
				\filename, {
					fnTxt.string = args[0];
					fnTxt.stringColor_(Color.black);
					this.updateStatus(format("Filename updated: %", args[0]), 4);
				},
				\dirname, {
					dirTxt.string = args[0];
					dirTxt.stringColor_(Color.black);
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