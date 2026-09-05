plugins {
    java
    id("pl.allegro.tech.build.axion-release")
}

// 모듈 b 는 `b-<version>` 태그로 독립 버전을 가진다.
scmVersion {
    unshallowRepoOnCI.set(false)

    tag {
        prefix.set("b")
        versionSeparator.set("-")
    }

    monorepo {
        // b 는 a 에 의존하므로 a/ 의 변경도 b 의 버전 증가 대상으로 본다.
        include(listOf("a"))
    }

    versionIncrementer("incrementPatch")
}

version = scmVersion.version

dependencies {
    implementation(project(":a"))
}
