import pl.allegro.tech.build.axion.release.domain.VersionConfig
import pl.allegro.tech.build.axion.release.domain.properties.VersionProperties

plugins {
    id("pl.allegro.tech.build.axion-release") version "1.21.3"
}

// 버전 증가 폭은 커밋 메시지(Conventional Commits)에서 읽는다.
//
// 예전에는 PR 의 head 브랜치명으로 결정했는데, 브랜치명은 머지 후 사라지는
// 일회성 정보라 릴리스 잡에 이벤트로만 전달됐다. 그래서 두 가지가 깨졌다.
//
//   1. 잡이 늦게 시작하면 그 사이 머지된 다른 PR 의 커밋까지 자기 브랜치명의
//      증가 폭으로 태그해버린다. (`ref: main` 이 "지금 최신"을 다시 조회하므로)
//   2. concurrency 큐에는 하나만 대기할 수 있어서, PR 이 연달아 머지되면
//      중간 잡이 취소된다. 그 PR 의 증가 규칙은 그대로 증발한다.
//
// 커밋 메시지는 main 에 영구히 남으므로, 직전 태그 이후 커밋을 전부 훑어
// 가장 큰 증가 폭을 고르면 잡이 언제 몇 번 돌든 같은 답이 나온다.
//
//   feat!: / fix!: / BREAKING CHANGE  -> major
//   feat:                             -> minor
//   그 외                             -> patch

val breakingChangePattern = Regex("""^\w+(\([^)]*\))?!:|^BREAKING[ -]CHANGE""", RegexOption.MULTILINE)
val featurePattern = Regex("""^feat(\([^)]*\))?:""", RegexOption.MULTILINE)

fun git(vararg args: String): String {
    val process = ProcessBuilder(listOf("git", *args))
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    return if (process.waitFor() == 0) output else ""
}

// 해당 접두사를 가진 태그 중 가장 높은 버전. 아직 없으면 null.
fun latestTag(pattern: String): String? =
    git("tag", "--list", pattern, "--sort=-v:refname")
        .lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotEmpty)

// 직전 태그 이후, 주어진 경로를 건드린 커밋들의 메시지.
// 태그가 없으면 히스토리 전체를 본다.
fun commitMessagesSince(tag: String?, pathspec: List<String>): String {
    val range = tag?.let { listOf("$it..HEAD") } ?: emptyList()
    return git(*(listOf("log", "--format=%B") + range + listOf("--") + pathspec).toTypedArray())
}

// pathspec 은 lazy 다. monorepo/tag 설정이 모두 끝난 뒤(버전 계산 시점)에 읽어야
// 각 모듈의 실제 설정값을 볼 수 있다.
fun conventionalIncrementer(config: VersionConfig, pathspec: () -> List<String>) =
    VersionProperties.Incrementer { context ->
        val tagPattern = config.tag.prefix.get() + config.tag.versionSeparator.get() + "*"
        val messages = commitMessagesSince(latestTag(tagPattern), pathspec())
        when {
            breakingChangePattern.containsMatchIn(messages) -> context.currentVersion.incrementMajorVersion()
            featurePattern.containsMatchIn(messages) -> context.currentVersion.incrementMinorVersion()
            else -> context.currentVersion.incrementPatchVersion()
        }
    }

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

    // 등급 판정 범위도 monorepo 설정과 같게 맞춘다. (a, b, c 커밋은 무시)
    versionIncrementer(conventionalIncrementer(this) {
        listOf(".") + monorepoConfig.excludeDirs.get().map { ":(exclude)$it" }
    })
}

version = scmVersion.version

allprojects {
    group = "com.example"
}

subprojects {
    val moduleDir = name

    repositories {
        mavenCentral()
    }

    // 각 모듈은 자기 디렉터리(+ monorepo include 로 지정한 의존 디렉터리)의
    // 커밋 메시지로 증가 폭을 정한다. 모듈마다 기준 태그와 대상 경로가 다르므로
    // CLI 프로퍼티로는 한 번에 못 넘기고, 여기서 모듈별로 심어준다.
    plugins.withId("pl.allegro.tech.build.axion-release") {
        extensions.configure<VersionConfig> {
            versionIncrementer(conventionalIncrementer(this) {
                listOf(moduleDir) + monorepoConfig.dependenciesDirs.get()
            })
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
