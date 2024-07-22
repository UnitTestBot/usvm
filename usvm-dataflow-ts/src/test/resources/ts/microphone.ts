interface Microphone {
    uuid: string
}

interface Devices {
    microphone: Microphone
}

class VirtualMicro implements Microphone {
    uuid: string = "virtual_micro_v3"
}

class VirtualDevices implements Devices {
    microphone: Microphone = new VirtualMicro()
}

function getMicrophoneUuid(device: Devices): string {
    return device.microphone.uuid
}

function entrypoint0() {
    let devices = new VirtualDevices()
    let uuid = getMicrophoneUuid(devices)
    console.log(uuid)
}

function entrypoint0_no_ctor() {
    let micro = new VirtualMicro()
    micro.uuid = "virtual_micro_v3"

    let devices = new VirtualDevices()
    devices.microphone = micro

    let uuid = getMicrophoneUuid(devices)
    console.log(uuid)
}
