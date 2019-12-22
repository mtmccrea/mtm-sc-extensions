/* =====================================
		Signal Isolation
		e.g. subtract known background signal
				level from recording for signal level
		e.g. add/remove frequency band level during
				multi-band decomposition
		Thanks to Jo Anderson for getting this started.
   ===================================== */

/*
Test to confirm the method of cacluating the RMS of part of a signal
that is mixed with another signal if the rms of one of those signals is known.

The real case will be inferring the rms of one band from the
full signal's rms and the rms of an adjacent band.
*/

// size of test signals
~size = 2.pow(12).asInteger

// lfs (noise)
a = -14.dbamp * Array.fill(~size, { 7.7812830544624.dbamp.bilinrand }).as(Signal)	// noise ~ -14dB
// a = Array.fill(~size, { 7.7812830544624.dbamp.bilinrand }).as(Signal)				// noise ~ 0dB
// a = 14.dbamp * Array.fill(~size, { 7.7812830544624.dbamp.bilinrand }).as(Signal)	// noise ~ +14dB
// a = -12.dbamp * Signal.newClear(~size).sineFill2([[8], [13, 0.5]])


// hfs (signal)
b = 3.dbamp * Signal.newClear(~size).sineFill2([[55, 1.0, 0.5pi]])
// b = 12.dbamp * Array.fill(~size, { 1.0.bilinrand }).as(Signal)
// a = 12.dbamp * Signal.newClear(~size).sineFill2([[8], [13, 0.5], [21, 0.25], [55, 0.125, 0.5pi]]);
// b = 12.dbamp * Signal.newClear(~size).sineFill2([[7.3], [23, 0.5], [31, 0.25], [58, 0.125, 0.5pi]]).overDub(Signal.fill(~size, { 1.0.bilinrand }));


// sum - full spectrum: signal + noise
c = a + b
c.plot

// rms
x = a.rms // lfs
y = b.rms // hfs // recall rms of sinusoid is peak/(2.sqrt), e.g. 3.dbamp / 2.sqrt
z = c.rms // full

// (x.squared + y.squared).sqrt

x.ampdb  // lfs, dB
y.ampdb  // hfs, dB
z.ampdb  // full, dB

// full band sum = L2 norm: sqrt(x_rms^2 + y_rms^2)
(x.squared + y.squared).sqrt.ampdb  // some via rms
c.rms.ampdb 						// compare to pressure sum

// isolate level of noise signal
(z.squared - y.squared).sqrt.ampdb  // difference via rms
a.rms.ampdb							// compare to original noise signal's rms

// Combining
// find rms of a in dB, inferred from the full signal (noise + sinusoids) and sinusoids alone
(c.rms.squared - b.rms.squared).sqrt.ampdb  // inferred noise level
a.rms.ampdb									// actual noise level



/* Same as above, but through "pressure domain" */
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// "background noise removal" method from acoustical measurements course

// extract signal level from known background and combined level
(
var bg = x.ampdb;
var sum = c.rms.ampdb;
var sig = 10 * log10( 10.pow(sum/10) - 10.pow(bg/10) );
sig // extracted signal level in dB
)
y.ampdb // original signal level in dB


// extract background level from known signal and combined level
(
var sig = y.ampdb;
var sum = c.rms.ampdb;
var bg = 10*log10(10.pow(sum/10) - 10.pow(sig/10));
bg 		// extracted background level in dB
)
x.ampdb	// extracted signal level in dB