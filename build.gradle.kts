import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.registering

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

val openApiOutputDir = layout.buildDirectory.dir("generated/openapi")

val openApiTypeMappings = mapOf(
    "CoordinatesAddRequest" to "ru.chousik.is.dto.request.CoordinatesAddRequest",
    "CoordinatesUpdateRequest" to "ru.chousik.is.dto.request.CoordinatesUpdateRequest",
    "CoordinatesResponse" to "ru.chousik.is.dto.response.CoordinatesResponse",
    "LocationAddRequest" to "ru.chousik.is.dto.request.LocationAddRequest",
    "LocationUpdateRequest" to "ru.chousik.is.dto.request.LocationUpdateRequest",
    "LocationResponse" to "ru.chousik.is.dto.response.LocationResponse",
    "PersonAddRequest" to "ru.chousik.is.dto.request.PersonAddRequest",
    "PersonUpdateRequest" to "ru.chousik.is.dto.request.PersonUpdateRequest",
    "PersonResponse" to "ru.chousik.is.dto.response.PersonResponse",
    "StudyGroupAddRequest" to "ru.chousik.is.dto.request.StudyGroupAddRequest",
    "StudyGroupUpdateRequest" to "ru.chousik.is.dto.request.StudyGroupUpdateRequest",
    "StudyGroupResponse" to "ru.chousik.is.dto.response.StudyGroupResponse",
    "StudyGroupShouldBeExpelledGroupResponse" to "ru.chousik.is.dto.response.StudyGroupShouldBeExpelledGroupResponse",
    "StudyGroupExpelledTotalResponse" to "ru.chousik.is.dto.response.StudyGroupExpelledTotalResponse",
    "PagedCoordinatesResponse" to "org.springframework.data.web.PagedModel",
    "PagedLocationResponse" to "org.springframework.data.web.PagedModel",
    "PagedPersonResponse" to "org.springframework.data.web.PagedModel",
    "PagedStudyGroupResponse" to "org.springframework.data.web.PagedModel",
    "PageModel" to "org.springframework.data.web.PagedModel",
    "Color" to "ru.chousik.is.entity.Color",
    "Country" to "ru.chousik.is.entity.Country",
    "FormOfEducation" to "ru.chousik.is.entity.FormOfEducation",
    "Semester" to "ru.chousik.is.entity.Semester"
)

val openApiAdditionalProperties = mapOf(
    "useTags" to "true",
    "interfaceOnly" to "true",
    "skipDefaultInterface" to "true",
    "useSpringBoot3" to "true"
)

val generateOpenApi by tasks.registering(Exec::class) {
    group = "openapi"
    description = "Generates Spring interfaces from the OpenAPI contract"

    val outputDir = openApiOutputDir.get().asFile

    inputs.file(file("src/main/resources/openapi.yaml"))
    outputs.dir(outputDir)

    doFirst {
        project.delete(outputDir)
    }

    val typeMappingsArg = openApiTypeMappings.entries.joinToString(separator = ",") { (schema, fqcn) ->
        "$schema=$fqcn"
    }
    val importMappingsArg = typeMappingsArg
    val additionalPropsArg = openApiAdditionalProperties.entries.joinToString(separator = ",") { (key, value) ->
        "$key=$value"
    }

    commandLine(
        "openapi-generator",
        "generate",
        "-i", "${projectDir}/src/main/resources/openapi.yaml",
        "-g", "spring",
        "-o", outputDir.absolutePath,
        "--global-property=apis,apiTests=false,apiDocs=false",
        "--api-package=ru.chousik.is.api",
        "--type-mappings=$typeMappingsArg",
        "--import-mappings=$importMappingsArg",
        "--additional-properties=$additionalPropsArg"
    )

    doLast {
        val studyGroupsApi = outputDir.resolve("src/main/java/ru/chousik/is/api/StudyGroupsApi.java")
        if (studyGroupsApi.exists()) {
            val importLine = "import ru.chousik.is.entity.Semester;\n"
            val marker = "package ru.chousik.is.api;\n\n"
            val content = studyGroupsApi.readText()
            if (!content.contains(importLine)) {
                studyGroupsApi.writeText(content.replaceFirst(marker, marker + importLine))
            }
        }
    }
}

extensions.getByType<SourceSetContainer>().named("main") {
    java.srcDir(openApiOutputDir.get().dir("src/main/java").asFile)
}

val cleanOpenApi by tasks.registering(Delete::class) {
    delete(openApiOutputDir)
}

tasks.named("build") {
    dependsOn(generateOpenApi)
    finalizedBy(cleanOpenApi)
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(generateOpenApi)
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
