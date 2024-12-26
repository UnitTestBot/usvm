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
