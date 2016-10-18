GenericCV {
	classvar <>defaultValue, <>defaultSpec, <>debug=false;
	var <value, <spec;
	var <>dependantAdded=false;
	var <responders;

	*new {
		|initialValue, spec|
		^super.new.init(initialValue, spec);
	}

	init {
		|initialValue, inSpec|
		value = initialValue ?? defaultValue.copy;
		spec = inSpec ?? defaultSpec.copy;
	}

	value_{
		|inVal|

		inVal = spec.constrain(inVal);

		// removed same-value setting because, e.g. noteOn will always send the same message
		// if ((value != inVal) or: dependantAdded) {
			value = inVal;
			this.changed(\value, value);
			dependantAdded = false;
	// }
	}

	input_{
		|inVal|
		this.value_(spec.map(inVal));
		this.changed(\input, inVal);
	}

	input {
		^spec.unmap(value);
	}

	spec_{
		|inSpec, updateVal=true|

		if (inSpec != spec) {
			var oldVal = this.input;
			spec = inSpec;
			updateVal.if{this.input_(oldVal)};
			this.changed(\spec, spec);
			this.value = value;
		}
	}

	connectTo { |objOrFunc, performMethod| //TODO: , useInput=false|
		var dep;

		dep = if (objOrFunc.isKindOf(Function)) {
			if (this.connectedFunctions.includes(objOrFunc)) {
				"Function already connected!".warn;
				^this
			};

			FuncResponder(objOrFunc, this);
		} {
			this.connectedMethods.do{|objMthd|
				var obj, method;
				#obj, method = objMthd;
				if ((objOrFunc == obj) and: (performMethod == method)) {
					"Method already connected!".warn;
					^this
				}
			};

			MethodResponder(objOrFunc, performMethod, this);
		};
		dep.isNil{^this}; // break

		responders = responders.add(dep);
		// TODO: check if the dependent exists
		this.addDependant(dep);
		this.dependantAdded = true;
	}

	disconnect { |objFuncOrResp, performMethod|
		var rmvIdxs = [];

		if (objFuncOrResp.isKindOf(MethodResponder) or:
			objFuncOrResp.isKindOf(FuncResponder) ) {
			responders.do{ |resp, i|
				if (resp == objFuncOrResp) {
					this.class.debug.if{"Found the responder to disconnect".postln};
					rmvIdxs = rmvIdxs.add(i);
				}
			}
		} {
			// look up the FuncResponder
			responders.do{ |resp, i|
				case
				{resp.isKindOf(FuncResponder)} {
					if (resp.func == objFuncOrResp) {
						this.class.debug.if{"Found the function responder".postln};
						rmvIdxs = rmvIdxs.add(i);
					}
				}
				{resp.isKindOf(MethodResponder)} {
					if (resp.receiver == objFuncOrResp) {
						if (performMethod.isNil) { // remove all all responders for this object
							this.class.debug.if{"Found the object responder".postln};
							rmvIdxs = rmvIdxs.add(i);
						} {
							if(resp.method == performMethod) { // remove only responders for this object's method
								this.class.debug.if{"Found the object's method responder".postln};
								rmvIdxs = rmvIdxs.add(i);
							}
						}
					}
				};

			};
		};

		if (rmvIdxs.size > 0) {
			rmvIdxs.sort.reverse.do{|rmvIdx|
				this.removeDependant(responders[rmvIdx]);
				responders.removeAt(rmvIdx);
			} {
				"Didn't find any responder to disconnect".warn
			}
		};
	}

	disconnectAll { this.dependants.do{|dep|this.removeDependant(dep)} }

	postConnections {
		"\n\t:: CV connections ::\n".postln;
		responders.do{|r,i|
			"\t".post;
			r.isKindOf(FuncResponder).if{r.func.postln};
			r.isKindOf(MethodResponder).if{[r.receiver, r.method].postln};
		}
	}

	connectedFunctions {
		var ret = [];
		this.responders.do{|r|
			r.isKindOf(FuncResponder).if{
				ret = ret.add(r.func)
			}
		}
		^ret
	}

	// return [obj, method] pairs
	connectedMethods {
		var ret;
		this.responders.do{|r|
			r.isKindOf(MethodResponder).if{
				ret = ret.add([r.receiver, r.method])
			}
		}
		^ret
	}
}

NumericCV : GenericCV {
	classvar <>debug=false;
	*initClass {
		Class.initClassTree(Spec);

		defaultValue = 0;
		defaultSpec = \unipolar.asSpec;
	}
}

// TODO: look into mididef dispatcher as a way to forward signals
MIDICV : NumericCV {
	classvar <>midiCvDict, <>debug=false;

	var <midiDef, func, isOwned=false;
	var <ccNum, <midiChan; //
	var <midiOut, <>toggleOnMode=1, <destPortIdx; //<toggleState,
	var <toggleCV, toggleFunc, mirrorHWFunc;

	cc {
		| ccnum, chan, srcID, argTemplate, dispatcher, divisor = 127.0 |
		var overWrite;

		// init global storage if not already
		MIDICV.midiCvDict ?? {MIDICV.midiCvDict = Dictionary()};

		ccNum = ccnum;

		overWrite = MIDICV.midiCvDict[ccNum];
		overWrite !? {
			// the former MIDICV will be overwritten in the global dict
			format("Overwriting MIDIdef: %", ccNum).warn;
			overWrite.free;
			overWrite = nil;
			responders = nil;
			format("Overwriting MIDIdef: %", ccNum).warn;
		};


		func = func ? {
			|val, num, mchan, and src|
			var in = val / divisor;
			midiChan = mchan; // set midi channel so MIDIOut toggle has access to it
			this.input = in; // this signals the value change to responders
		};

		chan !? {midiChan = chan};
		midiDef = MIDIdef.cc(ccNum.asSymbol, func, ccNum, chan, srcID, argTemplate, dispatcher);

		MIDICV.midiCvDict.put(ccNum, this);

		responders = responders ? [];
	}

	button {
		| ccnum, chan, srcID, argTemplate, dispatcher, divisor = 127.0 |
		var overWrite;

		// init global storage if not already
		MIDICV.midiCvDict ?? {MIDICV.midiCvDict = Dictionary()};

		ccNum = ccnum;

		overWrite = MIDICV.midiCvDict[ccNum];
		overWrite !? {
			// the former MIDICV will be overwritten in the global dict
			format("Overwriting MIDIdef: %", ccNum).warn;
			overWrite.free;
			overWrite = nil;
			responders = nil;
			format("Overwriting MIDIdef: %", ccNum).warn;
		};


		func = func ? {
			|val, num, mchan, and src|
			var in = val / divisor;
			midiChan = mchan; // set midi channel so MIDIOut toggle has access to it
			this.input = in; // this signals the value change to responders
		};

		chan !? {midiChan = chan};
		midiDef = [
			MIDIdef.noteOn( (ccNum.asString++"_noteOn").asSymbol,
				func, ccNum, chan, srcID, argTemplate, dispatcher ),
			MIDIdef.noteOff( (ccNum.asString++"_noteOff").asSymbol,
				func, ccNum, chan, srcID, argTemplate, dispatcher )
		];

		MIDICV.midiCvDict.put(ccNum, this);

		responders = responders ? [];
	}

	// a setter to be explicit about setting the cc number
	newCC_ {
		|newCcNum|

		if (ccNum.isNil) {
			this.cc(newCcNum)
		} {
			var chan, srcID, argTemplate, dispatcher, func, key;

			if( (midiDef.msgType == \control ).not)
			{"Updating cc number on the fly is currently only supported with \control values, not noteOn/Off (button-style) values.\nIf creating a toggle CV, free and re-create a MIDICV to change cc number".throw};

			// remove the MIDIdef currently owned by this instance
			MIDICV.midiCvDict.removeAt(ccNum);
			ccNum = newCcNum; // update var to new ccNum

			chan = midiChan.postln; //midiDef.chan;
			srcID = midiDef.srcID.postln;
			argTemplate = midiDef.argTemplate.postln;
			dispatcher = midiDef.dispatcher.postln;
			func = midiDef.func.postln;
			key = midiDef.key.postln;

			midiDef.free;
			// create a midiDef to replace it
			"creating new one".postln;
			midiDef = MIDIdef.cc(ccNum.asSymbol, func, ccNum, chan, srcID, argTemplate, dispatcher);
			// move this instance to the new ccNum slot in the global dict
			MIDICV.midiCvDict.put(ccNum, this);
		}
	}

	enable { midiDef !? midiDef.asArray.do(_.enable) }
	disable { midiDef !? midiDef.asArray.do(_.disable) }

	// create a CV to hold the toggle state
	makeToggle {
		|initState=0|
		midiDef ?? {".cc_ hasn't yet been set on this MIDIDV".throw};
		if( midiDef.isKindOf(Array).not ) {"this MIDICV has not been set to be .button(noteNum)".throw};

		// a CV to hold the toggle state
		toggleCV = NumericCV(0, \unipolar.asSpec);
		toggleCV.value_(initState);

		toggleFunc = { |val|
			// set the toggle state on the push (val==1)
			// TODO: make it possible to set on release
			if (val==1) {
				toggleCV.value.asBoolean.if( // if already on
					{ toggleCV.value_(0) }, // turn off
					{ toggleCV.value_(1) }	// turn on
				);
			};
		};
		this.connectTo(toggleFunc);
	}

	toggleState {^toggleCV.value}

	// destPort: port index of controller in MIDIClient.destinations
	// initState: start with the toggle on (1) or off (0)
	// TODO: investigate onMode: blinking (2)
	mirrorHWToggle {
		|destPort=0, onMode=1|
		toggleCV ?? {"Toggle not yet created! Use .createToggle to initialize the toggle mode".throw};

		destPortIdx = destPort;
		midiOut = MIDIOut(destPort, MIDIClient.destinations[destPort].uid);
		toggleOnMode = if ((onMode>2) or: (onMode<1))
		{ "onMode must be 1 (solid) or 2 (blinking). Defaulting to 1.".warn; 1 }
		{ onMode };

		mirrorHWFunc = { |hwButtonVal|
			midiChan.isNil.if( {
				"no MIDI chan set yet, assuming channel 0 for initial state".warn;
				midiOut.noteOn(0, this.ccNum, this.toggleState.asBoolean.if({toggleOnMode},{0}));
			}, {
				midiOut.noteOn(midiChan, this.ccNum, this.toggleState.asBoolean.if({toggleOnMode},{0}));
			});
		};

		this.connectTo(mirrorHWFunc);
		mirrorHWFunc.value(0); // init with fake release message
	}

	stopMirrorHWToggle {
		mirrorHWFunc !? this.disconnect(mirrorHWFunc);
		midiChan !? {midiOut.noteOff(midiChan, this.ccNum, 0)};
		midiOut=nil;
	}

	// // val: 0 or 1
	// prUpdateToggle {|val|
	// 	var released = val.asBoolean.not;
	// 	// the button LED state should only update on release
	// 	if( released ){
	// 		toggleState.asBoolean.if( // if already on
	// 			{ // turn off
	// 				midiOut.noteOn(midiChan, this.ccNum, 0);
	// 				toggleState = 0;
	// 			},
	// 			{ // turn on
	// 				midiOut.noteOn(midiChan, this.ccNum, toggleOnMode);
	// 				toggleState = 1;
	// 			}
	// 		);
	// 	};
	// }

	// connectTo { |objOrFunc, performMethod| //TODO: , useInput=false|
	// 	var dep;
	//
	// 	dep = if (objOrFunc.isKindOf(Function)) {
	// 		if (this.connectedFunctions.includes(objOrFunc)) {
	// 			"Function already connected!".warn;
	// 			^this
	// 		};
	//
	// 		FuncResponder(objOrFunc, this);
	// 	} {
	// 		this.connectedMethods.do{|objMthd|
	// 			var obj, method;
	// 			#obj, method = objMthd;
	// 			if ((objOrFunc == obj) and: (performMethod == method)) {
	// 				"Method already connected!".warn;
	// 				^this
	// 			}
	// 		};
	//
	// 		MethodResponder(objOrFunc, performMethod, this);
	// 	};
	// 	dep.isNil{^this}; // break
	//
	// 	responders = responders.add(dep);
	// 	// TODO: check if the dependent exists
	// 	this.addDependant(dep);
	// 	this.dependantAdded = true;
	// }
	//
	// disconnect { |objFuncOrResp, performMethod|
	// 	var rmvIdxs = [];
	//
	// 	if (objFuncOrResp.isKindOf(MethodResponder) or:
	// 	objFuncOrResp.isKindOf(FuncResponder) ) {
	// 		responders.do{ |resp, i|
	// 			if (resp == objFuncOrResp) {
	// 				this.class.debug.if{"Found the responder to disconnect".postln};
	// 				rmvIdxs = rmvIdxs.add(i);
	// 			}
	// 		}
	// 	} {
	// 		// look up the FuncResponder
	// 		responders.do{ |resp, i|
	// 			case
	// 			{resp.isKindOf(FuncResponder)} {
	// 				if (resp.func == objFuncOrResp) {
	// 					this.class.debug.if{"Found the function responder".postln};
	// 					rmvIdxs = rmvIdxs.add(i);
	// 				}
	// 			}
	// 			{resp.isKindOf(MethodResponder)} {
	// 				if (resp.receiver == objFuncOrResp) {
	// 					if (performMethod.isNil) { // remove all all responders for this object
	// 						this.class.debug.if{"Found the object responder".postln};
	// 						rmvIdxs = rmvIdxs.add(i);
	// 					} {
	// 						if(resp.method == performMethod) { // remove only responders for this object's method
	// 							this.class.debug.if{"Found the object's method responder".postln};
	// 							rmvIdxs = rmvIdxs.add(i);
	// 						}
	// 					}
	// 				}
	// 			};
	//
	// 		};
	// 	};
	//
	// 	if (rmvIdxs.size > 0) {
	// 		rmvIdxs.sort.reverse.do{|rmvIdx|
	// 			this.removeDependant(responders[rmvIdx]);
	// 			responders.removeAt(rmvIdx);
	// 		} {
	// 			"Didn't find any responder to disconnect".warn
	// 		}
	// 	};
	// }
	//
	// disconnectAll { this.dependants.do{|dep|this.removeDependant(dep)} }
	//
	// postConnections {
	// 	postf("\n\t:: MIDICV % connections ::\n\n", ccNum);
	// 	midiDef.enabled.not.if{"\t[ DISABLED ]".postln};
	// 	responders.do{|r,i|
	// 		"\t".post;
	// 		r.isKindOf(FuncResponder).if{r.func.postln};
	// 		r.isKindOf(MethodResponder).if{[r.receiver, r.method].postln};
	// 	}
	// }
	//
	// *postConnections {
	// 	"\n\t:: MIDICV connections ::".postln;
	// 	MIDICV.midiCvDict.asSortedArray.do{|kv|
	// 		var key, mctl;
	// 		#key, mctl = kv;
	// 		postf("\n\tMIDICV %\n", key);
	// 		mctl.midiDef.enabled.not.if{"\t[ DISABLED ]".postln};
	// 		mctl.responders.do{|r,i|
	// 			"\t".post;
	// 			r.isKindOf(FuncResponder).if{r.func.postln};
	// 			r.isKindOf(MethodResponder).if{[r.receiver, r.method].postln};
	// 		}
	// 	}
	// }

	// connectedFunctions {
	// 	var ret = [];
	// 	this.responders.do{|r|
	// 		r.isKindOf(FuncResponder).if{
	// 			ret = ret.add(r.func)
	// 		}
	// 	}
	// 	^ret
	// }
	//
	// // return [obj, method] pairs
	// connectedMethods {
	// 	var ret;
	// 	this.responders.do{|r|
	// 		r.isKindOf(MethodResponder).if{
	// 			ret = ret.add([r.receiver, r.method])
	// 		}
	// 	}
	// 	^ret
	// }

	free {
		toggleCV !? toggleCV.free;
		this.disconnectAll;
		responders.do{ |resp| resp = nil };
		responders = func = nil;
		ccNum !? MIDICV.midiCvDict.removeAt(ccNum);
		midiDef !? midiDef.asArray.do(_.free); // asArray to handle noteOn/Off MIDIdef pairs for toggle
		// midiOut !? { this.stopEchoToggle; };
	}

	*clearAll {
		MIDICV.midiCvDict.keysValuesDo{|k,v| v.free};
	}

}

FuncResponder {
	var <func, <replyToObj;

	*new{ |func, replyToObj|
		^super.newCopyArgs(func, replyToObj)
	}

	update { |object, what ...args|
		if (object == replyToObj) {
			if (what == \value) {
				func.(*args)
			}
		}
	}
}

MethodResponder {
	var <receiver, <method, <replyToObj;

	*new{ |receiver, methodName, replyToObj|
		var ctktest = false;
		methodName = (methodName ? \value_).asSymbol;

		if (receiver.isKindOf(CtkNote)) {
			var noteArg;
			noteArg = if (methodName.asString.last.asString == "_") {
				var methodStr;
				methodStr = methodName.asString;
				methodStr.keep(methodStr.size-1);
			} { methodName };

			ctktest = receiver.args.keys.includes(noteArg.asSymbol);
		};

		if (ctktest.not) {
			if (receiver.respondsTo(methodName).not) {
				if (receiver.tryPerform(\know).asBoolean.not) {
					Exception("Object of type % doesn't respond to %.".format(receiver.class, methodName)).throw;
				}
			}
		};

		^super.newCopyArgs(receiver, methodName, replyToObj)
	}

	update { |object, what ...args|
		if (object == replyToObj) {
			if (what == \value) {
				receiver.perform(method, *args);
			}
		}
	}
}

MIDICtl {
	// copyArgs
	var <>divisor, <func;
	var <ccNum, <spec, <def, <key, <ctlVal, <mappedVal;

	*new { |ccNum, spec, func, key, divisor=127|
		^super.newCopyArgs(divisor, func).init(ccNum, spec, key);
	}

	init { |ccNum_, spec_, key_|
		(ccNum_ == \touch).if({
			"Touch a control to set the MIDICtl...".postln;
			MIDIdef.cc(\setCcNum, { |val, num, chan, src|
				[val, num, chan, src].postln;
				postf("Set ctl to chan %.\n", num);
				this.setDef(num, spec_, key_);
			}, nil).oneShot;
		},{
			this.setDef(ccNum_, spec_, key_);
		})
	}

	setDef { |ccNum_, spec_, key_|
		ccNum = ccNum_;
		spec = spec_.asSpec;
		key = (key_ ?? ccNum).asSymbol;

		// warn if overwriting another MIDIdef
		MIDIdef.allFuncProxies['MIDI control'] !? {
			MIDIdef.allFuncProxies['MIDI control'].collect(_.key).includes(key).if{
				format("Overwriting MIDIdef: %", key).warn
			}
		};

		def = MIDIdef.cc(key, { |val, num, chan, src|
			ctlVal = val;
			mappedVal = spec.map(val / divisor);
			func.value(mappedVal);
			// this.changed(\value, mappedVal); // Experimental!!
		}, ccNum );
	}

	func_ { |aFunc|
		func = aFunc;
	}

	spec_ { |aSpec| spec = aSpec.asSpec }

	ccNum_ { |newCCNum|
		// if key was set to former ccNum, rename to new one
		if(key.asInt == ccNum){key = newCCNum.asSymbol};
		def.free;
		this.setDef(newCCNum, spec, key);
	}

	disable { def.disable }

	enable { def.enable }

	free { def.free }

	*freeAll { MIDIdef.freeAll }
}