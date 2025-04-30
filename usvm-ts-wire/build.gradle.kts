plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-ts-wire:client"))
    implementation(project(":usvm-ts-wire:server"))
    implementation(project(":usvm-ts"))

    implementation(Libs.jacodb_ets)
    implementation(Libs.logback)
}
