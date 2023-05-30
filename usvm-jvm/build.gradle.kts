plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation("org.jacodb:jacodb-core:${Versions.jcdb}")
    implementation("org.jacodb:jacodb-analysis:${Versions.jcdb}")
}