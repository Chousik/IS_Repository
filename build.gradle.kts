plugins {
    java
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ru.chousik.is"
version = "0.0.1-SNAPSHOT"
description = "IS-project"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa"){
        exclude(group = "org.hibernate", module = "hibernate-core")
    }
    // https://mvnrepository.com/artifact/org.eclipse.persistence/eclipselink
    implementation("org.eclipse.persistence:eclipselink:4.0.6")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    // https://mvnrepository.com/artifact/org.mapstruct/mapstruct
    implementation("org.mapstruct:mapstruct:1.5.5.Final")
    // https://mvnrepository.com/artifact/com.nimbusds/nimbus-jose-jwt
    implementation("com.nimbusds:nimbus-jose-jwt:10.3.1")
    // https://mvnrepository.com/artifact/org.springframework.security/spring-security-core
    implementation("org.springframework.security:spring-security-core:6.5.5")
    // https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-devtools
    implementation("org.springframework.boot:spring-boot-devtools:3.5.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
