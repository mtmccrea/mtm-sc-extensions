g = GrainFader([0,0], [2,3], bufFolderPath: "/Users/admin/Documents/Recordings/performance/");
f = GrainFader([1,1], [2,3], bufFolderPath: "/Users/admin/Documents/Recordings/performance/");



// useful to know this local IP for touchOSC setup
Pipe.new("ipconfig getifaddr en1", "r").getLine();

// once connected on a local network, enter the iPad's IP
t = TouchOSC("169.254.253.177", 9000)
o = TouchOSC("169.254.75.190", 9000)

// connect the TouchOSC to the GrainFader
// note the layout now has a 4x4 menu of soundfiles
g.connectTouchOSC(t, numSfColumns: 4, numSfRows: 4)
f.connectTouchOSC(o, numSfColumns: 4, numSfRows: 4)


n = NetAddr("169.254.253.177", 9000)
m = NetAddr("169.254.75.190", 9000)

g.presetGUI(numCol:3)
f.presetGUI(numCol:3)


// here's a list of all of the mapped controls
t.controls.keys.do(_.postln); nil
// Once you find the one you want, change it by the key,
// in this case, \amp2
t.remap(\amp2, ControlSpec(-120, 2, \db, 0, -90))
// or try changing the warp
t.remap(\amp2, ControlSpec(-90, 8, \linear, 0, -90))
// a numerical warp (like env)
o.remap(\amp2, ControlSpec(-90, 8, -4, 0, -90)) // this is the default in the class atm
// remember to change it on both synths ...
t.remap(\amp1, ControlSpec(-90, 8, -4, 0, -90)) // this is the default in the class atm

// remap pitch
t.remap(\pitch2, ControlSpec(0.3, 2.0, 3, 0, 1)) // this is the default in the class atm
o.remap(\pitch2, ControlSpec(0.3, 2.0, 3, 0, 1)) // this is the default in the class atm
// ... see the ControlSpec helpfile for more mapping options

// test disconnect
t.disconnectAll
o.disconnectAll

// reconnect
g.connectTouchOSC(t, numSfColumns: 4, numSfRows: 4)
f.connectTouchOSC(o, numSfColumns: 4, numSfRows: 4)

[g,f].do(_.free)
[t,o].do(_.free)

s.quit