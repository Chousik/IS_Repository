import org.gradle.api.plugins.quality.Checkstyle

plugins {
    java
    checkstyle
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
configurations.all {
    exclude(group = "org.glassfish.jaxb", module = "jaxb-core")
}
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa"){
        exclude(group = "org.hibernate", module = "hibernate-core")
    }
    // https://mvnrepository.com/artifact/org.eclipse.persistence/eclipselink
    implementation("org.eclipse.persistence:eclipselink:4.0.6")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
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
    testImplementation("org.mockito:mockito-core:5.19.0")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")
    // https://mvnrepository.com/artifact/com.nimbusds/nimbus-jose-jwt
    implementation("com.nimbusds:nimbus-jose-jwt:10.3.1")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.22")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
}

checkstyle {
    toolVersion = "10.21.1"
}

tasks.withType<Checkstyle> {
    configFile = file("config/checkstyle/checkstyle.xml")
    reports {
        xml.required.set(true)
        html.required.set(false)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
