GrainScanner2 {
	classvar <grnSynthDef, <>replyIDCnt = 0;

	// copyArgs
	var <outbus, initSfPath;
	var server, <scanners, <sf, <buffers, <grnGroup, <synths, <bufDur, <view, <bufferPath;
	var <grnDurSpec, <grnRateSpec, <grnRandSpec, <grnDispSpec, <distFrmCenSpec, <clusterSpreadSpec, <azSpec, <xformAmtSpec, <ampSpec;
	var <dataDir, loadSuccess = true, <curDataSet;
	var <spreadSpec, <panSpec, <lastRecalledPreset, <presetWin;
	var <curCluster, <numFramesInClusters, <numClusters, <clusterFramesByDist, <invalidClusters, setClusterCnt = 0;

	*new { |outbus=0, sfPath|
		^super.newCopyArgs( outbus, sfPath ).init;
	}

	init {
		block { |break|
			server = Server.default;
			server.serverRunning.not.if{warn("Server isn't running, won't start grain scanner"); break.()};

			// init presets
			Archive.global[\grainScanner2] ?? { this.prInitArchive };

			// grain specs
			grnDurSpec = ControlSpec(0.01, 8, warp: 3, default: 1.3);
			grnRateSpec = ControlSpec(4.reciprocal, 70, warp: 3, default:10);
			grnRandSpec = ControlSpec(0, 1, warp: 4, default: 0.0);
			grnDispSpec = ControlSpec(0, 5, warp: 3, default: 0.03);
			ampSpec = ControlSpec(-inf, 16, warp: 'db', default: 0.0);

			// cluster specs
			clusterSpreadSpec = ControlSpec(0, 0.5, warp: 3, default: 0.05);
			distFrmCenSpec = ControlSpec(0, 1, warp: 0, default: 0.1);

			// space specs
			panSpec = ControlSpec(-1, 1, warp: 0, default: 0.0);
			spreadSpec = ControlSpec(0, 1, warp: 'db', default: 0.25);

			fork{
				var cond = Condition();

				this.class.grnSynthDef ?? { this.loadGlobalSynthLib; 0.2.wait; };
				server.sync;

				this.prepareNewDataSet(initSfPath, cond);
					// this.setBuffer(bufferOrPath, cond);
				cond.wait;

				grnGroup = CtkGroup.play;
			}
		}
	}

	setBuffer{ |bufferOrPath, finishCond|
		fork{
			var cond = Condition(), result;
			case
			{ bufferOrPath.isKindOf(String) }{
				bufferPath = bufferOrPath;
				result = this.prepareBuffers(cond);
				cond.wait;
			}
			// this assumes an array of mono buffers from the same file path
			{ bufferOrPath.isKindOf(Array) }{
				var test;
				test = bufferOrPath.collect({|elem| elem.isKindOf(Buffer)}).includes(false);
				test.if{ "one or more elements in the array provided is not a buffer".error };
				buffers = bufferOrPath;
				bufferPath = buffers[0].path;
				result = true;
			}
			{ bufferOrPath.isKindOf(Buffer) }{
				case
				// use the mono buffer
				{ bufferOrPath.numChannels == 1 }{
					"using mono buffer provided".postln;
					bufferPath = bufferOrPath.path;
					buffers = [bufferOrPath];
					result = true;
				}
				// split into mono buffers
				{ bufferOrPath.numChannels > 1 }{
					"loading buffer anew as single channel buffers".postln;
					bufferPath = bufferOrPath.path;
					result = this.prepareBuffers(cond);
					cond.wait;
				}
			};

			result.notNil.if({
				bufDur = buffers[0].duration;
				scanners.do(_.updateBuffer(buffers));
				dataDir ?? { dataDir = PathName(buffers[0].path).pathOnly }; // set dataDir if it hasn't yet been set;
			}, {
				warn("There was a problem loading the buffer(s)");
				loadSuccess = false;
			});

			finishCond !? {finishCond.test_(true).signal};
		}
	}

	prepareBuffers { |finishCond|

		// check the soundfile is valid and get it's metadata
		sf = SoundFile.new;
		{sf.openRead(bufferPath)}.try({
			"Soundfile could not be opened".warn;
			finishCond.test_(true).signal;
			^nil // break
		});
		sf.close;

		// load the buffers
		fork {
			// one condition for each channel loaded into a Buffer
			var bufLoadConds = [];
			buffers = sf.numChannels.collect{|i|
				var myCond = Condition();
				bufLoadConds = bufLoadConds.add(myCond);
				Buffer.readChannel(
					server, bufferPath,
					channels: i, action: {myCond.test_(true).signal} );
			};
			bufLoadConds.do(_.wait); // wait for each channel to load
			"grain scanner buffer(s) loaded".postln;
			finishCond.test_(true).signal;
		}
	}

	addScanner { |initCluster=0, gui=true|
		scanners = scanners.add( GrainScan2(this, initCluster, gui) );
	}

	// mirFrames: eg. ~data.beatdata
	initClusterData { |aKMeansMod, mirFrames|
		var framesByCluster;

		numClusters = aKMeansMod.k;
		numFramesInClusters = numClusters.collect{ |i| aKMeansMod.assignments.occurrencesOf(i) };
		invalidClusters = List(); 	// keep track of clusters without frames to catch later
		numFramesInClusters.do{ |frmcnt, i| if(frmcnt == 0, { invalidClusters.add(i) }) };

		framesByCluster = numClusters.collect{List()};

		aKMeansMod.cenDistances.do{|dist, i|
			// Create an dict for each frame, associating it with its
			// distance from its assigned centroid, for later sorting
			// Put each of these frame dicts into its cluster group.
			framesByCluster[aKMeansMod.assignments[i]].add(
				().f_(mirFrames[i]).d_(dist)
			);
		};

		// sort by each frame's distance from the centroid, ordered nearest to furthest
		clusterFramesByDist = framesByCluster.collect{ |dataDictsArr|
			dataDictsArr.sortBy(\d).collect{|dict| dict.f }
		};
	}

	getReplyID {
		// for requesting instance's OSC responder
		var id = this.class.replyIDCnt;
		this.class.replyIDCnt = id + 1;
		^id
	}

	gui { scanners.do(_.gui) }


	play { |fadeTime| scanners.do(_.play(fadeTime)); }

	release { |fadeTime = 4|
		scanners.do(_.release(fadeTime));
	}

	resume { |fadeTime = 4|
		scanners.do(_.play(fadeTime));
	}


	free {
		scanners.do(_.free);
		grnGroup.freeAll;
		buffers.do(_.free);
	}

	dataDir_ { |dirPath|
		(dirPath.last != "/").if{ dirPath = dirPath ++ "/" };
		File.exists(dirPath).if(
			{ dataDir = dirPath },
			{ warn("Directory not found!") }
		);
	}

	loadSCMIRdata { |path|
		var scmirZTestFile, data;

		scmirZTestFile = PathName(path).pathOnly++PathName(path).fileNameWithoutExtension++".scmirZ";

		if ( File.exists(scmirZTestFile))
		{
			"Found SCMIR Analysis File...loading".postln;
			data = SCMIRAudioFile.newFromZ(scmirZTestFile);
			"SCMIR data loaded".postln;
			^data;
		} {
			("No SCMIR analysis found at this path: " ++ scmirZTestFile).error;
			^nil;
		};
	}

	loadKMeansData { |path|
		var kmeansTestFile, data;

		kmeansTestFile = PathName(path).pathOnly ++ PathName(path).fileNameWithoutExtension++"_KMEANS"++".scmirZ";

		if ( File.exists(kmeansTestFile)) {
			"Found KMeans Data File...loading".postln;
			data = KMeansMod().load( kmeansTestFile );
			"KMeans data loaded".postln;
			^data
		} {
			("No KMeans analysis found at this path: " ++ kmeansTestFile).error;
			^nil;
		}
	}

	prepareNewDataSet { |sfPath, finishCond|
		var scmirData, kmeansData;
		fork {
			if(curDataSet != PathName(sfPath).fileName){

				// retrieve scmir data
				scmirData = this.loadSCMIRdata(sfPath);
				// retrieve kmeans data
				kmeansData = this.loadKMeansData(sfPath);

				(scmirData.notNil and: kmeansData.notNil).if({
					var curbuffers, bufLoadCond = Condition();

					// recall the buffer
					curbuffers = buffers; // store to free later
					this.setBuffer(sfPath, bufLoadCond);
					bufLoadCond.wait;

					loadSuccess.if({
						// recall load the cluster data
						this.initClusterData(kmeansData, scmirData.beatdata);
						curDataSet = PathName(sfPath).fileName;
						// free the previous buffers
						(curbuffers.size > 0).if{curbuffers.do(_.free)};

					},{ "Could not prepare new data set.".error });
				},{ loadSuccess = false });

			} { "Data set already loaded".postln };

			finishCond !? { finishCond.test_(true).signal };
		}
	}

	// --------------------------------------------------
	/* PRESETS */
	// --------------------------------------------------

	prInitArchive {
		^Archive.global.put(\grainScanner2, IdentityDictionary(know: true));
	}

	presets { ^Archive.global[\grainScanner2] }
	listPresets { ^this.presets.keys.asArray.sort.do(_.postln) }

	*archive { ^Archive.global[\grainScanner2] }
	*presets { ^Archive.global[\grainScanner2] }
	*listPresets { ^this.class.presets.keys.asArray.sort.do(_.postln) }

	backupPreset { this.class.backupPreset }

	*backupPreset {
		Archive.write(format(
			"~/Desktop/archive_GrainScanner2BAK_%.sctxar",
			Date.getDate.stamp).standardizePath
		)
	}
	storePreset { |name, overwrite=false|
		block{ |break|
			(this.presets[name.asSymbol].notNil and: overwrite.not).if {
				warn("NOT CREATING PRESET. Preset already exists! Choose another name or explicitly overwrite with flag");
				break.()
			};

			this.presets.put( name.asSymbol,
				IdentityDictionary(know: true).putPairs([
					\bufName, PathName(buffers[0].path).fileName,

					\params, scanners.collect{ |scanner, i|
						IdentityDictionary(know: true).putPairs([
							// NOTE: these keys must match the class setters
							// so they can be recalled with this.perform(key++'_', val)
							\grnDur,		scanner.synths[0].grainDur,
							\grnRate,		scanner.synths[0].grainRate,
							\grnRand,		scanner.synths[0].grainRand,
							\grnDisp,		scanner.synths[0].grainDisp,
							\cluster,		scanner.synths[0].cluster,
							\clusterSpread,	scanner.synths[0].clusterSpread,
							\distFrmCen,	scanner.synths[0].distFrmCen,
							\pan,			scanner.synths[0].pan,
							\spread,		scanner.synths[0].spread,
							\amp,			scanner.synths[0].amp,
							\gate, 			scanner.synths[0].gate,
						]);
					}
				])
			);
			postf("Preset % stored\n", name);
		}
	}

	recallPreset { |name, fadeTime = 2|
		var preset = this.presets[name.asSymbol], scmirdata;

		fork{
			block { |break|
				preset ??	{ "Preset not found.".warn; break.() };
				dataDir ??	{ "Must set dataDir before recalling presets!".error; break.() };

				// check if the preset uses a different data set...
				if(curDataSet != preset.bufName, {
					var curbuffers, dataLoadCond = Condition();

					loadSuccess = true;
					this.prepareNewDataSet( dataDir ++ preset.bufName, dataLoadCond );
					dataLoadCond.wait;

					loadSuccess.not.if{ "Could not prepare new data set.".error; break.() };
				});

				// recall the synth settings
				fork({
					preset[\params].do{|dict, index|
						dict.keysValuesDo({ |k,v|
							scanners[index].synths.do(_.ctllag_(fadeTime));
							scanners[index].perform((k++'_').asSymbol, v);
						})
					};
					lastRecalledPreset = name.asSymbol;
				}, AppClock);
			}
		}
	}

	updatePreset {
		lastRecalledPreset !? {
			postf("Updating preset %\n", lastRecalledPreset);
			this.storePreset( lastRecalledPreset, overwrite: true )
		}
	}

	removePreset { |name|
		this.presets[name.asSymbol].notNil.if(
			{ this.presets.removeAt(name.asSymbol); postf("Preset % removed\n", name) },
			{ warn("Preset not found.. didn't remove!") }
		);
	}

	presetGUI { |numCol=1|
		var presetsClumped, ftBox, varBox, msg_Txt, presetLayouts, maxRows, nPsets, cnt;
		cnt = -1;
		nPsets = this.presets.size;
		maxRows = (nPsets / numCol).ceil.asInt;

		presetsClumped = this.presets.keys.asArray.sort.clump(maxRows);

		presetLayouts = presetsClumped.collect({ |presetGroup, j|
			VLayout(
				*presetGroup.extend(maxRows,nil).collect({ |name, i|
					var lay;
					cnt = cnt+1;
					name.notNil.if({
						lay = HLayout(
							[ Button().states_([[name, Color.black, Color.hsv(0.9-(nPsets.reciprocal*cnt*0.3),0.6,1,0.25)]])
								.action_({
									this.recallPreset(name.asSymbol, ftBox.value);
									msg_Txt.string_(format(
										"preset % updated.", name.asSymbol)).stringColor_(Color.black);
								}), a: \top ]
						)
					},{
						nil
					})
				})
			)
		});

		presetWin = Window("Presets", Rect(0,0,100, 100)).view.layout_(
			VLayout(
				[ Button().states_([
					["Play", Color.black, Color.hsv(0.55,0.65,1,0.25)],
					["Release", Color.white, Color.red.alpha_(0.4)]

				]).action_({ |but|
					switch( but.value,
						0, {this.release},
						1, {this.play}
					)
				}).maxWidth_(70).fixedHeight_(35), a: \right],
				HLayout(
					nil,
					StaticText().string_("Fade Time").align_(\right).fixedHeight_(25),
					ftBox = NumberBox().value_(1.0).maxWidth_(35).fixedHeight_(25).align_(\center)
				),
				HLayout(
					msg_Txt = StaticText().string_("Select a preset to recall.").stringColor_(Color.new255(*80!3)).fixedHeight_(35),
					Button().states_([["Update Preset", Color.black, Color.new255(*180!3)]])
					.action_({this.updatePreset}).fixedWidth_(95)
				),
				HLayout( *presetLayouts )
			)
		).background_(Color.hsv(0.55,0.3,1)).front;
	}

	loadGlobalSynthLib {
		grnSynthDef = CtkSynthDef(\grainScanner2, {
			arg outbus = 0, ctllag = 0, lagcrv = 0,
			grainRate = 2, grainDur=1.25, grainRand = 0, grainDisp = 0,
			cluster = 0, clusterSpread = 0.1, distFrmCen = 0, // bookkeeping args
			buffer, bufnum, pos = 0, replyID = -1,
			pan = 0, spread = 0.25,
			fadein = 2, fadeout = 2, amp = 1, gate = 1,
			pitchDriftPct = 0.12, pitchDriftPer = 5, bw = 1855, bpfDriftPer = 6;

			var env, trig, out, grain_dens, amp_scale, disp, dispNorm, panner;
			var gRate, gDur, gRand, gDisp, gPan, gSpread, gAmp, cSpread, dFrmCen, gPos;
			var pitchDrift, bpfFrqDrift;

			// envelope for fading output in and out - re-triggerable
			env = EnvGen.kr(Env([1,1,0],[fadein, fadeout], \sin, 1), gate, doneAction: 0);

			// TODO: wrap these up in 1 VarLag via multichannel expansion
			gRate = VarLag.kr(grainRate, ctllag, lagcrv);
			gDur = VarLag.kr(grainDur, ctllag, lagcrv);
			gRand = VarLag.kr(grainRand, ctllag, lagcrv);
			gDisp = VarLag.kr(grainDisp, ctllag, lagcrv);
			gPan = VarLag.kr(pan, ctllag, lagcrv);
			gSpread = VarLag.kr(spread, ctllag, lagcrv);
			gAmp = VarLag.kr(amp, ctllag, lagcrv);
			cSpread = VarLag.kr(clusterSpread, ctllag, lagcrv);
			dFrmCen = VarLag.kr(distFrmCen, ctllag, lagcrv);


			// gaussian trigger
			// grainRand = 0 regular at grainRate
			// grainRand = 1 random around grainRate
			trig = GaussTrig.ar( gRate, gRand );
			// trig = Impulse.ar(grainRate);

			dispNorm = gDisp * 0.5 * BufDur.kr(bufnum).reciprocal;
			disp = TRand.ar(dispNorm.neg, dispNorm, trig);

			// calculate grain density
			grain_dens = gRate * gDur;
			amp_scale = grain_dens.reciprocal.sqrt.clip(0, 1);

			SendReply.ar(trig, '/pointer', [cSpread, dFrmCen, gDur], replyID);

			gPos = (pos + disp).wrap(0,1);
			panner = WhiteNoise.kr(gSpread, gPan).wrap(-1,1);

			pitchDrift = LFDNoise3.kr(pitchDriftPer.reciprocal, pitchDriftPct);

			out = GrainBufJ.ar(
				4, //1, // pan to multiple channels
				trig, gDur, buffer, 1+ pitchDrift,//1,
				gPos,
				1, interp:1, grainAmp: amp_scale,
				pan: panner
			);
			//out = GrainBuf.ar(numChannels: 4,trigger: trig, dur: gDur,sndbuf: buffer, rate: 1, pos: gPos, interp: 1, pan: panner, mul: amp_scale);

			bpfFrqDrift = LFDNoise3.kr(bpfDriftPer.reciprocal).range(100, 1000);
			// sweeping BPFs
			out = Mix.ar(
				5.collect{ |i|
					var frq;
					// frq=mod*(i+1*3);
					frq= if(i==0,{bpfFrqDrift},{bpfFrqDrift*(i*4)});
					// Formlet.ar(in, mod*(i+1*3), att, dec)
					BPF.ar(out, frq, bw/frq)
				}
			);

			out = out * env;
			// out = Pan2.ar(out);
			Out.ar( outbus, out * gAmp );
		});
	}
}

// a single scanner created by GrainScanner2
GrainScan2 {
	// copyArgs
	var <master, <curCluster, initGUI;
	var <replyID, <synths, <grnResponder, setClusterCnt = 0, numFramesInCluster, clusterFramesByDist, <view, <bufDur, <playing = false;
	*new { |aGrainScanner, clusterNum = 0, initGUI=true|
		^super.newCopyArgs(aGrainScanner, clusterNum, initGUI).init;
	}

	init {

		master.getReplyID;
		this.cluster_(curCluster); // set the cluster data
		bufDur = master.buffers[0].duration;
		this.buildResponder;

		synths = master.buffers.collect{ |buf, i|
			master.class.grnSynthDef.note(addAction: \head, target: master.grnGroup)
			// .outbus_(outbus+i)
			// .outbus_(panBus)
			.outbus_(master.outbus)
			.buffer_(buf).bufnum_(buf.bufnum)
			.grainRate_(master.grnRateSpec.default)
			.grainDur_(master.grnDurSpec.default)
			.grainRand_(master.grnRandSpec.default)
			.grainDisp_(master.grnDispSpec.default)
			.cluster_(curCluster)
			.clusterSpread_(0.1)
			.distFrmCen_(0)
			.pos_(0).replyID_(replyID)
			.gate_(1)
		};

		initGUI.if{ this.gui };
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	/* cluster controls */

	// see also .chooseClusterFrame, which is called by the responder

	// spread: 0 > ~0.5
	// 0 no random spread from distFrmCen point
	// ~0.5 effectively random distribution across the cluster
	clusterSpread_ { |spread|
		synths.do(_.clusterSpread_(spread)); this.changed(\clusterSpread, spread) }

	// dist: 0 > 1
	// 0 center of the centroid
	// 1 furthest distance from centroid in the cluster
	distFrmCen_ { |dist|
		synths.do(_.distFrmCen_(dist)); this.changed(\distFrmCen, dist) }

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	/* grain controls */

	play { |fadeInTime|
		fadeInTime !? {synths.do(_.fadein_(fadeInTime))};
		synths.do({|synth|
			synth.isPlaying.not.if(
				{synth.play},
				{synth.gate_(1); synth.run}
			);
		});
		this.changed(\gate, 1);
		playing = true;
	}

	release { |fadeOutTime|
		fadeOutTime !? { synths.do(_.fadeout_(fadeOutTime)) };
		synths.do({|synth|
			synth.isPlaying.not.if(
				{ "synth isn't playing, can't release".warn },
				{ synth.gate_(0) })
		});
		this.changed(\gate, 0);
		playing = false;
		// pause after fadetime
		fork{ synths[0].fadeout.wait; playing.not.if{synths.do(_.pause)} };
	}

	gate_ { |gate| synths.do(_.gate_(gate)); this.changed(\gate, gate); }

	grnDur_ { |dur| synths.do(_.grainDur_(dur)); this.changed(\grnDur, dur); }

	grnRate_ {|rateHz| synths.do(_.grainRate_(rateHz)); this.changed(\grnRate, rateHz); }

	// gaussian trigger: 0 = regular at grainRate, 1 = random around grainRate
	grnRand_ {|distro| synths.do(_.grainRand_(distro)); this.changed(\grnRand, distro); }

	// position dispersion of the pointer, in seconds
	grnDisp_ {|dispSecs|
		synths.do(_.grainDisp_(dispSecs)); this.changed(\grnDisp, dispSecs); }

	amp_ {|amp| synths.do(_.amp_(amp)); this.changed(\amp, amp); }

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	/* space controls */
	// az_ { |azRad| xformSynth.az_(azRad); this.changed(\az, azRad); }
	// xformAmt_ { |amtRad| xformSynth.xformAmt_(amtRad); this.changed(\xformAmt, amtRad); }
	pan_ { |az| // -1, 1
		synths.do(_.pan_(az)); this.changed(\pan, az);
	}
	spread_ { |amt| // 0>1
		synths.do(_.spread_(amt)); this.changed(\spread, amt);
	}

	cluster_ { |clusterIndex|

		// make sure the cluster has frames in it
		if( master.invalidClusters.includes(clusterIndex) and: (setClusterCnt < 20),
			{
				warn("chose a cluster with no frames, choosing another randomly");
				this.cluster_(master.numClusters.rand);
				setClusterCnt = setClusterCnt + 1;
			},{
				curCluster = clusterIndex;
				synths.do(_.cluster_(curCluster));
				numFramesInCluster = master.numFramesInClusters[curCluster];
				clusterFramesByDist = master.clusterFramesByDist;
				setClusterCnt = 0;
				this.changed(\cluster, curCluster);
			}
		);
	}

	updateBuffer { |buffers|

		if( buffers.size != synths.size, {
			var diff = (buffers.size - synths.size).abs;
			warn("number of buffers (channels) doesn't match the number of synths in this GrainSan2");
			if( diff > 0,
				{	// more buffer channels than synths
					synths.do{|synth, i| synth.buffer_(buffers[i]).bufnum_(buffers[i].bufnum) }
				},{	// more synths than buffer channels
					buffers.do{|buf, i| synths[i].buffer_(buf).bufnum_(buf.bufnum) };
					diff.abs.do{|i| synths.reverse[i].release};
				}
			);
		},{
			buffers.do{|buf, i| synths[i].buffer_(buf).bufnum_(buf.bufnum) };
		}
		);

		bufDur = buffers[0].duration;
	}

	buildResponder {

		grnResponder !? {grnResponder.free};

		grnResponder = OSCFunc({ |msg, time, addr, recvPort|
			var clust_spread, shift, grndur, frame, start, end;

			var node;
			node = msg[1];
			synths.do{ |synth|
				// make sure to respond only to the synth that sent the message
				if( synth.node == node, {

					#clust_spread, shift, grndur = msg[3..];

					frame = this.chooseClusterFrame(clust_spread, shift);
					// postFrames.if{ frame.postln }; // debug

					// center the grain around the frame location
					synth.pos_(frame - (grndur * 0.5) / bufDur);
				})
			};
		},
		'/pointer',
		Server.default.addr,
		argTemplate: [nil, this.replyID] // respond only to this instance's synths by replyID
		);
	}

	// spread controls the probablitity distribution
	//	probability -> 'shift' value as spread -> 0
	//	practical vals to still cluster around 'shift' are ~0.5 max
	//	beyond that it's pretty much uniform random
	//	spread of 0.001 pretty much converges on 'shift'
	chooseClusterFrame { |spread = 0.1, shift = 0|
		var ptr, index;
		// choose random (gausian) index into the frames
		ptr = shift.gaussian(spread);
		ptr = ptr.fold(0,1); // keep in range at cost of slightly altering distro

		// translate this normalized pointer position
		// into an index into the clustered frames
		index = (numFramesInCluster - 1 * ptr).round;
		^clusterFramesByDist[curCluster][index]
	}


	free {
		grnResponder.free;
		synths.do(_.free);
		view !? { view.win.isClosed.not.if{view.win.close} };
	}

	gui {
		var test = view.isNil;
		test.not.if{ test = view.win.isClosed };
		test.if{ view = GrainScan2View(this) };
	}
}

GrainScan2View {
	// copyArgs
	var scanner;
	var <win, <controls;

	*new {|aGrainScanner2|
		^super.newCopyArgs( aGrainScanner2 ).init;
	}

	init {
		scanner.addDependant( this );
		controls = IdentityDictionary(know: true);
		this.buildControls;
		this.makeWin;
	}


	// TODO: make a class to handle grouping widgets (like EZ) that works with layouts
	buildControls {
		var bw = 40;

		controls.putPairs([
			\grnDur, ()
			.numBox_( NumberBox()
				.action_({ |bx|scanner.grnDur_(bx.value) })
				.value_(scanner.master.grnDurSpec.default)
				.fixedWidth_(bw).align_(\center)
				.maxDecimals_(3)
			)
			.knob_(	Knob().step_(0.001)
				.action_({|knb|
					scanner.grnDur_(scanner.master.grnDurSpec.map(knb.value)) })
				.value_(scanner.master.grnDurSpec.unmap(scanner.master.grnDurSpec.default))
			),

			\grnRate, ()
			.numBox_( NumberBox()
				.action_({ |bx|
					scanner.grnRate_(bx.value) })
				.value_(scanner.master.grnRateSpec.default)
				.fixedWidth_(bw).align_(\center)
				.maxDecimals_(3)
			)
			.knob_(	Knob().step_(0.001)
				.action_({|knb|
					scanner.grnRate_(scanner.master.grnRateSpec.map(knb.value)) })
				.value_(scanner.master.grnRateSpec.unmap(scanner.master.grnRateSpec.default))
			),

			\grnRand, ()
			.numBox_( NumberBox()
				.action_({ |bx|
					scanner.grnRand_(bx.value) })
				.value_(scanner.master.grnRandSpec.default)
				.fixedWidth_(bw).align_(\center)
				.maxDecimals_(3)
			)
			.knob_(	Knob().step_(0.001)
				.action_({|knb|
					scanner.grnRand_(scanner.master.grnRandSpec.map(knb.value)) })
				.value_(scanner.master.grnRandSpec.unmap(scanner.master.grnRandSpec.default))
			),

			\grnDisp, ()
			.numBox_( NumberBox()
				.action_({ |bx|
					scanner.grnDisp_(bx.value) })
				.value_(scanner.master.grnDispSpec.default)
				.fixedWidth_(bw).align_(\center)
				.maxDecimals_(3)
			)
			.knob_(	Knob().step_(0.001)
				.action_({|knb|
					scanner.grnDisp_(scanner.master.grnDispSpec.map(knb.value)) })
				.value_(scanner.master.grnDispSpec.unmap(scanner.master.grnDispSpec.default))
			),

			\amp, ()
			.slider_( Slider().orientation_('vertical')
				.action_({|sl|
					scanner.amp_(scanner.master.ampSpec.map(sl.value).dbamp)
				})
				.value_(scanner.master.ampSpec.unmap(scanner.master.ampSpec.default))
			)
			.numBox_( NumberBox()
				.action_({ |bx|
					scanner.amp_(bx.value.dbamp) })
				.fixedWidth_(bw).align_(\center)
				.value_(scanner.master.ampSpec.default)
			)
			.txt_( StaticText().string_("dB").align_(\center)
			),

			// cluster controls

			\clusterSpread, ()
			.numBox_( NumberBox()
				.action_({ |bx|
					scanner.clusterSpread_(bx.value) })
				.value_(scanner.master.clusterSpreadSpec.default)
				.fixedWidth_(bw).align_(\center)
				.maxDecimals_(3)
			)
			.knob_(	Knob().step_(0.001)
				.action_({|knb|
					scanner.clusterSpread_(scanner.master.clusterSpreadSpec.map(knb.value)) })
				.value_(scanner.master.clusterSpreadSpec.unmap(scanner.master.clusterSpreadSpec.default))
			),

			\distFrmCen, ()
			.numBox_( NumberBox()
				.action_({ |bx|
					scanner.distFrmCen_(bx.value) })
				.value_(scanner.master.distFrmCenSpec.default)
				.fixedWidth_(bw).align_(\center)
				.maxDecimals_(3)
			)
			.knob_(	Knob().step_(0.001)
				.action_({|knb|
					scanner.distFrmCen_(scanner.master.distFrmCenSpec.map(knb.value)) })
				.value_(scanner.master.distFrmCenSpec.unmap(scanner.master.distFrmCenSpec.default))
			),

			\newCluster, ()
			.numBox_( NumberBox()
				.action_({ |bx| scanner.cluster_(bx.value.asInt) }).value_(scanner.curCluster) )
			.fixedWidth_(bw).align_(\center)
			.txt_( StaticText().string_("Cluster") ),


			// space controls
			\pan, ()
			.numBox_( NumberBox()
				.action_({ |bx|
					scanner.pan_(bx.value) })
				// .value_(scanner.azSpec.default)
				.fixedWidth_(bw).align_(\center)
				.value_(scanner.master.panSpec.default)
			)
			.knob_(	Knob().step_(0.001).centered_(true)
				.action_({|knb|
					// scanner.az_(scanner.azSpec.map(knb.value)) })
					scanner.pan_(scanner.master.panSpec.map(knb.value)) })
				// .value_(scanner.azSpec.unmap(scanner.azSpec.default))
				.value_(scanner.master.panSpec.unmap(scanner.master.panSpec.default))
			),

			\spread, ()
			.numBox_( NumberBox()
				.action_({ |bx|
					scanner.spread_(bx.value) })
				// .value_(scanner.xformAmtSpec.default)
				.fixedWidth_(bw).align_(\center)
				.value_(scanner.master.spreadSpec.default)
			)
			.knob_(	Knob().step_(0.001)
				.action_({|knb|
					// scanner.spread_(scanner.xformAmtSpec.map(knb.value)) })
					scanner.spread_(scanner.master.spreadSpec.map(knb.value)) })
				// .value_(scanner.xformAmtSpec.unmap(scanner.xformAmtSpec.default))
				.value_(scanner.master.spreadSpec.unmap(scanner.master.spreadSpec.default))
			),

			// play/release
			\fadeIO, ()
			.button_(
				Button()
				.states_([["*", Color.black, Color.red], ["*", Color.black, Color.green]])
				.action_({
					|but| switch( but.value,
						0, {scanner.release},
						1, {scanner.play}
					);
				}).value_(0)
			)
			.txt_( StaticText().string_("Fade in/out") ),

		]);
	}

	makeWin {
		var bw = 40;
		win = Window("a GrainScanner", Rect(200,200,450,100)).layout_(
			HLayout(
				VLayout(
					[controls[\amp].slider.maxWidth_(30), a: \center],
					controls[\amp].txt.maxWidth_(45).align_(\center),
					[controls[\amp].numBox.fixedWidth_(bw), a: \bottom],
				),
				VLayout(
					HLayout(
						*[\grnDur, \grnRate, \grnRand, \grnDisp].collect({ |key|
							var col = Color.hsv(0, 0.65, 1);
							VLayout( StaticText().string_(key).align_(\center).background_(col),
								HLayout(
									controls[key].numBox.fixedWidth_(bw).background_(col).background_(col.alpha_(0.2)),
									controls[key].knob.mode_(\vert).background_(col.alpha_(0.2))
								)
							)
						})
					).spacing_(2),
					HLayout(
						VLayout(
							[controls[\newCluster].txt.align_(\center), a: \topLeft ],
							[controls[\newCluster].numBox.fixedWidth_(bw).align_(\center).background_(Color.yellow.alpha_(0.2)), a: \topLeft],
							nil
						),
						nil,
						*[\distFrmCen, \clusterSpread].collect({ |key|
							var col = Color.hsv(0.3, 0.6, 1);
							VLayout( StaticText().string_(key).align_(\center).background_(col),
								HLayout(
									controls[key].numBox.fixedWidth_(bw).background_(col.alpha_(0.2)),
									controls[key].knob.mode_(\vert).background_(col.alpha_(0.2))
								)
							)
						})
					).spacing_(2),
					HLayout(
						nil, nil,
						*[\pan, \spread].collect({ |key|
							var col = Color.hsv(0.6, 0.65, 1);
							VLayout( StaticText().string_(key).align_(\center).background_(col),
								HLayout(
									controls[key].numBox.fixedWidth_(bw).background_(col.alpha_(0.2)),
									controls[key].knob.mode_(\vert).background_(col.alpha_(0.2))
								)
							)
						})
					).spacing_(2),
					nil,
					HLayout(
						nil,
						[controls[\fadeIO].txt.align_(\left), a: \left ],
						[controls[\fadeIO].button.fixedWidth_(35), a: \left],
					),
				)
			)
		)
		.onClose_({scanner.removeDependant( this );})
		.front;
	}

	update {
		| who, what ... args |
		var val = args[0];

		if( who == scanner, {
			switch ( what,

				\grnDur, {  var ctl = controls.grnDur;
					ctl.numBox.value_( val );
					ctl.knob.value_(scanner.master.grnDurSpec.unmap( val ));
				},
				\grnRate, { var ctl = controls.grnRate;
					ctl.numBox.value_( val );
					ctl.knob.value_(scanner.master.grnRateSpec.unmap( val ));
				},
				\grnRand, {  var ctl = controls.grnRand;
					ctl.numBox.value_( val );
					ctl.knob.value_(scanner.master.grnRandSpec.unmap( val ));
				},
				\grnDisp, {  var ctl = controls.grnDisp;
					ctl.numBox.value_( val );
					ctl.knob.value_(scanner.master.grnDispSpec.unmap( val ));
				},
				\amp, {  var ctl = controls.amp;
					ctl.numBox.value_( val.ampdb );
					ctl.slider.value_(scanner.master.ampSpec.unmap( val.ampdb ));
				},

				\distFrmCen, {  var ctl = controls.distFrmCen;
					ctl.numBox.value_( val );
					ctl.knob.value_(scanner.master.distFrmCenSpec.unmap( val ));
				},
				\clusterSpread, { var ctl = controls.clusterSpread;
					ctl.numBox.value_( val );
					ctl.knob.value_(scanner.master.clusterSpreadSpec.unmap( val ));
				},
				\pan, { var ctl = controls.pan;
					ctl.numBox.value_( val );
					// ctl.knob.value_(scanner.azSpec.unmap( val ));
					ctl.knob.value_(scanner.master.panSpec.unmap( val ));
				},
				\spread, { var ctl = controls.spread;
					ctl.numBox.value_( val );
					// ctl.knob.value_(scanner.xformAmtSpec.unmap( val ));
					ctl.knob.value_(scanner.master.spreadSpec.unmap( val ));
				},
				\cluster, {
					controls[\newCluster].numBox.value_( val );
				},
				\gate, {
					controls[\fadeIO].button.value_(val);
				}
			)
		});
	}
}

/* Usage
var p;
p = "/Users/admin/src/rover/data/AUDIO/discovery_cliffside_clip.WAV"
g = GrainScanner2(0, p)
g.play
g.gui
g.scanRange(rand(g.bufDur), 1)
g.free

i = GrainScanner(0, g.buffers)
i.play
i.gui
i.scanRange(rand(g.bufDur), 1)

q = 4.collect{GrainScanner(0, g.buffers)}
q.do(_.gui)
q.do(_.free)

*/