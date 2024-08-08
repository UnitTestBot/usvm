interface Microphone {
    uuid: string
}

class VirtualMicro implements Microphone {
    uuid: string;

    constructor() {
        this.uuid = "virtual_micro_v3"
    }
}

interface Devices {
    microphone: Microphone
}

class VirtualDevices implements Devices {
    microphone: Microphone;

    constructor() {
        this.microphone = new VirtualMicro();
    }
}

function getMicrophoneUuid(devices: Devices): string {
    return devices.microphone.uuid;
}

function entrypoint() {
    let devices = new VirtualDevices()
    let uuid = getMicrophoneUuid(devices)
    console.log(uuid)
}

function entrypoint0_manual() {
    let micro = new VirtualMicro()
    micro.uuid = "virtual_micro_v3"

    let devices = new VirtualDevices()
    devices.microphone = micro

    let uuid = getMicrophoneUuid(devices)
    console.log(uuid)
}

interface A {
    aStr: string
    bObj: B
}

interface B {
    bNum: number
}

function conditional(x: A, cond: boolean): number {
    if (cond) {
        return x.aStr.length
    } else {
        return x.bObj.bNum
    }
}

function entrypoint1(arg: A) {
    console.log(conditional(arg, false))
}

interface X {
    a: string
}

interface Y {
    b: number
}

function foo(x: X | Y) {
    if ("a" in x) {
        strBar(x.a)
    } else {
        numberBar(x.b)
    }
}

function baz(x: X & Y) {
    strBar(x.a)
    numberBar(x.b)
}

function strBar(x: string) {

}

function numberBar(x: number) {

}

function entrypoint2(arg0: X, arg1: Y) {
    foo(arg0)
    foo(arg1)
    baz({...arg0, ...arg1})
}
