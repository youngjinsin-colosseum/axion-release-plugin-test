plugins {
    java
    id("pl.allegro.tech.build.axion-release")
}

// 모듈 c 는 `c-<version>` 태그로 독립 버전을 가진다.
scmVersion {
    tag {
        prefix.set("c")
        versionSeparator.set("-")
    }

    monorepo {
        // c 는 b(그리고 전이적으로 a)에 의존한다.
        include(listOf("a", "b"))
    }

    versionIncrementer("incrementPatch")
}

version = scmVersion.version

dependencies {
    implementation(project(":b"))
}
