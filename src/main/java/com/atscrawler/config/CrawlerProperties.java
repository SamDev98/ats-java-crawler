package com.atscrawler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Setter
@Getter
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {
  private List<String> greenhouseCompanies, leverCompanies, workableCompanies, recruiteeCompanies, ashbyCompanies;
  private List<String> breezyCompanies, zohoCompanies, jobviteCompanies, taleoCompanies, ripplingCompanies;
  private List<String> bamboohrCompanies, recruitcrmCompanies, teamtailorCompanies, jobsoidCompanies, talentlyftCompanies;
  private List<String> hundredHiresCompanies, recruitezeCompanies, kulaCompanies, ismartCompanies, vincereCompanies;

}
