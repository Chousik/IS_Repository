import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.registering

plugins {
    id("java-library")
    checkstyle
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.4.0"
}

group = "ru.chousik.is"
version = "0.0.1-SNAPSHOT"
description = "Generated Java API surface for information-systems backend"

val springBootBomVersion = "3.5.5"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

configurations {
    create("openApi")
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
    "ImportJobResponse" to "ru.chousik.is.dto.response.ImportJobResponse",
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
    "interfaceOnly" to "true",
    "useTags" to "true",
    "skipDefaultInterface" to "true",
    "useSpringBoot3" to "true",
    "useJakartaEe" to "true",
    "useBeanValidation" to "true",
    "useResponseEntity" to "true",
    "generatedAnnotation" to "false",
    "useLocalDateTime" to "true"
)

val openApiGlobalProperties = mapOf(
    "apis" to "",
    "apiTests" to "false",
    "apiDocs" to "false",
    "supportingFiles" to ""
)

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("${projectDir}/src/main/resources/openapi.yaml")
    outputDir.set(openApiOutputDir.get().asFile.absolutePath)
    apiPackage.set("ru.chousik.is.api")
    modelPackage.set("ru.chousik.is.api.model")
    globalProperties.set(openApiGlobalProperties.toMutableMap())
    additionalProperties.set(openApiAdditionalProperties.toMutableMap())
    typeMappings.set(openApiTypeMappings.toMutableMap())
    importMappings.set(openApiTypeMappings.toMutableMap())
}

tasks.named("openApiGenerate") {
    doLast {
        val studyGroupsApi = openApiOutputDir.get().asFile.resolve("src/main/java/ru/chousik/is/api/StudyGroupsApi.java")
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
    delete(openApiOutputDir.get().asFile)
}

tasks.named("build") {
    dependsOn(tasks.named("openApiGenerate"))
    finalizedBy(cleanOpenApi)
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.named("clean") {
    dependsOn(cleanOpenApi)
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
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    // https://mvnrepository.com/artifact/org.mapstruct/mapstruct
    implementation("org.mapstruct:mapstruct:1.5.5.Final")
    testImplementation("org.mockito:mockito-core:5.19.0")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")
    // https://mvnrepository.com/artifact/com.nimbusds/nimbus-jose-jwt
    implementation("com.nimbusds:nimbus-jose-jwt:10.3.1")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.flywaydb:flyway-core:11.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.10.0")
    api("io.swagger.core.v3:swagger-annotations:2.2.22")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    api("org.springframework:spring-web")
    api("org.springframework:spring-context")
    api("jakarta.validation:jakarta.validation-api")
    api("jakarta.annotation:jakarta.annotation-api")
    api("jakarta.servlet:jakarta.servlet-api")
    api("com.fasterxml.jackson.core:jackson-annotations")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    api("org.openapitools:jackson-databind-nullable:0.2.6")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootBomVersion")
    }
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
