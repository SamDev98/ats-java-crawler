package com.atscrawler.service.fetch;

import com.atscrawler.model.Job;
import java.util.List;

public interface JobFetcher {
  List<Job> fetch();
  String getSourceName();
}
