r = RotaryView(bounds: Size(300, 300).asRect, innerRadius: 0.1).clickMode_(\relative).front;

// test methods / functionality
r.direction = \ccw;
r.startAngle_(-0.7pi).sweepLength_(1.4pi).direction_(\cw);

r.clickMode = \absolute; // jump to value on click, note: makes sense to use this with orientation=\circular
r.clickMode = \relative; // doesn't jump to click, move relative to starting click point

r.orientation = \circular // position moves with radial mouse drag
r.orientation = \vertical // position moves with vertical mouse drag
r.orientation = \horizontal // position moves with horizontal mouse drag

r.bipolar = true // center of movement defaults to spec.minval+spec.range.half
r.centerValue_(0.7) // but can be set:
r.centerValue
r.bipolar = false

// when setting a property to a function, the property list
// is passed into the function call, so other properties can be
// accessed as well as the ".val" and ".input"
r.level.fillColor = {|props| if (props.val > r.centerNorm) {Color.green.alpha_(0.3)} {Color.red.alpha_(0.3)}}

// each drawing layer
~layers = [r.range, r.level, r.handle, r.text, r.ticks];
// ...has properties
~layers.do{|me| me.class.postln; me.p.keysValuesDo{|k,v|postf("\t%,\t%\n", k,v)}}


// stepping and movement behavior
r.scrollStep = 0.005; 			// scale _input_ step on scroll movement, default: 0.01
r.keyStep = 20.reciprocal; 	// this covers the range in 20 strokes

r.xScrollDir = 1;					// change scroll direction, -1 or 1
r.yScrollDir = -1;						// change scroll direction, -1 or 1, -1 is "natural" scrolling on Mac
r.keyDirLR   = 1;						// change step direction of Left/Right arrow keys (1=right increments)
r.keyDirUD   = 1;						// change step direction of Up/Down arrow keys (1=up increments)

// Note the default movement will use the step resolution
// built into the underlying spec. To get the values to step,
// change the Rotary's (ValueView's) spec step:
r.spec.step = 0.1;
r.spec = [0, 20].asSpec
r.handle.fill

r.level.fillColor = {|me| if (me.val > r.centerNorm) {Color.green.alpha_(0.3)} {Color.red.alpha_(0.3)}}
r.level.fillColor = {|me| if (r.value > r.centerNorm) {Color.green.alpha_(0.3)} {Color.red.alpha_(0.3)}}
r.level.strokeColor = {|me| if (me.val > r.centerNorm) {Color.green} {Color.red}}
r.range.stroke = false
r.range.fillColor = Color(*0.8!3)

r.level.fillColor = {|me| Color.hsv( me.val.linlin(0,1,0.3,0.05), 0.7, 1, 1 )}
r.level.fillColor = {|me| Color.hsv( me.val.lincurve(0,1,0.3,0.05,3), 0.7, 1, 1 )}


/* radio dial */
(
r = RotaryView(bounds: Size(300, 300).asRect, innerRadius: 0.2).clickMode_(\relative).front;
// output
r.spec = ControlSpec(1,5);
r.spec.step = 1; // spec's step determins how the dial clicks into each position
r.action = {|v, value, input| [v, value, input].postln}; // action: post self, value, and normalized input
// shape
r.startAngle_(-0.7pi).sweepLength_(1.4pi).direction = \cw;
// handle style: arrow
r.handle.style = \arrow;
r.handle.length_(0.4).width_(0.6).anchor_(0.95).color_(Color.hsv(0,0.8,0.9));
// appearance
r.level.show = false; // turn off the level "wedge"
r.ticks.show = true; // show ticks
r.numTicks_(5, majorEvery: 1, endTick: true); // 5 positions,
r.ticks.majorLength_(0.3).majorWidth_(1.5);
r.ticks.majorColor = Color.gray;
r.text.align_(0.5@0.8).font_(Font("Monaco", 24));
// behavior
r.clickMode = \absolute; // value follows mouse down position
r.orientation_(\circular); // makes sense for a radio dial
)

// default behavior is to perform the action only if the value is new:
r.suppressRepeatedAction; // >> true
// this filtering can be turned off so the action will be performed on every mouse move, even if it's the same value
r.suppressRepeatedAction = false;

(
// variation on dial
r.handle.style = \line;
r.ticks.majorLength_(0.1).anchor_(0.95);
r.handle.length_(0.45).anchor_(0.85).strokeWidth_(10);
r.handle.capStyle = \round; // or \flat, or \square
r.level.show_(true).stroke_(false).fill_(true).fillColor_(Color.red.alpha_(0.1));
r.value_(4);
r.suppressRepeatedAction = true;
)
(
// variation on dial
r.innerRadiusRatio_(0); // no center gap
r.background_(Color.blue);
r.userView.background_(Color.white);
r.range.stroke_(true).strokeWidth_(4).strokeType_(\outside;
r.resize_(5);
r.handle.style_(\arrow).length_(0.8).width_(0.6).anchor_(0.8-r.innerRadiusRatio);
r.handle.fill = false;
r.handle.stroke_(true).strokeWidth_(6);
r.handle.fill_(true).fillColor_(Color.gray.alpha_(0.8));
r.handle.joinStyle = \round;
// r.handle.joinStyle = \miter;
// r.handle.joinStyle = \bevel;
r.level.show_(false);
)
(
r.handle.type = \arrow;
r.handle.fillArrow = true;
r.handle.stroke = true;
r.handle.width = 5;
r.handle.align = 0.95; // where does tip of arrow fall
r.handle.arrowLengthRatio = 0.4;
r.handle.arrowWLRatio = 0.4;
r.level.stroke = false;
)
(
r.spec.step = 0.5;
r.numTicks_(9, majorEvery: 2, endTick:true);
r.handle.style_(\circle).radius_(12);
r.ticks.align_(\center).anchor_(r.handle.anchor).majorLength_(0.3);
)


/* continuoud/wrapping dial */
(
r = RotaryView(bounds: Size(300, 300).asRect, innerRadiusRatio: 0.1).front;
r.innerRadiusRatio = 0.3;
r.spec = [0, 360, \lin].asSpec;
r.startAngle_(-0.0pi);
r.sweepLength = 2pi;
r.range.fillColor = Color.new(0.9,0.9,0.9);
r.range.stroke = false;
r.handle.show_(true).color_(Color.green);
r.level.fill = false;
r.level.stroke = false;
r.text.fontSize = 36;
r.text.align = 0.5@0.51;
r.text.color = Color.red;
r.ticks.show = true;
r.numTicks_(12, 3, endTick: false);
r.ticks.minorColor = Color.gray;
r.ticks.majorColor = Color.red;
r.ticks.align = \center;
r.ticks.majorLength = 0.6;
r.action = {|view, val, input| val.postln};
r.direction = \ccw;
r.wrap = true;
r.text.round = 1; // round the value of the displayed text
r.scrollStep_(0.01); // percentage of the full range for each scroll step
r.keyStep_(0.05); // percentage of the full range for each key step, 20 strokes per range in this case
)

(
r.handle.align = \inside
r.handle.align =0.91
r.handle.type = \circle
)
(
r.handle.type = \lineAndCircle
r.handle.align =0.5;
r.handle.radius = 8;
)

r.handle.type = \line
r.handle.p.keys


/* like SC's default knob */
(
r = RotaryView(bounds: Size(300, 300).asRect, innerRadiusRatio: 0.1).front;
)
(
// continuous/wrapping knob
r = Rotary(bounds: Size(300, 300).asRect, innerRadiusRatio: 0.4).front;
r.spec = [0, 360, \lin].asSpec;
r.startAngle_(0.0pi);
r.sweepLength = 2pi;
r.rangeFillColor = Color.new(0.9,0.9,0.9);
r.strokeRange = false;
r.showHandle_(true).handleColor_(Color.green);
r.fillLevel = false;
r.strokeLevel = false;
r.valueFontSize = 36;
r.valueAlign = \center;
r.showTicks = true;
r.numTicks_(12, 3, endTick: false);
// r.numTicks_(9, 2);
r.tickColor = Color.gray;
r.tickAlign = \outside;
r.majorTickRatio = 1;
r.action = {|val| val.postln};
r.direction = \cw;
r.wrap = true;
// r.wrap = false;
r.round = 1;
)

// peak/rms meter

(

r = Rotary(bounds: Size(300, 300).asRect, spec: [-120, 0, \db, 0, -6].asSpec, innerRadiusRatio: 0.4).front;
// r.spec = ControlSpec(-120, 0, \db, default:-6);
r.startAngle = -0.7pi;
r.sweepLength = 1.4pi;
r.rangeFillColor = Color.new(0.9,0.9,0.9);
r.orientation = \circular;
r.strokeRange = false;
r.showHandle_(true).handleColor_(Color.black);
r.fillLevel = true;
r.strokeLevel = false;
r.valueFontSize = 36;
r.valueAlign = \bottom;
r.showTicks = false;
// r.numTicks_(12, 3, endTick: true);
r.tickColor = Color.gray;
r.tickAlign = \center;
// r.majorTickRatio = 0.4;
r.action = {|val| val.postln; ~sig.set(\amp, val.dbamp)};
r.round = 0.1;
r.levelFollowsValue = false;
r.showTicks = true;
r.ticksAtValues_([0, -6, -12, -24], [-3, -9, -16]);

// sound source = 6 channels
~sig !? {~sig.free};
~sig = { |updateFreq=15, amp=0.5|
	var sig, trig, rms, peak, outbus;

	sig = WhiteNoise.ar(amp);

	trig = Impulse.kr(updateFreq);
	rms = RunningSum.rms(sig, 0.05*SampleRate.ir);
	peak = Peak.ar(sig, Delay1.kr(trig));

	SendReply.kr(trig,
		'/ampPkVals',
		[rms, peak]
	);
	// outbus = s.options.numInputBusChannels + s.options.numOutputBusChannels;
	// Out.ar(outbus, sig);
}.play;

// listener to get amp/peak vals and update meters

~meterListener = OSCdef(\meterRelay, { |msg|
	var ampPkVals;
	ampPkVals = msg[3..];
// ampPkVals.postln;
	defer {r.levelValue = ampPkVals[0].ampdb}
}, '/ampPkVals'
);


)



/* in a layout */
(
var setupRot, numRots = 8;
setupRot = {|col|
	var r;
	r= Rotary(spec: [0,45].asSpec, innerRadiusRatio: 0.1);
	r.spec = [10, 100, \lin].asSpec;
	r.startAngle_(0.75pi).sweepLength_(1.5pi);
	r.rangeFillColor = Color.new(0.9,0.9,0.9);
	r.strokeRange = false;
	r.levelStrokeColor = col;
	r.handleColor = col;
	r.levelFillColor = col.copy.alpha_(0.2);
	r.strokeLevel = true;
	r.levelStroke = \outside;
	// r.valueFontColor = col.val_(0.2);
	r.levelStrokeWidth = 3;
	r.valueFontSize = 12;
	r.valueAlign = \right;
	r.showTicks = true;
	r.numTicks_(15, 5);
	r.tickColor = Color.gray;
	r.action = {|val| val.postln};
	r;
};

w = Window(bounds:Rect(100,100, 140*8, 150)).front.layout_(HLayout(*numRots.collect({|i|setupRot.(
	Color.hsv(numRots.reciprocal*i, 1,1,1)
	// Color.red
)})))
)
)
)

/* circle handle */
(
r = Rotary(bounds: Size(300, 300).asRect, innerRadiusRatio: 0.4).front;
r.spec = [0, 360, \lin].asSpec;
r.startAngle_(0.0pi);
r.sweepLength = 2pi;
r.rangeFillColor = Color.new(0.9,0.9,0.9);
r.strokeRange = false;
r.showHandle_(true).handleColor_(Color.green);
r.fillLevel = false;
r.strokeLevel = false;
r.valueFontSize = 36;
r.valueAlign = \center;
r.showTicks = true;
r.numTicks_(12, 3, endTick: false);
// r.numTicks_(9, 2);
r.tickColor = Color.gray;
r.tickAlign = \inside;
r.majorTickRatio = 1;
r.action = {|val| val.postln};
r.direction = \cw;
// r.wrap = true;
r.wrap = false;
r.round = 1;
r.innerRadiusRatio_(0.25);
// r.handleType_(\circle);
r.handleType_(\lineAndCircle);
r.handleRadius_(8);
r.handleAlign_(\center);
// r.handleAlign_(\inside);
// r.handleAlign_(\outside);
r.value = 45;
r.fillLevel=true;
r.levelFillColor_(Color.green.alpha_(0.15));
r.showTicks_(false);
r.valueAlign;
)

// bipolar
(
r = Rotary(bounds: Size(300, 300).asRect, innerRadiusRatio: 0.4).front;
r.spec = [-180, 180, \lin].asSpec;
r.startAngle_(-0.9pi);
r.sweepLength = 1.8pi;
r.rangeFillColor = Color.new(0.9,0.9,0.9);
r.strokeRange = false;
r.showHandle_(true).handleColor_(Color.green);
r.fillLevel = false;
r.strokeLevel = false;
r.valueFontSize = 36;
r.valueAlign = \center;
r.showTicks = true;
r.numTicks_(12, 3, endTick: false);
// r.numTicks_(9, 2);
r.tickColor = Color.gray;
r.tickAlign = \inside;
r.majorTickRatio = 1;
r.action = {|val| val.postln};
r.direction = \cw;
r.wrap = false;
// r.wrap = false;
r.round = 1;
r.innerRadiusRatio_(0.25);
r.handleType_(\circle);
r.handleType_(\lineAndCircle);
r.handleRadius_(8);
r.handleAlign_(\center);
// r.value = 45;
r.fillLevel=true;
r.levelFillColor_(Color.green.alpha_(0.15));
r.showTicks_(false);
r.bipolar_(true);
r.centerValue = 0;
r.colorValBelow = 0.6;
r.strokeLevel_(true);
r.levelStrokeColor_(Color.green)
)


r.spec = [-inf, inf, \lin].asSpec;
r.spec = [-150, 6, \db].asSpec;
r.centerValue = 0
r.value
r.input

r.startAngle_(0)
r.direction = \ccw

r.startAngle_(-0.1pi)
r.direction
r.orientation = \circular
r.orientation = \vertical
r.orientation = \horizontal

r.value=0



/* ???????? */

startAngle reference from where? down? cw?
value/input is the right scheme?
should this be a quark?
color shift in negative bipolar - value shift or separate color parameter for levelBelowColor
params: strokeLevel=true & levelStroke=\inside – confusing?
terminology: range/level/handle/value?
