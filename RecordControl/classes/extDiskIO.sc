+ DiskOut {

	*kr { arg bufnum, channelsArray;
		^this.multiNewList(['control', bufnum] ++ channelsArray.asArray)
	}
}

+ DiskIn {

	*kr { arg numChannels, bufnum, loop = 0;
		^this.multiNew('control', numChannels, bufnum, loop)
	}
}