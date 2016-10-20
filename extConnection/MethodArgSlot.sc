MethodArgSlot {
	var <updateFunc, <reciever, <methodName;

	*new {
		|obj, method ...args|
		^super.new.init(obj, method, args);
	}

	init {
		|inObject, inMethodName, args|
		reciever = inObject;
		methodName = inMethodName;
		updateFunc = MethodArgSlot.makeUpdateFunc(reciever, methodName, args);
	}

	*makeUpdateFunc {
		|reciever, methodName, args|
		var argString, callString;
		var possibleArgs = ['object', 'changed', '*args', 'args', 'value'];
args.postln;
		if (methodName.isKindOf(String) && args.isEmpty) {
			// Should be of the form e.g. "someMethod(value, arg[0])"
			callString = methodName;
			methodName = methodName.split($()[0].asSymbol; // guess the method name - used later for validation
		} {
			// args.do {
			// 	|a|
			// 	if (a.isNumber.not and:
			// 		a.isKindOf(Symbol).not and:
			// 	possibleArgs.includes(a).not) {
			// 		Error(
			// 			format("Can't handle arg '%' - must be one of: a Number, a Symbol, or Strings: %.",
			// 			a, possibleArgs.join(", "))
			// 		).throw
			// 	}
			// };

			if (args.isEmpty) {
				args = ['object', 'changed', '*args'];
			};

			argString = args.collect({
				|a|
				if (a.isKindOf(Symbol) and:
					possibleArgs.includes(a).not // don't convert a match in possibleArgs to Symbol
				) {
					"'%'".format(a) // wrap in '' so it's interpreted as a Symbol
				} {
					a.asString
				}
			}).join(", ");
			callString = "%(%)".format(methodName, argString);
			callString.postln;
		};

		if (reciever.respondsTo(methodName).not && reciever.tryPerform(\know).asBoolean.not) {
			Exception("Object of type % doesn't respond to %.".format(reciever.class, methodName)).throw;
		};

		^"{ |reciever, object, changed, args| var value = args[0]; reciever.% }".format(callString).postln.interpret;
	}

	update {
		|object, changed ...args|
		updateFunc.value(reciever, object, changed, args);
	}

	connectionTraceString {
		|what|
		^"%(%(%).%)".format(this.class, reciever.class, reciever.identityHash, methodName)
	}
}