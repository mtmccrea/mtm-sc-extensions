PlayControlView {
	// copyArgs
	var player, pw, ph;

	var win, uv, plotter, plotView;
	var playBut, rateNb, resetBut, clearSelBut;
	var selendTxt, selstartTxt, seldurTxt;
	var selectionActive=false;
	var selstart=0, selend=0; // selection normalized 0>1


	*new { |aPlayControl, plotWidth=400, plotHeight=400|
		^super.newCopyArgs(aPlayControl, plotWidth, plotHeight).init;
	}

	init {
		player.addDependant(this);
		{
			this.prMakeWindow;
			this.prInitPlotter;
			this.prMakeUserView;
			this.prReplot;
			win.onClose_({ this.free });
			win.front;
			}.defer;
	}

	buffer {	^player.buffer }

	prMakeWindow {

		playBut = Button().states_([["Play Selection"],["Stop"]])
		.action_({ |but|
			but.value.asBoolean.if({player.play}, {player.stop})
		}).value_(player.synth.isRunning.asInt);

		resetBut = Button().states_([["Reset Playhead"]]).action_({ |but| player.reset });
		clearSelBut = Button().states_([["Clear Selection"]]).action_({ |but| player.clearSelection; selectionActive=false });
		rateNb = NumberBox().value_(player.rate).action_({|but| player.rate_(but.value)});
		selstartTxt = StaticText().string_("0:00");
		selendTxt = StaticText().string_("0:00");
		seldurTxt = StaticText().string_("0:00");

		win = Window("Play control signals");

		/* Layout */
		win.view.layout_(
			VLayout(
				HLayout(
					// View to hold the plotter
					plotView = View()
					.fixedSize_(Size(pw, ph))
					.background_(Color.blue.alpha_(0.2)),
					//
					View().fixedWidth_(100).layout_(
						VLayout(
							StaticText().string_("Selection").align_(\center),
							HLayout(
								StaticText().string_("start: ").align_(\right),
								selstartTxt.align_(\left),
							),
							HLayout(
								StaticText().string_("end: ").align_(\right),
								selendTxt.align_(\left),
							),
							HLayout(
								StaticText().string_("dur: ").align_(\right),
								seldurTxt.align_(\left),
							),
							nil
						)
					)
				),
				HLayout(
					playBut,
					20,
					StaticText().string_("rate:").align_(\right),
					rateNb.maxWidth_("00.000".bounds.width).align_(\center),
					5,
					resetBut,
					clearSelBut,
					nil
				)
			).margins_(0).spacing_(0)
		);
	}

	prMakeUserView {

		/* User View */
		uv  = UserView(plotView, this.prGetUvBounds);
		win.view.onResize_({
			uv.bounds_(this.prGetUvBounds);
			uv.refresh;
		});
		uv.background_(Color.red.alpha_(0.15));

		uv.drawFunc_{ |v|
			var bnds, width, top, stPx, endPx, phw=2;

			bnds = v.bounds;
			width = bnds.width;

			if (selectionActive) {
				// Selection
				Pen.fillColor_(Color.blue.alpha_(0.25));
				Pen.fillRect(
					Rect(
						selstart*width, 0,
						(selend-selstart)*width, bnds.height
					)
				);
			};

			// Playhead
			Pen.fillColor_(Color.red.alpha_(0.5));
			Pen.fillRect(
				Rect(
					((player.playhead-player.trimStart)/player.trimLength) * width - (phw/2),
					0, phw, bnds.height
				)
			);

		};

		uv.mouseDownAction_{
			|v, x y|
			selstart = selend = x/v.bounds.width;
			player.selStart_(selstart.clip(0,1));
			selectionActive = true;
			uv.refresh;
		};

		uv.mouseMoveAction_{
			|v, x y|
			selend = x/v.bounds.width;
			player.selEnd_(selend.clip(0,1));
			uv.refresh;
		};

		uv.mouseUpAction_{
			|v, x y|
			postf("st: %  end: %  len: %\n", selstart, selend, selend-selstart);
			player.selEnd_(selend.clip(0,1));
			uv.refresh;
		};

		uv.animate_(true).frameRate_(20);
	}

	prInitPlotter {
		plotter = Plotter(parent: plotView);
	}

	// start: first frame in buffer to plot
	// count: number of frames to plot
	prReplot { |start, count|
		var separately = false, st, cnt;
		// var minval, maxval;
		st = start ? 0;
		cnt = cnt ? player.numFrames;

		// note: this writes a soundfile to disk, then loads that data to
		// an array. This is stable but potentially redundant, given the buffer
		// is already loaded. getToFloatArray requests values directly from the
		// buffer on the server, but is less stable, data may be lost in high traffic.
		player.buffer.loadToFloatArray(
			index: st,
			count: cnt,
			// index:0,
			// count:-1, // all frames
			action: { |array, buf|
				{
					plotter.setValue(
						array.unlace(buf.numChannels),
						findSpecs: true,
						separately: separately,
						refresh: false,
						// minval: minval,
						// maxval: maxval
					);
					plotter.domainSpecs = ControlSpec(st, st+cnt, units:"frames");
					plotter.refresh;
				}.defer
		});
	}

	prGetUvBounds {
		^Rect(plotView.bounds.left+15,  plotView.bounds.top, plotView.bounds.width-(15*2), plotView.bounds.height)
	}

	free {
		player.removeDependant(this);
		plotter = nil;
		win.isClosed.not.if {win.close};
	}
}