r = Rotary(bounds: Size(300, 300).asRect, innerRadiusRatio: 0.1).front;
r.spec = [10, 100, \lin].asSpec;
r.startAngle_(-0.75pi);
r.sweepLength = 1.5pi;
r.rangeFillColor = Color.new(0.9,0.9,0.9);
r.strokeRange = true;
r.fillRange = false;
r.fillRange = true;
r.showTicks = false
r.showTicks = true
r.showHandle_(true).handleColor_(Color.green);
r.levelFillColor = Color.green.alpha_(0.2);
r.strokeLevel = true;
r.levelStroke = \outside;
r.levelStrokeWidth = 3;
r.valueFontSize = 36;
r.valueAlign = \right;
r.showTicks = true;
r.numTicks = 5;
r.tickColor = Color.gray;
r.action = {|val| val.postln};

r.innerRadiusRatio_(0.000001) // TODO
r.innerRadiusRatio_(0.2) // TODO
r.valueAlign = \bottom
r.rangeStrokeWidth_(1)
r.tickAlign = \center
r.tickAlign = \outside
r.tickAlign = \inside
r.majorTickRatio = 0.2
r.showValue = false
r.strokeLevel =false
r.fillLevel =true
r.fillLevel =false
r.strokeRange =true
r.showTicks=false
r.showTicks=true
r.strokeRange = false
r.showHandle = false
r.showHandle = true
r.handleType_(\circle)
r.wrap =true
line



r = RotaryView(bounds: Size(300, 300).asRect, innerRadiusRatio: 0.1).clickMode_(\relative).front;
r.direction = \cw
r.startAngle_(-0.7pi).sweepLength_(1.4pi).direction = \ccw
r.direction = \cw


r.clickMode = \absolute
r.clickMode = \relative

r.orientation = \circular
r.orientation = \vertical

r.bipolar = true
r.bipolar = false

r.handle.fillArrow = false
r.handle.fillArrow = true
r.handle.arrowWLRatio = 1
r.handle.arrowLengthRatio = 0.7
r.handle.align = 0.2
r.step = 0.1;
r.scrollStepMul = 0.2;
r.arrowKeyStepMul = 1;
r.scrollDir = -1;

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
r = RotaryView(bounds: Size(300, 300).asRect, innerRadiusRatio: 0.1).clickMode_(\relative).front;
r.startAngle_(-0.7pi).sweepLength_(1.4pi).direction = \cw;
r.handle.type = \arrow;
r.handle.arrowWLRatio_(0.7).arrowLengthRatio_(1.3).align_(0.95).color_(Color.hsv(0,0.8,0.9));
// r.step = 0.2;
r.clickMode = \absolute;
r.orientation_(\circular);
r.ticks.show = true;
r.numTicks_(5, majorEvery: 1, endTick: true);
r.ticks.majorColor = Color.black;
r.range.fillColor = Color(*0.8!3);
r.level.fill = false;
r.level.strokeType_(\outside).strokeWidth_(8).strokeColor_(Color.hsv(0,1,0.6));
r.text.align_(0.5@0.8).font_(Font("Monaco", 24));
r.action = {|v, value, input| [value, input].postln};
r.spec.step_(r.spec.range/4)
)

// note, this will perform the action on every mouse move, even if it's the same value
r.suppressRepeatedAction = false;
// default behavior is to perform the action only if the value is new:
r.suppressRepeatedAction = true;

(
// variation on dial
r.handle.type = \line;
r.handle.width = 10;
r.handle.capStyle = \round;
r.handle.capStyle = \flat;
r.handle.getCapIndex(\square);
)
(
// variation on dial
r.handle.type = \arrow;
r.level.strokeType_(\around);
r.handle.fillArrow = false;
r.handle.width = 25;
r.handle.joinStyle = \miter;
r.handle.joinStyle = \round;
r.handle.joinStyle = \bevel;
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
r.ticks.majorRatio = 0.6;
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