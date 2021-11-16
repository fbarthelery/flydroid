/*
 * Flydroid is a self hosted platform for Android emulation.
 *
 * Copyright (C) 2020-2021 by Frederic-Charles Barthelery.
 *
 * This file is part of Flydroid.
 *
 * Flydroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Flydroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Flydroid.  If not, see <http://www.gnu.org/licenses/>.
 */
plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow")
}

application {
    mainClass.set("com.geekorum.flydroid.apiserver.AppKt")
}

tasks {
    withType<Jar> {
        manifest {
            attributes(
                    mapOf("Main-Class" to application.mainClass.get())
            )
        }
    }
    withType<Test> {
        useJUnitPlatform()
    }
}

kotlin {
    sourceSets.all {
        languageSettings.optIn("kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(enforcedPlatform("io.ktor:ktor-bom:1.6.5"))
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-websockets")
    implementation("io.ktor:ktor-network")
    implementation("io.ktor:ktor-serialization")
    implementation("io.ktor:ktor-client-okhttp")
    implementation("io.ktor:ktor-client-serialization-jvm")
    implementation("io.ktor:ktor-auth")
    implementation("io.ktor:ktor-locations")
    testImplementation("io.ktor:ktor-server-test-host") {
        exclude("org.jetbrains.kotlin", "kotlin-test-junit")
    }

    implementation("ch.qos.logback:logback-classic:1.2.3")

    implementation("com.squareup.okhttp3:okhttp:4.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.7.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.1")
    testImplementation("com.google.truth:truth:1.1.2")
    testImplementation("io.mockk:mockk:1.11.0")
}
