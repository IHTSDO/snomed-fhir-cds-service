package org.snomed.cdsservice.rest;

import org.snomed.cdsservice.rest.pojo.VersionInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PropertySource("classpath:version.properties")
public class VersionController {

	@Value("${build.version}")
	private String version;

	@Value("${build.timestamp}")
	private String buildTimestamp;

	@GetMapping("/version")
	@ResponseBody
	public VersionInfo getVersion() {
		return new VersionInfo(version, buildTimestamp);
	}

}
