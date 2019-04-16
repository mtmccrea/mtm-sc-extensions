+ Color {

	// generate an array of colors in hsv space within a certain hue range
	*hsvSeries { |numColors, hueOffset=(rrand(0,1.0)), hueRange=(rrand(0.15,1.3)), sat = 0.6, value = 1, alpha = 1|
		var hueStep = max(1, (numColors-1)).reciprocal;
		 ^numColors.collect{ |i|
			Color.hsv(
				// ((i/(max(numColors,1.0001)-1)) * hueRange + hueOffset).wrap(0,1),
				((hueStep * i) * hueRange + hueOffset).wrap(0,1),
				sat,
				value,
				alpha
		)}
	}

}