import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.registering
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

plugins {
    id("java-library")
    checkstyle
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.4.0"
    id("com.diffplug.spotless") version "6.25.0"
}

group = "ru.chousik.is"
version = "0.0.1-SNAPSHOT"
description = "Generated Java API surface for information-systems backend"

val checkstyleConfigFile = layout.projectDirectory.file("config/checkstyle/checkstyle.xml").asFile
val checkstyleDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(checkstyleConfigFile)
val checkstyleXPath = XPathFactory.newInstance().newXPath()

fun readCheckstyleProperty(moduleName: String, propertyName: String): String? {
    val expression = checkstyleXPath.compile("//module[@name='$moduleName']/property[@name='$propertyName']/@value")
    val value = expression.evaluate(checkstyleDocument, XPathConstants.STRING) as String
    return value.takeIf { it.isNotEmpty() }
}

val checkstyleIndentSize = readCheckstyleProperty("Indentation", "basicOffset")?.toIntOrNull() ?: 4
val checkstyleLineLength = readCheckstyleProperty("LineLength", "max")?.toIntOrNull() ?: 120

val generatedFormatterFile = layout.buildDirectory.file("generated/spotless/eclipse-checkstyle.xml").get().asFile
if (!generatedFormatterFile.parentFile.exists()) {
    generatedFormatterFile.parentFile.mkdirs()
}
val formatterXml = """<?xml version="1.0" encoding="UTF-8"?>
<profiles version="12">
    <profile kind="CodeFormatterProfile" name="checkstyle-derived" version="12">
        <setting id="org.eclipse.jdt.core.formatter.tabulation.char" value="space"/>
        <setting id="org.eclipse.jdt.core.formatter.tabulation.size" value="$checkstyleIndentSize"/>
        <setting id="org.eclipse.jdt.core.formatter.indentation.size" value="$checkstyleIndentSize"/>
        <setting id="org.eclipse.jdt.core.formatter.continuation_indentation" value="1"/>
        <setting id="org.eclipse.jdt.core.formatter.continuation_indentation_for_array_initializer" value="1"/>
        <setting id="org.eclipse.jdt.core.formatter.lineSplit" value="$checkstyleLineLength"/>
        <setting id="org.eclipse.jdt.core.formatter.comment.line_length" value="$checkstyleLineLength"/>
        <setting id="org.eclipse.jdt.core.formatter.join_wrapped_lines" value="false"/>
        <setting id="org.eclipse.jdt.core.formatter.tabulation.char.use_tabs_only_for_leading_indentations" value="false"/>
    </profile>
</profiles>
"""
if (!generatedFormatterFile.exists() || generatedFormatterFile.readText() != formatterXml) {
    generatedFormatterFile.writeText(formatterXml)
}


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
    "models" to "",
    "apiTests" to "false",
    "apiDocs" to "false",
    "modelTests" to "false",
    "modelDocs" to "false",
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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")
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
    testImplementation("com.h2database:h2")
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
    implementation("org.apache.commons:commons-dbcp2:2.12.0")
    implementation("org.ehcache:ehcache:3.10.8")
    implementation("org.hibernate.orm:hibernate-jcache")
    implementation("io.minio:minio:8.5.10")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    runtimeOnly("org.glassfish.jaxb:jaxb-runtime:2.3.6")
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
    exclude("**/generated/**")
    exclude("${layout.buildDirectory.get().asFile}/generated/**")
}

tasks.named<Checkstyle>("checkstyleMain") {
    source = fileTree("src/main/java")
}

tasks.named<Checkstyle>("checkstyleTest") {
    source = fileTree("src/test/java")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
