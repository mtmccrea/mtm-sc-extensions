/*
m. mccrea - 160611_120213

Count the number of OSC messages received per second.

*/


/* define the osc listener to count the messages (just need to do this once)*/
(
~oscTag = '/myTag';		// the osc message "path": the tag to identify the message
									// put the tag that matches the message
									// your plant device/python bridge sends

~port = 57120;				// the port your python bridge sends TO; SC's default port is 57120

~starttime = Main.elapsedTime;
~count = 0;

~oscDef = OSCdef(\counter, {
	|msg, time, addr, recvPort|
	var now, count;
	now = Main.elapsedTime;

	if ((now - ~starttime) < 1) {
		~count = ~count + 1;
	} {
		~starttime = now;
		postf("% messages received\n", ~count);
		~count = 0;
	};
}, ~oscTag, recvPort: ~port
);
)


/*  TEST  */
// test internally, without the plant device... just to verify it works

(
var duration = 10;	// how long will the test last
var rate = 5000;		// how many messages per second

// this is me, listening for messages
~listenerAddress = NetAddr("localhost", ~port);

// this routine wil send messages for 'duration' at 'rate'
fork {
	~count = 0;
	(duration*rate).do{
		// send a message to myself, to be counted
		~listenerAddress.sendMsg(~oscTag, rrand(0,1.0));
		rate.reciprocal.wait; // send 100 msgs per second
	};
	"done".postln;
}
)


// To stop counting and to cleanup when you're done
~oscDef.free