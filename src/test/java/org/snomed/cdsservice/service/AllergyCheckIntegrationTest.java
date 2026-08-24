package org.snomed.cdsservice.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.snomed.cdsservice.service.medication.MedicationOrderSelectCDSService;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for allergy checking using REAL SNOMED terminology server.
 * 
 * These tests are DISABLED by default because they:
 * - Make real HTTP requests to the terminology server
 * - Are slower (~2-5 seconds vs milliseconds)
 * - Require internet connection
 * - Require the terminology server to be available
 * 
 * To run these tests:
 * 1. Remove @Disabled annotation from the test(s) you want to run
 * 2. Run: mvn test -Dtest=AllergyCheckIntegrationTest -Dnet.bytebuddy.experimental=true
 * 
 * Or run specific test:
 * mvn test -Dtest=AllergyCheckIntegrationTest#testBetaBlockerSubsumption -Dnet.bytebuddy.experimental=true
 */
@SpringBootTest
@TestPropertySource(properties = {
    "fhir.terminology-server.url=https://implementation-demo.snomedtools.org/fhir"
})
public class AllergyCheckIntegrationTest {

    @Autowired
    private MedicationOrderSelectCDSService service;

    @Autowired
    private FHIRTerminologyServerClient tsClient; // ← Real client, NOT @MockBean

    @Test
    public void testBetaBlockerSubsumptionWithRealServer() throws IOException {
        // This test makes REAL requests to the SNOMED terminology server
        // It verifies that the subsumption logic works with real SNOMED data
        
        System.out.println("\n=== INTEGRATION TEST: Beta-blocker Subsumption ===");
        System.out.println("Testing with REAL terminology server...\n");
        
        // Test: Allergy to beta-blocker class (372661004) should detect Atenolol
        
        // First, verify the terminology server can expand the beta-blocker class
        String betaBlockerECL = "http://snomed.info/sct?fhir_vs=ecl/<<%20372661004";
        try {
            System.out.println("Expanding ECL: << 372661004");
            var descendants = tsClient.expandValueSet(betaBlockerECL);
            System.out.println("Found " + descendants.size() + " beta-blocker substances:");
            descendants.stream()
                    .limit(10)
                    .forEach(coding -> System.out.println("  - " + coding.getCode() + " | " + coding.getDisplay()));
            
            // Check if Atenolol is in the expansion
            boolean hasAtenolol = descendants.stream()
                    .anyMatch(coding -> "387506000".equals(coding.getCode()));
            assertTrue(hasAtenolol, "Beta-blocker class should include Atenolol in real SNOMED data");
            
        } catch (Exception e) {
            fail("Failed to expand beta-blocker ECL from real server: " + e.getMessage());
        }
        
        // Now test the full allergy checking flow
        String allergyBundle = createAllergyBundle("372661004", "Substance with beta-1 adrenergic receptor antagonist mechanism of action");
        String medicationBundle = StreamUtils.copyToString(
                getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithAtenolol.json"), 
                StandardCharsets.UTF_8);
        
        CDSRequest cdsRequest = new CDSRequest();
        cdsRequest.setPrefetchStrings(Map.of(
                "patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
                "conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
                "draftMedicationRequests", medicationBundle,
                "allergies", allergyBundle
        ));

        System.out.println("\nCalling CDS Service with beta-blocker allergy and Atenolol medication...");
        List<CDSCard> cards = service.call(cdsRequest);
        System.out.println("Received " + cards.size() + " card(s)\n");
        
        // Find allergy alert
        CDSCard allergyCard = cards.stream()
                .filter(card -> "Allergy Contraindication".equals(card.getAlertType()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(allergyCard, "Should generate allergy alert via real SNOMED subsumption");
        assertEquals(CDSIndicator.critical, allergyCard.getIndicator());
        
        System.out.println("✅ Allergy Alert Generated:");
        System.out.println("   Summary: " + allergyCard.getSummary());
        System.out.println("   Detail: " + allergyCard.getDetail());
        System.out.println("\n=== TEST PASSED ===\n");
    }

    @Test
    public void testAllergyPropensityResolutionWithRealServer() throws IOException {
        // This test verifies that propensity codes are resolved correctly using real SNOMED data
        // Example: "Allergy to atenolol" (293965006) → resolves to "Atenolol" (387506000)
        // Uses lookup with normalForm (refactored approach)
        
        System.out.println("\n=== INTEGRATION TEST: Propensity Resolution ===");
        System.out.println("Testing with REAL terminology server...\n");
        
        // Test resolving causative agent from propensity using lookup with normalForm
        String allergyCode = "293965006"; // Allergy to atenolol
        try {
            System.out.println("Resolving causative agent for: " + allergyCode + " |Allergy to atenolol|");
            System.out.println("Using lookup with normalForm to extract causative agent attribute (246075003)");
            
            // Lookup the allergy concept to get its normalForm
            var conceptParameters = tsClient.lookup("http://snomed.info/sct", allergyCode);
            var normalForm = conceptParameters.getNormalForm();
            
            // Extract causative agent from normalForm
            final String CAUSATIVE_AGENT_ATTRIBUTE = "246075003";
            String causativeAgentCode = null;
            
            // Check direct attributes first
            causativeAgentCode = normalForm.getAttributes().get(CAUSATIVE_AGENT_ATTRIBUTE);
            
            // Check attribute groups if not found in direct attributes
            if (causativeAgentCode == null) {
                for (var group : normalForm.getAttributeGroups()) {
                    causativeAgentCode = group.get(CAUSATIVE_AGENT_ATTRIBUTE);
                    if (causativeAgentCode != null) {
                        break;
                    }
                }
            }
            
            assertNotNull(causativeAgentCode, "Should find causative agent in normalForm");
            System.out.println("Found causative agent code: " + causativeAgentCode);
            
            // Lookup the causative agent to get its display name
            var agentParams = tsClient.lookup("http://snomed.info/sct", causativeAgentCode);
            String display = agentParams.getParameters("display").stream()
                    .findFirst()
                    .map(p -> p.getValue().toString())
                    .orElse(null);
            
            System.out.println("Causative agent: " + causativeAgentCode + " | " + display);
            
            // Should resolve to Atenolol (387506000)
            assertEquals("387506000", causativeAgentCode, "Propensity should resolve to Atenolol (387506000) using real SNOMED data");
            assertNotNull(display, "Display name should be available");
            assertTrue(display.toLowerCase().contains("atenolol"), "Display should mention Atenolol");
            
        } catch (Exception e) {
            fail("Failed to resolve propensity from real server: " + e.getMessage());
        }
        
        System.out.println("\n=== TEST PASSED ===\n");
    }
    
    @Test
    @Disabled("Integration test - server demo may not have same data as production SNOMED server")
    public void testPenicillinSubsumptionWithRealServer() throws IOException {
        // Test: Allergy to Penicillin class should detect Amoxicillin
        
        System.out.println("\n=== INTEGRATION TEST: Penicillin Subsumption ===");
        System.out.println("Testing with REAL terminology server...\n");
        
        String penicillinECL = "http://snomed.info/sct?fhir_vs=ecl/<<%20372806008";
        try {
            System.out.println("Expanding ECL: << 372806008 |Penicillin|");
            
            var descendants = tsClient.expandValueSet(penicillinECL);
            System.out.println("Found " + descendants.size() + " penicillin-related substances:");
            descendants.stream()
                    .limit(20)
                    .forEach(coding -> System.out.println("  - " + coding.getCode() + " | " + coding.getDisplay()));
            
            // Verify we got some results
            assertTrue(descendants.size() > 0, "Penicillin class expansion should return at least one substance");
            
            // Check if Amoxicillin is in the expansion (code 372687004)
            boolean hasAmoxicillin = descendants.stream()
                    .anyMatch(coding -> "372687004".equals(coding.getCode()));
            
            // Check if Ampicillin is in the expansion (code 373270004)
            boolean hasAmpicillin = descendants.stream()
                    .anyMatch(coding -> "373270004".equals(coding.getCode()));
            
            // Check if Penicillin G is in the expansion (code 76435001) as a fallback
            boolean hasPenicillinG = descendants.stream()
                    .anyMatch(coding -> "76435001".equals(coding.getCode()));
            
            // At least one of these common penicillins should be present
            assertTrue(hasAmoxicillin || hasAmpicillin || hasPenicillinG, 
                    "Penicillin class should include at least one common penicillin (Amoxicillin 372687004, Ampicillin 373270004, or Penicillin G 76435001). " +
                    "Found " + descendants.size() + " substances total.");
            
        } catch (Exception e) {
            fail("Failed to expand penicillin ECL from real server: " + e.getMessage());
        }
        
        System.out.println("\n=== TEST PASSED ===\n");
    }

    // Helper methods
    private String createAllergyBundle(String allergyCode, String allergyDisplay) {
        return String.format("""
            {
              "resourceType": "Bundle",
              "type": "searchset",
              "entry": [{
                "resource": {
                  "resourceType": "AllergyIntolerance",
                  "id": "test-allergy",
                  "clinicalStatus": {
                    "coding": [{"system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical", "code": "active"}]
                  },
                  "code": {
                    "coding": [{"system": "http://snomed.info/sct", "code": "%s", "display": "%s"}]
                  },
                  "patient": {"reference": "Patient/test"}
                }
              }]
            }
            """, allergyCode, allergyDisplay);
    }
}

