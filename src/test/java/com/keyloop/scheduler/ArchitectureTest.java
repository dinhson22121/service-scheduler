package com.keyloop.scheduler;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.keyloop.scheduler", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.keyloop.scheduler";

    @ArchTest
    static final ArchRule domainIsPlainJava = classes().that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..");

    @ArchTest
    static final ArchRule applicationDependsOnPortsNotTechnology = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter..", "jakarta.persistence..", "org.springframework.data..", "org.springframework.dao..",
                    "org.springframework.web..", "org.springframework.http..", "org.postgresql..", "io.micrometer..");

    @ArchTest
    static final ArchRule inboundAdaptersDoNotReachOutboundAdapters = noClasses().that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..");

    @ArchTest
    static final ArchRule catalogIsReachedThroughItsDomainAndPorts = onlyThroughDomainAndPorts("catalog");

    @ArchTest
    static final ArchRule bookingIsReachedThroughItsDomainAndPorts = onlyThroughDomainAndPorts("booking");

    @ArchTest
    static final ArchRule availabilityIsReachedThroughItsDomainAndPorts = onlyThroughDomainAndPorts("availability");

    private static ArchRule onlyThroughDomainAndPorts(String context) {
        String contextPackage = ROOT + "." + context;
        return noClasses().that().resideOutsideOfPackage(contextPackage + "..")
                .and().resideOutsideOfPackage(ROOT)
                .and().resideOutsideOfPackage(ROOT + ".availability.adapter.out.persistence")
                .should().dependOnClassesThat().resideInAnyPackage(
                        contextPackage + ".adapter..", contextPackage + ".application")
                .because("other contexts may use only its domain and ports; the availability read model and the "
                        + "composition root in the root package are the documented exceptions");
    }
}
