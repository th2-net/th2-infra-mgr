package com.exactpro.th2.inframgr;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.exactpro.th2.inframgr.docker.util.VersionNumberUtils.chooseLatest;
import static com.exactpro.th2.inframgr.docker.util.VersionNumberUtils.filterTags;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DynamicVersionsTest {
    private List<String> tags = Arrays.asList(
            "2.1.0",
            "2.1.1",
            "2.1.2",
            "2.2.0",
            "2.2.1",
            "2.2.2",
            "3.0.0",
            "3.0.1-th21450-598864005",
            "3.0.1-th21450-599407903",
            "3.1.0-th21450-610367611",
            "2.3.0",
            "3.1.0",
            "3.1.1",
            "2.4.0",
            "3.2.0-th2-1769-729682817",
            "3.2.0",
            "2.4.1-TH2-1766-v2-732761325",
            "3.2.1",
            "3.3.0-th2-121-v3-740351306",
            "3.2.2-TH2-1766-v3-744530724",
            "3.2.2-TH2-1766-v3-746964701",
            "3.9.0-th2-2325-1213490116",
            "3.9.0",
            "3.10.0-th2-2095-1229438839",
            "3.10.0-th2-2095-1230647623",
            "3.10.0-th2-2095-1231377071",
            "3.10.0",
            "3.10.1-bump-sf-core-1265013193",
            "3.10.1",
            "3.10.1-th2-2273-1270201983",
            "3.30.1",
            "3.100.1"
    );

    @Test
    void TestChooseLatestVersion1() {
        Collections.sort(tags);
        String versionRange = "3.";
        List<String> filteredTags = filterTags(tags, versionRange);
        String latest = chooseLatest(filteredTags);
        assertEquals("3.100.1", versionRange + latest);
    }

    @Test
    void TestChooseLatestVersion2() {
        Collections.sort(tags);
        String versionRange = "";
        List<String> filteredTags = filterTags(tags, versionRange);
        String latest = chooseLatest(filteredTags);
        assertEquals("3.100.1", versionRange + latest);
    }
}
