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