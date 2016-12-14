// Set properties in an Event....
(
~default = (
	baseHue: 		Color.red,
	fillColor:		{ Color.hsv(1, 1, 0.5).hueBlend(~baseHue, 0.5) },
	strokeColor:	{ Color.hsv(1, 1, 1).hueBlend(~baseHue, 1) }
);

// Use .use to ensure ~environmentVariables get resolved using the event (~default)
~default.use {
	|env|
	env.baseHue.asArray.postln;
	env.fillColor.asArray.postln;
	env.strokeColor.asArray.postln;
};
)

// Override selected properties....
(
~volumeKnob = (
	baseHue: { Color.rand },
	fill: true,
	stroke: false
);
// Use ~default as the parent, to inherit anything that isn't specified
~volumeKnob.parent = ~default;

~volumeKnob.use {
	|env|
	env.baseHue.asArray.postln;
	env.fillColor.asArray.postln;
	env.strokeColor.asArray.postln;
};
)

// Combine two property dicts together - this SHOULD be the equivalent of parenting, except that it gives
// you a new, fixed Event rather than having to trace up the parent chain.
~newEvent = ~default.composeEvents((
	foo: true,
	bar: false
));

// This is probably more useful if you split each drawn component of your knob into objects
// that contain their own properties - then you can build the combined list of properties like:
~defaultProps = (
	some: 1,
	global: 2,
	properties: 3
);
[BackgroundLayer, ForegroundLayer, TextLayer].do {
	|layerClass|
	~defaultProps = ~defaultProps.composeEvent(layerClass.properties);
};

// Now, if you do this with your view:
view.setProps((
	\foo: 10,
	\bar: 20
));

// internally you can...
setProps {
	|event|
	actualProperties = actualProperties.composeEvent(event);
}

// Expose layer-specific properties as a class parameter
BackgroundLayer {
	*properties {
		^(
			ticks: 10,
			tickColor: { ~baseHue }
		)
	}

	draw {
		|props|
		Pen.strokeColor = props.tickColor;
		....
	}
}


// You can do the doesNotUnderstand property forwarding something like this:
MikesKnobIsAwesome {
	doesNotUnderstand {
		|selector, value|
		if (selector.isSetter && properties[selector.asGetter].notNil) {
			properties[selector.asSetter] = value;
		}
	}
}
// which would allow....
~myKnob.baseHue = Color.red;

// You could also expose the event storing the properties directly, which doesn't look so bad
~myKnob.properties.baseHue = Color.red;

// You'd need, as an optimization, to pre-calculate the actual property values, so that you're not calling functions or
// tripping through chains of Events. This is easy. You need to (a) .use to make sure you've got the
(
var properties, actualProperties;
properties = ~default;
actualProperties = properties.use {
	properties.collect {
		|value|
		value.value(properties); // first argument when calling value for each item is supposed to be the event - in this case, our props
	}
};
actualProperties.postln;
)

// You'd basically want to do this each time the properties are changed, to get your "hardened" list of actual property values.
// If you wanted SOME of your props to be evaluated at draw time instead of beforehand, you'd need to return the draw-time function as the result
// or otherwise wrap them - so that the first ".value" call above results in another function, rather than a fixed value.
// You can even be a little tricky here and allow environment variables in your functions... observe:

(
var defaults, userProperties, frozenProperties;

// default prop values
defaults = (
	baseHue: 		Color.red,
	fillColor:		{ Color.hsv(1, 1, 0.5).hueBlend(~baseHue, 0.5) },
	strokeColor:	{ Color.hsv(1, 1, 1).hueBlend(~baseHue, 1) }
);

// set by the user
// Wrap our function in a Ref, so it won't get "frozen" (Ref:value just returns the contianed object - you could also have wrapped this
// in a function, but Ref seems more visually clear than a double {{}} ).
// Also, notice -- when you run a pseudo-method on an event by doing e.g. userProperties.fillColor(), the first argument passed is the containing
// event itself, e.g. userProperties - it's effectively the "this" object. I'll access baseHue via this object, rather than doing it via an ~environmentVariable.
// You could also do ~baseHue, but you'd need to be using the Event (e.g. userProperties) as your current environment, meaning you'd need to wrap somethjing in .use{} at some point.
userProperties = (
	fillColor:		Ref({ |props, knobValue| props.baseHue.hueBlend(Color.blue, knobValue) });
);

// First, combine user props with defaults
frozenProperties = defaults.composeEvents(userProperties);
"Composed props list: %".format(frozenProperties).postln;

// then, resolve the actual property values
frozenProperties = frozenProperties.use {
	frozenProperties.collect { |val, key| val.value(frozenProperties) }
};
"Frozen props list: %".format(frozenProperties).postln;


// Now, you can use frozenProperties at draw time, and your ref'd function will get executed:
frozenProperties.fillColor(0.5).postln;
frozenProperties.fillColor(0.1).postln;
frozenProperties.fillColor(1.0).postln;

// You could also consider setting the knob value as a property at draw time, such that specialty draw-time functions could
// simply access it via the environment / the props arg. then you could do:
(
	fillColor:		Ref({ |props| props.baseHue.hueBlend(Color.blue, props.knobValue) });
);

)

// When you want to draw, you can set draw-time properties (if you want to do it this way....), and then call draw
// for each layer with the properties Event you've constructed:
frozenProperties[\knobValue] = knobValue;
[backgroundLayer, foregroundLayer, textLayer].do {
	|layer|
	layer.draw(frozenProperties, parentView)
};

// And:
BackgroundLayer {
	*properties {
		^(
			backgroundStroke: Color.red,
			backgroundFill: Color.blue
		)
	}

	draw {
		|props, parentView|
		Pen.strokeColor = props.backgroundStroke;
		Pen.fillColor = props.backgroundFill;
		Pen.addWedge(0@0, 100, 0, pi * 2 * props.knobValue);
	}
}

// This pattern is something like one I use in C++ all the time. Basically, if you have a sort of central component
// (your knob) that has a bunch of child pieces (draw layers), you let the child pieces define their own specific properties
// in a way that doesn't require the parent to KNOW anything about them, but still allows the parent to manage them
// in a overarching way. This way, your components can be changed easily, and your parent object doesn't really need to
// have ANY details about child-component-specific behavior (e.g. drawing), it just need to know (a) that they exist, and (b)
// that they have SOME set of properties.


