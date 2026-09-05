plugins {
    java
    id("pl.allegro.tech.build.axion-release")
}

// 모듈 a 는 `a-<version>` 태그로 독립 버전을 가진다.
scmVersion {
    unshallowRepoOnCI.set(false)

    tag {
        prefix.set("a")
        versionSeparator.set("-")
    }

    // a/ 디렉터리의 변경만 a 버전을 증가시킨다.
    monorepo {
    }
}

version = scmVersion.version
