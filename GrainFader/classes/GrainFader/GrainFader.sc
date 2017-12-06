// TODO
// - add deafult value loading to TouchOsc
// add a mute all for the recalling of presets that can then be unmuted and faded in


GrainFader {
	classvar <synthlib;
	// copyArgs
	var <outbus, <monitorBus, <auxBus, <bufFolderPath, <server;
	var <synth1, <synth2, <verbSynth, <monSynths, <internalMonBus;
	var <monBus; // assigned for user if not provided on instantiation
	var <internalAuxBus, <group, <sfNames, <bufPaths, <buffers;
	var <lastRecalledSynthDex = 1, <lastUpdated;
	var <myTouchOsc, presetWin;

	var <>pitchSnapVal = 1.0, <>pitchSnapThresh = 0.1;
	var <>rateSnapVal = 1.0, <>rateSnapThresh = 0.1;

	*new {|outbus=0, monitorBus, auxBus, bufFolderPath, server|
		^super.newCopyArgs( outbus, monitorBus, auxBus, bufFolderPath, server ).init;
	}

	init {
		sfNames = [];
		bufPaths = [];
		// check if buf folder exists and has files
		if (File.exists( bufFolderPath ), {
			// get waves and aiffs
			PathName(bufFolderPath).files.do{
				|filePN|
				var ext = filePN.extension;
				if( (ext == "wav") or: (ext == "aiff" or: (ext == "aif")),
					{
						sfNames = sfNames.add(filePN.fileNameWithoutExtension);
						bufPaths = bufPaths.add(filePN.fullPath);
					}
				);
			};
		},{ "Buffer folder path doesn't exist".error; });

		if ((sfNames.size > 0).not, {
			"No AIFF or WAV files found in buffer folder".error
		});

		server ?? { server = Server.default };

		server.waitForBoot({
			synthlib ?? {this.class.loadSynthDefs};
			server.sync;

			buffers = bufPaths.collect{ |path| CtkBuffer.playbuf(path).load };
			server.sync;

			group = CtkGroup.play(server: server);

			// allow user to not assign a monitor bus directly
			monitorBus ?? {
				monBus = CtkAudio.play(outbus.asArray.size);
				monitorBus = Array.series(monBus.numChans, monBus.bus);
			};

			internalMonBus = CtkAudio.play( monitorBus.asArray.size );
			internalAuxBus = auxBus ?? CtkAudio.play(outbus.asArray.size);

			monSynths = monitorBus.asArray.collect{ |busnum, i|
				GrainFader.synthlib[\grn_monitor].note(addAction: \tail, target: group)
				.inbus_(internalMonBus.bus + i)
				.outbus_(busnum)
				.play;
			};

			synth1 = GrainFader.synthlib[\grainJ].note(addAction: \head, target: group)
			.buffer_(buffers[0])			// default to first buffer
			.bufnum_(buffers[0].bufnum)
			.out_bus_( outbus.isKindOf(Array).if({outbus[0]}, {outbus}) )
			.out_bus_aux_( 					// outbus to aux
				case
				{internalAuxBus.isKindOf(Array)}{internalAuxBus[0]}
				{internalAuxBus.isKindOf(CtkAudio)}{internalAuxBus.bus}
			)
			.out_bus_mon_( internalMonBus )	// monitor out bus
			.balanceAmp_(1)					// default full balance on first synth, none on second synth
			.monAmp_(0)						// default mute monitor send
			.gate_(0)						// start synth but close gate
			.play;

			synth2 = GrainFader.synthlib[\grainJ].note(addAction: \head, target: group)
			.buffer_(buffers[0])
			.bufnum_(buffers[0].bufnum)
			.out_bus_(( outbus.isKindOf(Array).if({outbus[1] ?? outbus}, {outbus}) ))
			.out_bus_aux_( 					// outbus to aux
				case
				{internalAuxBus.isKindOf(Array)}{internalAuxBus[1] ?? internalAuxBus[0]}
				{internalAuxBus.isKindOf(CtkAudio)}{internalAuxBus.bus + internalAuxBus.numChans - 1 }
			)
			.out_bus_mon_( internalMonBus.bus + internalMonBus.numChans - 1 )
			.balanceAmp_(0)
			.monAmp_(0)
			.gate_(0)
			.play;


			verbSynth = outbus.asArray.collect({ |mainOutBus, i|
				GrainFader.synthlib[\verb_localin].note(addAction: \tail, target: group)
				.amp_(1)
				.outbus_(mainOutBus)
				.inbus_(
					case
					{internalAuxBus.isKindOf(Array)}{internalAuxBus[i] ?? internalAuxBus[0]}
					{internalAuxBus.isKindOf(CtkAudio)}{internalAuxBus.bus + i}
				)
				.mix_(1)
				.decayTime_(0.7) // early reflection decay
				.apDecay_(5)   // late field decay
				.scaleReflections_(1) // pack <1 or spread >1 early reflections
				.play;
			});

		});
	}

	// power balance between synth1 and synth2
	// 0 is full synth1 with no synth2
	// 1 is full synth2 with no synth1
	balance_ { |balance, lagTime = 0.05|
		balance !? {
			synth1.balance_amp_lag_(lagTime).balanceAmp_(sqrt(1-balance));
			synth2.balance_amp_lag_(lagTime).balanceAmp_(sqrt(balance));
		}
	}

	swapBuf_ { |newBufDex|
		var buf;
		buf = buffers[newBufDex];
		[synth1, synth2].do{
			|synth, i|
			(synth.recvUpdate == 1).if{
				synth.buffer_(buf).bufnum_(buf.bufnum)
			}
		}
	}

	// enable/disable monitoring of a synth
	monitor_ { |which, bool|
		switch( which,
			0, { synth1 }, 1, {synth2}
		).monAmp_(bool.asInt)
	}

	mute { [synth1, synth2].do(_.pause) }
	unmute { [synth1, synth2].do(_.run) }
	ampLag_ { |lagSecs = 0.3| [synth1, synth2].do({|me| me.amp_lag_(lagSecs)}) }

	free {
		buffers.do(_.free);
		group.freeAll;
		internalMonBus.free;
		monBus !? {monBus.free};
	}


	/* Preset/Archive Support */

	// which 0: synth1, 1: synth2, 2: both
	storePreset { |which = 0, key, overwrite =false|
		var arch, synth;

		synth = switch( which, 0, {synth1}, 1, {synth2}, 2, {[synth1,synth2]}).asArray;
		// TODO: move this to init
		arch = Archive.global[\grainFaderStates] ?? { this.prInitArchive };

		(arch[key].notNil and: overwrite.not).if {
			format("preset already exists! choose another name or first perform .removePreset(%)", key).throw
		};

		arch.put( key.asSymbol ?? {Date.getDate.stamp.asSymbol},

			IdentityDictionary( know: true ).putPairs([
				\params, IdentityDictionary( know: true ).putPairs([
                    \gate,      synth.collect{ |synth| synth.gate },
					\amp,       synth.collect{ |synth| synth.amp },
					\grainRate,	synth.collect{ |synth| synth.grainRate },
					\grainDur,	synth.collect{ |synth| synth.grainDur },
					\grainRand,	synth.collect{ |synth| synth.grainRand },
					\posDisp,   synth.collect{ |synth| synth.posDisp },
					\pitch,     synth.collect{ |synth| synth.pitch },
					\posRate,   synth.collect{ |synth| synth.posRate },
					\posInv,    synth.collect{ |synth| synth.posInv },
					\start,     synth.collect{ |synth| synth.start },
					\end,       synth.collect{ |synth| synth.end },
					\auxmix,    synth.collect{ |synth| synth.auxmix },
				]),
				// recalling these vars depend on whether 1 or both synths are recalled
				\balanceAmp,   synth.collect{|synth| synth.balanceAmp },
				\fileName,     synth.collect{|synth| PathName(synth.buffer.path).fileName },
				\numStored,    synth.size,
			]);
		);

		lastUpdated = key;

		postf("Preset Stored\n%\n", key);
		arch[key].fileName.postln;
		arch[key].params.keysValuesDo{|k,v| [k,v].postln;}
	}


	updatePreset {
		lastUpdated.notNil.if({
			this.storePreset( lastRecalledSynthDex, lastUpdated, true );
			},{
			"last updated key is not known".warn
		});
	}


	removePreset { |key|

		Archive.global[\grainFaderStates][key] ?? {
			format("preset % not found!", key).error
		};

		Archive.global[\grainFaderStates].removeAt(key)
	}


	// variance:	vary the synth params by variance value, typically 0 > 1
	// whichSynth:	nil - opposite the last recalled, 0 or 1 - (left or right on interface)
	// cueOnly:		true - the balance isn't changed, fadeTime ignored; false - balance fades to the new preset
	recallPreset { |key, fadeTime = 2, variance = 0.0, cueOnly = true, whichSynth|
		var preset, numStored, test1, test2, updateSynth, recallDex;

		preset = Archive.global[\grainFaderStates][key];
		preset ?? {"preset not found!".error};
		numStored = preset[\numStored];

		// test that buffer(s) exist
		test1 = preset[\fileName].collect{ |name| File.exists( bufFolderPath ++ name ) };
		test2 = test1.includes(false).not;
		if(test2.not) {format("one or both soundfiles not found! Synth buf found?: %\n", test2).error};

		// choose which synth is going to be updated by this preset
		// first follow whichSynth arg, if nil follow recvUpdate state of the synths,
		// if neither, do the opposite of the last updated
		updateSynth = if( whichSynth.notNil,
			{
				recallDex = lastRecalledSynthDex = whichSynth;
				this.prToggleUpdateSynth( recallDex, 1 );
				[synth1, synth2].at(whichSynth); // return
			},{
				var receiveStates, numReceivers;

				receiveStates = [synth1, synth2].collect(_.recvUpdate).asInt;
				numReceivers = receiveStates.occurrencesOf(1);

				case
				// one synth is selected to receive update
				{ numReceivers == 1 } {
					recallDex = lastRecalledSynthDex = receiveStates.indexOf(1);
					[synth1, synth2].at( recallDex ); // return
				}
				// both or neither no synth selected to receive update
				{ (numReceivers == 0) or: (numReceivers == 2) } {
					switch( numStored,
						1, {
							recallDex = (lastRecalledSynthDex - 1).abs;
							lastRecalledSynthDex = recallDex;
							[synth1, synth2].at( recallDex ); // return
						},
						2, {
							lastRecalledSynthDex = 2;
							[synth1, synth2] // return
						}
					)
				}

			}
		).asArray;

		// recall the buffer(s)
		preset[\fileName].do{
			|name, i|
			var buf, bufnum;
			buf = buffers.select({
				|ctkbuf|
				ctkbuf.path == (bufFolderPath ++ name) }).at(0);
			bufnum = buf.bufnum;
			updateSynth[i].buffer_(buf).bufnum_(bufnum);
		};

		{	// fork
			preset[\params].keysValuesDo({ |param, val|
				updateSynth.do{
					|synth, i|
					var vary, target;
					vary = rrand(variance.half.neg, variance.half) + 1;
					target =
					if( // don't add variance to these params
						(param != 'amp') and:
						(param != 'posInv') and:
						(param != 'gate'),
						{val.at(i) * vary},
						{val.at(i)}
					);

					// postf("setting dex %, % % % to %\n", recallDex, synth, synth.node, param, target);
					synth.set(0.0, param.asSymbol, target);
					synth.args[param.asSymbol] = target;

					// update touch osc gui
					try {
						this.updateGUI( param.asSymbol, target, (recallDex ?? i)+1 )
					}{
						("couldn't update GUI for " ++ param.asSymbol).warn
					};
					0.05.wait;
				};
			});

			if( cueOnly.not, {
				0.2.wait;
				// update balance between synths
				switch( numStored,
					1, {
						[synth1, synth2].at(recallDex).balance_amp_lag_(fadeTime).balanceAmp_(1);
						[synth1, synth2].at((recallDex - 1).abs).balanceAmp_(0);
						this.updateGUI( 'xfade', recallDex );
					},
					2, {
						preset[\balanceAmp].do{
							|balAmp, i|
							[synth1, synth2].at(i)
							.balance_amp_lag_(fadeTime)
							.balanceAmp_(balAmp) };
						this.updateGUI( 'xfade', 1 - preset[\balanceAmp][0].squared );
					}
				);
			});
		}.fork(AppClock);

		lastUpdated = key;
	}

	archive { ^Archive.global[\grainFaderStates] }
	presets { ^Archive.global[\grainFaderStates] }
	listPresets { ^this.presets.keys.asArray.sort.do(_.postln) }


	backupPreset { this.class.backupPreset }

	*backupPreset {
		Archive.write(format("~/Desktop/archive_GrainFaderBAK_%.sctxar",Date.getDate.stamp).standardizePath)
	}

	prInitArchive {
		^Archive.global.put(\grainFaderStates, IdentityDictionary(know: true));
	}

	// find preset name matches, display them in columns by "categories"
	// e.g. "trumpet" would match 'trumpet01', 'trumpet02', 'trumpet_noisy', etc...
	// and create a "trumpet" column in the preset window
	presetGUIbyCategory { |nameArray|
		var ftBox, varBox, msg_Txt, presetLayouts, categoryNames, groupedPresets, maxGroupSize;

		categoryNames = [];
		maxGroupSize = 0;
		groupedPresets = nameArray.collect(_.asString).collect({ |category|
			this.presets.keys.asArray.select{|name| name.asString.contains(category.asString) }
		});
		// alphabetize the resultant groups
		groupedPresets = groupedPresets.collect{ |group| group.sort };

		groupedPresets.do{
			|group|
			if (group.size > maxGroupSize, {
				maxGroupSize = group.size
			})
		};

		presetLayouts = groupedPresets.collect({ |nameSet, j|
			// catch non-matches and warn
			(nameSet.size == 0).if{warn(format("No matches found for %", nameArray[j]))};

			categoryNames = categoryNames.add(nameArray[j]);

			// the column layout
			VLayout(

				VLayout(
					*nameSet.extend(maxGroupSize, nil).collect({ |name|
						var lay;
						name.notNil.if({
							lay = HLayout(

								[ Button().states_([[name]])
									.action_({
										var whichSynth;

										// make sure a synth is set to receive updates
										[synth1, synth2].do({
											|synth, i|
											synth.recvUpdate.asInt.asBoolean.if({
												whichSynth.isNil.if(
													{	whichSynth = i },
													{
														whichSynth = nil;
														"More than one synth set to recall this preset".error
													}
												)
											})
										});

										whichSynth.notNil.if({

											this.recallPreset(name.asSymbol, ftBox.value, varBox.value, true, whichSynth);
											msg_Txt.string_(format("Synth % recalled %.", whichSynth+1, name)).stringColor_(Color.black);
										},{
											msg_Txt.string_("Select one synth to recall a preset").stringColor_(Color.red);
											"No (or both) synth(s) selected to recall this preset".error;
										});
								}), a: \top]

							)
						},{
							nil
						}
						)

					})
				)

			)
			// returns column layout to be collected
		});

		presetWin = Window("Grain Presets", Rect(0,0,100,100)).view.layout_(
			VLayout(
				[Button().states_([["Mute", Color.black, Color.grey],["Muted", Color.white, Color.red]]).action_({ |but|
					switch( but.value,
						0, {this.unmute},
						1, {this.mute}
					)
				}).maxWidth_(70).fixedHeight_(35), a: \right],
				HLayout(
					nil,
					StaticText().string_("Variance").align_(\right).fixedHeight_(25),
					varBox = NumberBox().value_(0.0).maxWidth_(35).fixedHeight_(25),
					StaticText().string_("Fade Time").align_(\right).fixedHeight_(25),
					ftBox = NumberBox().value_(1.0).maxWidth_(35).fixedHeight_(25)
				),
				HLayout(
					msg_Txt = StaticText().string_("Select a synth to update.").fixedHeight_(35),
					Button().states_([["Update Preset"]]).action_({this.updatePreset}).fixedWidth_(95)
				),
				// column title
				HLayout(
					*categoryNames.collect({ |name|
						StaticText().string_(name).maxHeight_(35).align_(\center)
					})
				),
				HLayout( *presetLayouts )
			)
		).front;
	}

	presetGUI { |numCol=1|
		var presetsClumped, ftBox, varBox, msg_Txt, presetLayouts, maxRows;
		maxRows = (this.presets.size / numCol).ceil.asInt;

		presetsClumped = this.presets.keys.asArray.sort.clump(maxRows);

		presetLayouts = presetsClumped.collect({ |presetGroup|
			VLayout(
				*presetGroup.extend(maxRows,nil).collect({ |name, i|
					var lay;
					name.notNil.if({
						lay = HLayout(
							[ Button().states_([[name]])
								.action_({
									var whichSynth;

									// make sure a synth is set to receive updates
									[synth1, synth2].do({
										|synth, i|
										synth.recvUpdate.asInt.asBoolean.if({
											whichSynth.isNil.if(
												{	whichSynth = i },
												{
													whichSynth = nil;
													"More than one synth set to recall this preset".error
												}
											)
										})
									});

									whichSynth.notNil.if({

										this.recallPreset(name.asSymbol, ftBox.value, varBox.value, true, whichSynth);
										msg_Txt.string_(format("Synth % updated.", whichSynth+1)).stringColor_(Color.black);
									},{
										msg_Txt.string_("Select one synth to recall a preset").stringColor_(Color.red);
										"No (or both) synth(s) selected to recall this preset".error
									});
							}), a: \top]
						)
					},{
						nil
					})
				})
			)
		});

		presetWin = Window("Grain Presets", Rect(0,0,100, 100)).view.layout_(
			VLayout(
				[ Button().states_([["Mute", Color.black, Color.grey],["Muted", Color.white, Color.red]]).action_({ |but|
					switch( but.value,
						0, {this.unmute},
						1, {this.mute}
					)
				}).maxWidth_(70).fixedHeight_(35), a: \right],
				HLayout(
					nil,
					StaticText().string_("Variance").align_(\right).fixedHeight_(25),
					varBox = NumberBox().value_(0.0).maxWidth_(35).fixedHeight_(25),
					StaticText().string_("Fade Time").align_(\right).fixedHeight_(25),
					ftBox = NumberBox().value_(1.0).maxWidth_(35).fixedHeight_(25)
				),
				HLayout(
					msg_Txt = StaticText().string_("Select a synth to update.").fixedHeight_(35),
					Button().states_([["Update Preset"]]).action_({this.updatePreset}).fixedWidth_(95)
				),
				HLayout( *presetLayouts )
			)
		).front;
	}



	// ------------------------------------------------------
	// TouchOSC control and mapping
	// ------------------------------------------------------

	// private, only called by touchOSC gui
	prToggleUpdateSynth { |synthDex, updateState|
		fork{
			// update the state of the de/selected synth
			[synth1, synth2].at(synthDex).recvUpdate_(updateState);
			this.updateGUI( 'recvUpdate', updateState, synthDex+1);

			// if turning synthDex on, turn the other off
			(updateState == 1).if{
				[synth1, synth2].at((synthDex-1).abs).recvUpdate_(0);
				0.05.wait;
				this.updateGUI( 'recvUpdate', 0, (synthDex-1).abs+1);
			};
		}
	}

	connectTouchOSC { |aTouchOsc, numSfColumns = 4, numSfRows = 4|
		var col = 0, sfPage = 0, numSfPages, sfNamesByPage;

		myTouchOsc = aTouchOsc;
		/* map controls first */

		// global controls
		aTouchOsc.addCtl( \monAmp,		\fader,		'/1/monAmp',		ControlSpec(-90, 8, -4, 0, -90));
		aTouchOsc.addCtl( \xfade,		\fader,		'/1/xfade',			ControlSpec(0, 1, \lin, 0,0));

		// granulation controls
		[1,2].do{ |i|
			aTouchOsc.addCtl( \gate++i,			\toggle,	'/1/gate'++i);
			aTouchOsc.addCtl( \amp++i,			\fader,		'/1/amp'++i,		ControlSpec(-90, 8, -4, 0, -90));
			aTouchOsc.addCtl( \grainRate++i,	\fader, 	'/1/grainRate'++i,	ControlSpec(0.2, 100.0, 5, 0, 10.0, "grn/sec"));
			aTouchOsc.addCtl( \grainDur++i,		\fader, 	'/1/grainDur'++i,	ControlSpec(0.02, 3.0, 4, 0, 0.5, "sec"));
			aTouchOsc.addCtl( \grainRand++i,	\fader, 	'/1/grainRand'++i,	ControlSpec(0.0, 1.0, \lin, 0, 0));
			aTouchOsc.addCtl( \posDisp++i,		\fader, 	'/1/posDisp'++i,	ControlSpec(0.0, 0.9, \lin, 0, 0));
			aTouchOsc.addCtl( \pitch++i,		\fader, 	'/1/pitch'++i,		ControlSpec(0.3, 8.0, 3, 0, 1));
			aTouchOsc.addCtl( \posRate++i,		\fader,		'/1/posRate'++i,	ControlSpec(0.0001, 5.0, 3, 0.0001, 1.0));
			aTouchOsc.addCtl( \start++i,		\fader,		'/1/start'++i,		ControlSpec(0.0, 1.0, \lin, 0, 0.0));
			aTouchOsc.addCtl( \end++i,			\fader, 	'/1/end'++i,		ControlSpec(0.0, 1.0, \lin, 0, 1.0));
			aTouchOsc.addCtl( \auxmix++i,		\fader,		'/1/auxmix'++i,		ControlSpec(0.0, 1.0, \lin, 0, 0), "aux");
			aTouchOsc.addCtl( \posInv++i,		\toggle,	'/1/posInv'++i,     ControlSpec(0.0, 1.0, \lin, 0, 0) );
			aTouchOsc.addCtl( \mon++i,			\toggle,	'/1/mon'++i );
			aTouchOsc.addCtl( \recvUpdate++i,	\toggle,	'/1/recvUpdate'++i );
			aTouchOsc.addCtl( \posReset++i,		\push,		'/1/posReset'++i );
		};

		/*// soundfiles VERSION 1
		sfNames.do{|name, i|
			var row;
			row = (i % numSfRows);
			if( row == 0, { col = col+1 });

			aTouchOsc.addCtl( \sf++i,  \multipush,
				// just using one column per multipush
				format( "/1/multipush%/%/1", col, row +1 ).asSymbol,
				label: name, postValue: false
			);
		};*/

		numSfPages = (sfNames.size / (numSfRows * numSfColumns)).ceil;

		// clump names into groups as large as a sf list "page"
		sfNamesByPage = sfNames.clump(numSfRows * numSfColumns);
		// sfBufsByPage = sfNames.clump(numSfRows * numSfColumns);

		// initialize controls with labels for first set of soundfiles
		(numSfRows * numSfColumns).do{ |i|
			var row = (i % numSfRows);
			// row = numSfRows - (i % numSfRows);
			if( row == 0, { col = col+1 });

			aTouchOsc.addCtl( \sf++i,  \multipush,
				// just using one column per multipush
				// rows in TouchOSC are numbered bottom to top, 1-based
				format( "/1/multipush%/%/1", col, numSfRows - row ).asSymbol,
				label: sfNames[i], postValue: false
			);
		};
		// next/prev page turn ctl
		2.do{ |i|
			aTouchOsc.addCtl( \sfPageTurn++i,  \multipush,
				// just using one column per multipush
				format( "/1/multipushPageTurn/1/%", i+1 ).asSymbol
			);
		};


		/* then connect controls */

		// connect individual synth settings
		[synth1, synth2].do{
			|synth, i|
			var j = i+1;
			aTouchOsc.connect( synth,
				//CTK bug - need to use function so getter works
				// \recvUpdate++j,{|obj, val| obj.recvUpdate_(val) },
				\recvUpdate++j, {|obj, val| this.prToggleUpdateSynth(i, val) },
				\gate++j,		{|obj, val| obj.gate_(val) },		//\gate,
				\amp++j,		{|obj, val| obj.amp_(val.dbamp) },
				\grainRate++j,	{|obj, val| obj.grainRate_(val) },	//\grainRate
				\grainDur++j,	{|obj, val| obj.grainDur_(val) },	//\grainDur,
				\grainRand++j,	{|obj, val| obj.grainRand_(val) },	//\grainRand,
				\posDisp++j, 	{|obj, val| obj.posDisp_(val) },	//\posDisp,

				\pitch++j,		{|obj, val|
					if( (val - pitchSnapVal).abs < pitchSnapThresh ) // snap to 1 within this threshold
					{	obj.pitch_(pitchSnapVal);
						this.updateGUI( \pitch, pitchSnapVal, i+1 );
					}
					{ obj.pitch_(val) };
				},

				\posRate++j,	{|obj, val|
					if( (val - rateSnapVal).abs < rateSnapThresh ) // snap to 1 within this threshold
					{ 	obj.posRate_(rateSnapVal);
						this.updateGUI( \posRate, rateSnapVal, i+1 );
					}
					{ obj.posRate_(val) };
				},

				\posInv++j,		{|obj, val| obj.posInv_(val) }, 	// \posInv,
				\posReset++j,	{|obj, val| (val==1).if{ obj.t_posReset_(1)} }, 	// \posReset,
				\start++j,		{|obj, val| obj.start_(val) },	//\start,
				\end++j,		{|obj, val| obj.end_(val) },	//\end,
				\auxmix++j,		{|obj, val| obj.auxmix_(val) },	//\auxmix,
				\mon++j,		{|obj, val| obj.monAmp_(val) },	//\monAmp, 0 or 1 (mute)
			)
		};

		// connect global settings to the GrainFader object
		aTouchOsc.connect( this,
			\monAmp,		{|obj, val| obj.monSynths.do({ |me| me.amp_(val.dbamp) }) },
			\xfade,			{|obj, val| obj.balance_(val, 0.3) },
		);

		// VERSION 1 // connect soundfile swapping controls
		// sfNames.do{ |name, i|
		// 	aTouchOsc.connect( this,
		// 		\sf++i,	{|obj, val|
		// 			(val.asInt == 1).if{ obj.swapBuf_( i ) }
		// 		}
		// 	)
		// };

		// connect soundfile swapping controls
		(numSfRows * numSfColumns).do{ |i|
			aTouchOsc.connect( this,
				\sf++i,	{|obj, val|
					(val.asInt == 1).if{
						obj.swapBuf_(
							// todo: check if this is a valid buffer num
							((numSfRows * numSfColumns) * sfPage) + i
						);
					}
				}
			)
		};

		// next/prev page turn ctl
		2.do{ |i|
			aTouchOsc.connect( this,
				\sfPageTurn++i,	{ |obj, val|
					(val.asInt == 1).if{
						sfPage = switch( i,
							// prev
							0, { (sfPage -1) % numSfPages; },
							// next
							1, { (sfPage +1) % numSfPages; }
						);
						// update sf name labels
						(numSfRows * numSfColumns).do{ |j|
							var name;
							try { name = sfNamesByPage[sfPage][j] };
							aTouchOsc.devRcvAddr.sendMsg(
								aTouchOsc.controls[(\sf++j).asSymbol].labelTag,
								name.notNil.if({name.asString},{"..."});
							);
						}
					}
				}
			)
		};
	}

	// note: synthNum is 1-based, not 0-based
	updateGUI { | param, val, synthNum|
		myTouchOsc !? {
			var ctlKey;

			(param == 'amp').if{ val = val.ampdb};

			synthNum.notNil.if(
				{
					ctlKey = param++synthNum;

					myTouchOsc.devRcvAddr.sendMsg( ('/1/'++ctlKey).asSymbol,
						myTouchOsc.controls[(param++synthNum).asSymbol].spec.unmap(val).round(0.001));
					myTouchOsc.devRcvAddr.sendMsg( ('/1/'++ctlKey++'_V').asSymbol, val.round(0.001) );
				},{
					myTouchOsc.devRcvAddr.sendMsg( ('/1/'++param).asSymbol,
						myTouchOsc.controls[param.asSymbol].spec.unmap(val).round(0.001));
					myTouchOsc.devRcvAddr.sendMsg( ('/1/'++param++'_V').asSymbol, val.round(0.001));
				}
			)
		};
	}

	*loadSynthDefs {

		synthlib = CtkProtoNotes(

			SynthDef(\grainJ, {
				arg
				buffer, bufnum,
				out_bus, 			// main out
				out_bus_aux,		// outbus to reverb
				out_bus_mon, 		// headphone out bus
				start=0, end=1,		// bounds of grain position in sound file
				grainRand = 0,		// gaussian trigger: 0 = regular at grainRate, 1 = random around grainRate
				grainRate = 10, grainDur = 0.04,
				posDisp = 0.01,	// position dispersion of the pointer, in seconds
				pitch=1,
				auxmix=0, amp=1,
				fadein = 2, fadeout = 2,
				// minRange = 0.01,	// min soundfile pointer, as a percentage of 0>1
				balanceAmp = 1,
				posRate = 1,	// change the speed of the grain position pointer (can be negative)
				posInv = 0,		// flag (0/1) to invert the posRate
				monAmp = 1,		// monitor amp, for headphones
				amp_lag = 0.3,		// time lag on amplitude changes (amp, xfade, mon send, aux send)
				balance_amp_lag = 0.3,	// time lag on amplitude xfade changes
				recvUpdate = 0,		// flag to check if next selected buffer is to be input to this instance
				t_posReset = 0,		// reset the phasor position with a trigger
				gate = 1;			// gate to start and release the synth

				var
				env, grain_dens, amp_scale, trig, b_frames,
				pos, pos_lo, pos_hi, sig, out, aux, auxmix_lagged;

				// envelope for fading output in and out
				env = EnvGen.kr(Env([0,1,0],[fadein, fadeout], \sin, 1), gate, doneAction: 0);

				// calculate grainRate
				grain_dens = grainRate * grainDur;

				amp_scale = grain_dens.reciprocal.clip(0, 1);

				// lag the amp_scale, we do it more in its way up
				// amp_scale = LagUD.kr(amp_scale,	grainDur * 100, grainDur);

				// gaussian trigger
				// grainRand = 0 regular at grainRate
				// grainRand = 1 random around grainRate
				trig = GaussTrig.ar(grainRate, grainRand);

				// use line to go from start to end in buffer
				b_frames = BufFrames.kr(bufnum);

				pos = Phasor.ar( t_posReset,
					BufRateScale.kr(bufnum) * posRate * (1 - (posInv*2)),
					b_frames * start, b_frames * end, b_frames * start
				);
				pos = pos * b_frames.reciprocal;

				pos_lo = posDisp * 0.5.neg;
				pos_hi = posDisp * 0.5;

				// add randomness to position pointer, make sure it remains within limits

				pos = pos + TRand.ar(pos_lo, pos_hi, trig);
				pos = pos.wrap(start , end);

				// sig = GrainBufJ.ar(1, trig, grainDur, buffer, pitch , pos, 1, interp, grainAmp: amp_scale);
				sig = GrainBufJ.ar(1, trig, grainDur, buffer, pitch , pos, 1, interp:4, grainAmp: amp_scale);
				sig = sig * Lag.kr(amp, amp_lag) * env;

				Out.ar( out_bus_mon, sig * Lag.kr(monAmp, amp_lag) ); // out to headphones, independent monAmp mute flag

				sig = Limiter.ar(sig, 0.95) * VarLag.kr( balanceAmp, balance_amp_lag);

				auxmix_lagged = Lag.kr(auxmix, amp_lag);
				// balance between dry and wet routing
				out = sig * (1 - auxmix_lagged).sqrt;
				aux = sig * auxmix_lagged.sqrt;

				// send signals to outputs
				Out.ar( out_bus,		out );
				Out.ar( out_bus_aux,	aux );

			}),

			SynthDef(\verb_localin, {
				arg outbus, inbus, revTime = 3, decayTime = 2, mix = 0.5, apDecay = 0.2, scaleReflections = 1, amp = 1; //, apDelay = 0.095;
				var src, combDels, g, lIn, lOut, delay, combs, ap, out;

				// var apDecay = 0.2; //2.0;
				var apDelay = 0.095; //0.095;
				var apOrder = 6;

				// src = Decay.ar(Impulse.ar(0.57, 0.25), 0.2, PinkNoise.ar, 0);
				src = In.ar(inbus, 1);

				combDels = [0.0297, 0.0371, 0.0411, 0.0437] * scaleReflections;

				// calculate feedback coefficient
				g = 10.pow(-3 * combDels / decayTime);

				lIn = LocalIn.ar(4);

				combs = DelayC.ar(src + (lIn * g),
					// combDels.maxItem - ControlRate.ir.reciprocal,
					0.5 - ControlRate.ir.reciprocal,
					combDels - ControlRate.ir.reciprocal
				);

				combs = LPF.ar(combs, 1800); // damping

				combs = LeakDC.ar(combs);

				lOut = LocalOut.ar(combs);

				ap = combs.sum;
				apOrder.do({ |i|
					ap = AllpassC.ar( ap,
						2.0, //apDelay,
						apDelay.rand * LFTri.kr( rrand(8,17.0).reciprocal ).range(0.9, 1), // mod delays a bit
						apDecay);
				});

				delay = DelayN.ar(src, ControlRate.ir.reciprocal, ControlRate.ir.reciprocal); // make up delay

				out = (mix.sqrt * ap) + ((1 - mix).sqrt * delay);

				Out.ar(outbus, out * Lag.kr(amp, 0.3))
			}),

			SynthDef(\grn_monitor, { arg outbus, inbus, amp=1;
				Out.ar( outbus, In.ar(inbus) * Lag.kr(amp, 0.2) );
			})
		)
	}

}

/*

// create a bus to send your GrainFader for processing, reverb, etc
~auxBus = CtkAudio.play(1)


// send the GrainFader output to 0 -the first hardware out-
// monitor on bus 2 (or whichever bus is routed to your headphones/monitor)
x = GrainFader( outbus: 0, monitorBus: 2, auxBus: ~auxBus,
	bufFolderPath: "/Users/admin/Documents/Recordings/test/"
)

// creat an instance of TouchOSC, make sure you've
// loaded the grain_instr template, see helpfile as needed
t = TouchOSC("169.254.50.164", 9000)

// connect the TouchOSC to the GrainFader
x.connectTouchOSC(t)

// clear the controls, freeing the listening responders (including Spec mappings)
t.clearControls
// or - disconnect the interface from your synth/object, but retain the conrol mappings
t.disconnectAll

// cleanup everything
x.free
*/

/*
// crossfading grain triggers
{
	// TTendency.kr( trig, 4, 0, 1.0, tendLow, tendHi );
	var imp;
	imp = Impulse.kr(30);
	Out.kr(0, [
		TTendency.kr(
			imp, 4, 0, 1.0, Line.kr(0, 1, 10), Line.kr(1, 0, 10) ).round,
		imp
	]);
}.play
s.scope(1, rate: 'control')
*/