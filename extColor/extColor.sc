+ Color {

	/*
		Generate an array of colors in hsv space within a certain hue range.
		'hueRange' will default to a range one step short of 1 (otherwise first and
		last hue will be identical).
	*/
	*hsvSeries { |numColors, hueOffset, hueRange, sat = 0.6, value = 1, alpha = 1|
		var hueStep;
		hueOffset = hueOffset ?? { 0 };
		hueRange  = hueRange ?? { 1 - numColors.reciprocal };
		hueStep   = hueRange / max(1, numColors - 1);

		^numColors.collect{ |i|
			Color.hsv(
				(hueOffset + (hueStep * i)).wrap(0,1),
				sat,
				value,
				alpha
		)}
	}

}