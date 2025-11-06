package com.atscrawler.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Setter
@Getter
@NoArgsConstructor
@Entity
@Table(name = "jobs", indexes = {
  @Index(name = "idx_jobs_url", columnList = "url", unique = true),
  @Index(name = "idx_jobs_active", columnList = "is_active")
})
public class Job {
  @Id @GeneratedValue private UUID id;
  @Column(nullable=false) private String source;
  @Column(nullable=false) private String company;
  @Column(nullable=false) private String title;
  @Column(length=2048, nullable=false, unique=true) private String url;
  private LocalDate firstSeen;
  private LocalDate lastSeen;
  @Column(name="is_active") private boolean active = true;
  @Column(nullable=false) private String status = "Awaiting";
  @Column(length=2048) private String notes;

    public Job(String source, String company, String title, String url) {
        this.source = source;
        this.company = company;
        this.title = title;
        this.url = url;
    }
}
