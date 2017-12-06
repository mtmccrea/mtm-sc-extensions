// Rules:
// 1. only broadcast changed values
// 2. only allow sending values at the broadcastRate
// 3. new values are sent at the time they arrive, assuming the broadcast "gate" is open
// 4. last updated but not-yet-sent values are sent immediately on the next broadcast cycle

// doesn't quite get there...
(
{ arg val=1, broadcastRate = 30;
    var sig, gateOpened, changed, closeGate, gateIsOpen, sendTrig, doSend;
    var valueWaiting;

    // sig = val;
    sig = LFDNoise0.kr(broadcastRate*1.5);

    changed = Changed.kr(sig);
    gateOpened = Impulse.kr(broadcastRate);


    // gate is closed behind a new value
    closeGate = Delay1.kr(changed); // close the gate the sample after a new value


    gateIsOpen = SetResetFF.kr(gateOpened, closeGate);

    // values can be sent either when the gate opens (sending last new value)
    // or when a value has changed in the window of an open gate, and
    // no value has yet been sent
    sendTrig = gateOpened + changed;

    // new value trigger * gate state
    doSend = sendTrig * gateIsOpen;

    // unsentNewValue = SetResetFF.kr(changed, doSend);

    SendReply.kr(doSend, '/iChanged', [sig]);

    // Out.kr(0,
    [
        sig,
        gateOpened,
        gateIsOpen,
        doSend * sig
    ]
    // );
}.plot(10.reciprocal*2, separately: true)
)



(
{ arg val=1, broadcastRate = 30;
    var sig, gateOpened, changed, newValCloseGate, gateIsOpen, sendTrig, doSend;
    var gateIsClosed, valueWaiting, closeAfterGateOpens, waitingValCloseGate;
    var gateClosed, gate;

    // sig = val;
    sig = LFDNoise0.kr(broadcastRate*10.5);

    changed = Changed.kr(sig);
    gateOpened = Impulse.kr(broadcastRate);
    gateClosed = Delay1.kr(changed);

    gate = SetResetFF.kr(gateOpened, gateClosed-gateOpened); // don't let new value close gate when it's opening

    doSend = changed * gate;

    SendReply.kr(doSend, '/iChanged', [sig]);

    // Out.kr(0,
    [
        sig,
        // changed,
        gateOpened,
        // gate,
        doSend * sig
    ]
    // );
}.plot(10.reciprocal*2, minval:-1, maxval:1, separately: true)
)




