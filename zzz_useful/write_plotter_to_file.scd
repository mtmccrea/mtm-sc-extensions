p = 300.collect({10.rand}).plot
p.dump
// then grab the window
(
// i = Image.fromWindow(w, a.bounds);
i = Image.fromWindow(p.parent, (p.plots[0].bounds.asArray+[0,0,15,15]).asRect);
w.close;
i.plot(freeOnClose:true);
)


~path = "~/Desktop/my_image.png".standardizePath;
i.write(~path, "PNG")

~path.openOS




(// Chebyshev polynomials
var stHarms = 4; // includes 0, so for 5th polynomial, harms = 6
var dexes;
var path = "~/Desktop/test/".standardizePath;
var time = Date.getDate.stamp;

fork( {
	path = path +/+ time;

	// make a directory
	format("mkdir %", path).unixCmd;
	0.5.wait;

	// j=0;
	// generate the sequence
	dexes = (0..180);
	dexes = dexes.clump(4).scramble.flat;

	dexes.do{|j, k|
		var harms = stHarms+j; //(2*j);
		p = harms.collect({|harm, i|
			300.collect({|i| var x = i/299*2-1; cos(harm * acos(x))})
		})
		.plot(
			format("% Chebyshev Polynomials", harms), [50,50, 1920, 1080].asRect, minval: -1, maxval: 1, separately: false,)
		.superpose_(true);

		"passed1".postln;
		p.plots[0].plotColor_(harms.collect{|i|Color.hsv(i/harms, 0.9, 1, 0.7)});
		p.plots.do { |x|
			var bool = false;
			x.gridOnY = bool; x.gridOnX = bool;
		};
		p.refresh;

		0.5.wait;
		"loading".postln;
		i = Image.fromWindow(p.parent, (p.plots[0].bounds.asArray+[0,0,15,15]).asRect);
		"passed".postln;
		i.write(
			format("%/%_%.png", path, k, harms),
			"PNG"
		);
		"wrote".postln;
		p.parent.close;
	}
}, AppClock)

)
// p.superpose=false


p.plots[0].gridOnY
p.plots[0].gridOnY=true
p.plots[0].gridOnX=true
p.plots[0].gridOnY=false
p.plots[0].gridOnX=true

