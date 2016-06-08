g = GrainFader(0, 1, bufFolderPath: "/Users/admin/Documents/Recordings/performance/");

x = g.synth1
y = g.synth2

x.gate_(1)
x.gate_(0)

x.balanceAmp_(1)

y.gate_(1)
y.gate_(0)

y.grainRate_(0.7);
y.grainDur_(0.42);

x.grainRand_(0.1);
x.posDisp_(0.1);
x.pitchRate_(1.2);
//CTK bug - need to use function so getter in \posInv works
y.posRateScale_(1);
y.posRateScale_(-1);
y.start_(0.6);
y.end_(0.75);
x.auxmix_(0.6);

x.recv_buf_(0)
y.recv_buf_(1)
g.swapBuf_(4)
g.sfNames

g.presets

g.storePreset(0, 'test1')
g.storePreset(1, 'test2')
g.storePreset(0, 'test3')
g.storePreset(2, 'test4') // both synths

g.balance_(0.5)

Archive.global[\grainFaderStates][\test1].fileName
Archive.global[\grainFaderStates][\test2].fileName
Archive.global[\grainFaderStates][\test3].fileName
Archive.global[\grainFaderStates][\test4].fileName

Archive.global[\grainFaderStates][\test1].params.keysValuesDo({|k,v| [k,v].postln})
Archive.global[\grainFaderStates][\test1].params

Archive.global[\grainFaderStates].removeAt(\test3)

Archive.global[\grainFaderStates].keys

g.recallPreset(\test1)
g.recallPreset(\test2)
g.recallPreset(\test3)
g.recallPreset(\test4)

g.updatePreset

g.lastUpdated
g.lastRecalledSynthDex

x.bufnum

x.args
y.args
y.args[\fadeout] = 2

x.node.getNode
x.dump
x.args
g.free
x.set(0.0, \auxmix, 0.0);
x.set(0.0, \grainDur, 3);
x.set(0.0, \posRateScale, 1.asInteger);
x.posRateScale

x.set(0.0, \posRateScale, Archive.global[\grainFaderStates][\test1].params[\posRateScale])

(
fork {
Archive.global[\grainFaderStates][\test1].params.keysValuesDo({ |param, val|
			x.asArray.do{ |synth, i|
				postf("setting % to %\n", param, val.at(i));
				synth.set(0.0, param.asSymbol, val.at(i));
				// synth.args[param.asSymbol] = val.at(1);
			5.wait;
			};
		});
}
)
x.asArray.do{|synth| synth.postln;}

backupPreset {
format( "cp %% %%%", Archive.archiveDir, "/archive.sctxar", "~/Desktop/archive.sctxar_BAK_", Date.getDate.stamp, ".sctxar").replace(" Support","\\ Support").unixCmd
}


