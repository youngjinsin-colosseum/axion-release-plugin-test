plugins {
    id("pl.allegro.tech.build.axion-release") version "1.21.3"
}

// 루트 프로젝트 버전: `v<version>` 태그를 기준으로 계산한다.
scmVersion {
    tag {
        prefix.set("v")
        versionSeparator.set("")
    }

    // a, b, c 디렉터리의 변경은 루트 버전 증가에 영향을 주지 않는다.
    monorepo {
        exclude(listOf("a", "b", "c"))
    }

    versionIncrementer("incrementPatch")
}

version = scmVersion.version

allprojects {
    group = "com.example"
}

subprojects {
    repositories {
        mavenCentral()
    }

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        dependencies {
            "testImplementation"(platform("org.junit:junit-bom:5.11.4"))
            "testImplementation"("org.junit.jupiter:junit-jupiter")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

// 모든 모듈의 현재 버전을 한 번에 보여주는 편의 태스크
tasks.register("versions") {
    group = "help"
    description = "루트 및 모든 모듈의 현재 버전을 출력한다."
    val versions = allprojects.associate { it.name to it.provider { it.version.toString() } }
    doLast {
        versions.forEach { (name, v) -> println("$name = ${v.get()}") }
    }
}
