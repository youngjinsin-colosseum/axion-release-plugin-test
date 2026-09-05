plugins {
    java
    application
    id("pl.allegro.tech.build.axion-release")
}

// 모듈 c 는 `c-<version>` 태그로 독립 버전을 가진다.
scmVersion {
    unshallowRepoOnCI.set(false)

    tag {
        prefix.set("c")
        versionSeparator.set("-")
    }

    // c/ 디렉터리의 변경만 c 버전을 증가시킨다. (a, b 의 변경에는 반응하지 않는다)
    monorepo {
    }
}

version = scmVersion.version

application {
    mainClass.set("com.example.c.App")
}

dependencies {
    implementation(project(":b"))
}
