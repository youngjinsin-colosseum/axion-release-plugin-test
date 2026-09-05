import pl.allegro.tech.build.axion.release.domain.VersionConfig

plugins {
    id("pl.allegro.tech.build.axion-release") version "1.21.3"
}

// 브랜치 이름으로 버전 증가 방식을 결정한다. (첫 매치 우선)
// 머지 후에는 현재 브랜치가 main 이므로, CI 에서 PR 의 head 브랜치명을
// -Prelease.overriddenBranchName 으로 주입해야 이 규칙이 적용된다.
val branchIncrementers = mapOf(
    "feature/.*" to "incrementMinor",
    "feat/.*" to "incrementMinor",
    "breaking/.*" to "incrementMajor",
    "major/.*" to "incrementMajor",
    "hotfix/.*" to "incrementPatch",
    "bugfix/.*" to "incrementPatch",
    "fix/.*" to "incrementPatch"
)

// 루트 프로젝트 버전: `v<version>` 태그를 기준으로 계산한다.
scmVersion {
    // CI 에서는 checkout 시 fetch-depth: 0 으로 전체 히스토리를 받으므로
    // 플러그인의 unshallow fetch 는 불필요하다. (병렬 실행 시 .git/shallow lock 충돌 유발)
    unshallowRepoOnCI.set(false)

    tag {
        prefix.set("v")
        versionSeparator.set("")
    }

    // a, b, c 디렉터리의 변경은 루트 버전 증가에 영향을 주지 않는다.
    monorepo {
        exclude(listOf("a", "b", "c"))
    }

    // 위 패턴에 걸리지 않는 브랜치(main 직접 커밋 등)의 기본값
    versionIncrementer("incrementPatch")
    branchVersionIncrementer.putAll(branchIncrementers)
}

version = scmVersion.version

allprojects {
    group = "com.example"
}

subprojects {
    repositories {
        mavenCentral()
    }

    // 브랜치별 증가 규칙은 4개 모듈에 동일하게 적용한다.
    // (각 모듈의 scmVersion 블록보다 먼저 실행되므로 tag/monorepo 설정과 충돌하지 않는다)
    plugins.withId("pl.allegro.tech.build.axion-release") {
        extensions.configure<VersionConfig> {
            branchVersionIncrementer.putAll(branchIncrementers)
        }
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
