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

    // b/ 디렉터리의 변경만 b 버전을 증가시킨다. (a 의 변경에는 반응하지 않는다)
    monorepo {
    }
}

version = scmVersion.version

dependencies {
    implementation(project(":a"))
}
