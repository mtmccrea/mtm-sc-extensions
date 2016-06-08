// running this will boot the server it it's not already booted

// an array for outbusses will send each synth to individual busses
g = GrainFader([0,1], [2,3], bufFolderPath: "/Users/admin/Documents/Recordings/performance/");
// g = GrainFader(0, 1, bufFolderPath: "/Users/admin/Documents/Recordings/performance/");


// useful to know this local IP for touchOSC setup
Pipe.new("ipconfig getifaddr en1", "r").getLine();

// once connected on a local network, enter the iPad's IP
t = TouchOSC("169.254.75.190", 9000)

// connect the TouchOSC to the GrainFader
// note the layout now has a 4x4 menu of soundfiles
g.connectTouchOSC(t, numSfColumns: 4, numSfRows: 4)

// >> Note you should see the soundfile names show up at the top of your TouchOSC interface, in the labels of the multi-push widgets.


// ~~~  Remapping Fader Controls  ~~~
// The ControlSpecs are stored in the class, but if you'd like
// to change the range or the curve of the mapping, you can remap the
// ControlSpec for a specific control
// NOTE: this method is called on TouchOsc, not GrainFader

// here's a list of all of the mapped controls
t.controls.keys.do(_.postln); nil
// Once you find the one you want, change it by the key,
// in this case, \amp2
t.remap(\amp2, ControlSpec(-120, 2, \db, 0, -90))
// or try changing the warp
t.remap(\amp2, ControlSpec(-90, 8, \linear, 0, -90))
// a numerical warp (like env)
t.remap(\amp2, ControlSpec(-90, 8, -4, 0, -90)) // this is the default in the class atm
// remember to change it on both synths ...
t.remap(\amp1, ControlSpec(-90, 8, -4, 0, -90)) // this is the default in the class atm

// remap pitch
t.remap(\pitch2, ControlSpec(0.3, 8.0, 3, 0, 1)) // this is the default in the class atm
// ... see the ControlSpec helpfile for more mapping options


// ~~~  Smoothing Amplitude Controls  ~~~
// if the amplitude jumps too much for your liking,
// you can smooth it (0.3 sec by default)
g.ampLag_( 1.3 )

// useful while working/rehearsing
// mute (pause)
g.mute
// unmute (run)
g.unmute


// VALUE SNAPPING
// pitch and posRate can snap to a value within a threshold

// defaults
g.pitchSnapVal
g.pitchSnapThresh
g.rateSnapVal
g.rateSnapThresh

// change them...
g.pitchSnapVal_(1)
g.pitchSnapThresh_(0.05)
g.rateSnapVal_(1)
g.rateSnapThresh_(0.05)


// PRESETS

// which 0, 1, or 2:
// 0 - synth1 (left)
// 1 - synth2
// 2 - both --- NOT REALLY SUPPORTED WITH NEW RECALLING SCHEME
g.storePreset(0, \test1)


// When recalling a preset, it first follow whichSynth argument,
// if whichSynth isn't provided (nil), follow recvUpdate state of the synths,
// if neither, update the synth opposite of the last updated synth

// variance:	vary the synth params by variance value, typically 0 > 1
// whichSynth:	nil - opposite the last recalled, 0 or 1 - (left or right on interface)
// cueOnly:		true - the balance isn't changed, fadeTime ignored; false - balance fades to the new preset

g.recallPreset(\test1, fadeTime: 2, variance: 0, cueOnly: true ) // defaults

// whichSynth arg explicitly recalls to synth 0 or 1 (left or right)
g.recallPreset(\test1, fadeTime: 2, variance: 0, cueOnly: true, whichSynth: 1 )

// if cueOnly is false, the preset is recalled and the balance fades to whichSynth over fadeTime
g.recallPreset(\test1, 1, cueOnly:false, whichSynth: 0)

//optionally use a variance amount 0>1 that varies the synth preset params
g.recallPreset(\test1, 2, variance: 0.15)

// if you make changes to the sound, you can update the preset
// (updates the last recalled)
// this is preferred to overwriting the a preset because you may miss which synth
// the preset is recalled to, and get the index wrong when updating a preset
g.updatePreset

// if you try to store a preset that's already used
g.storePreset(0, \test1)
//... it wil fail and make you explicitly remove the one you're replacing
g.removePreset(\test1)	// remove
g.storePreset(0, \test1)	// then write


// unless you explicitly tell it to overwrite at the time of writing,
// make sure you note the correct synth index
g.storePreset(1, \test1, overwrite: true)


g.presets		// Return all the presets
g.listPresets	// Easier to read (posts them alphabetically)

// a very simple gui to show you all the saved presets, recall them, update them
// one method takes an array of "categories", which will match presets to specified strings.
// This expects that presets are saved in way that somehow contains a category in the key's name.
// e.g. "trumpet" would match 'trumpet01', 'trumpet02', 'trumpet_noisy', etc...
// and create a "trumpet" column in the preset window, think of it as a myKey* matching.
g.presetGUIbyCategory(["test1", "test0", "test3"])
// note: this will display ONLY presets that found a key matching a category
// this could also be useful for creating separate GUIs for separate categories,
// e.g:
g.presetGUIbyCategory(["trumpet", "trombone"])
g.presetGUIbyCategory(["piano", "bass", "drums"])

// alternatively, a simple layout in alphabetical order, arranged into the specified
// number of columns
g.presetGUI(numCol:3)


// could be a good idea to backup your SC archive
// (archive.sctxar in Library/Application Support/SuperCollider)
// in case you overwrite it, or need to roll back, saves to desktop
g.backupPreset			// as instance method
GrainFader.backupPreset		// or class method



// CLEANUP

// free the GrainFader
g.free;

// Disconnect all controls from your object/model.
// Controls are still stored with their mappings, they just no longer control your object/model.
t.disconnectAll

// or free for good - remove the reponders entirely
t.free